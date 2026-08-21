/*
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.transport.sl651;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * SL651 帧解码器。
 *
 * SL651 帧不定长、自带长度字段，本解码器负责：
 *  1. 同步定位帧起始符 0x7E（兼容单/双起始符）；
 *  2. 依据长度字段待齐整帧后交给 {@link SL651Parser} 解析；
 *  3. 解析结果为有效帧则向后传递 {@link SL651Frame}，否则丢弃以便重新同步。
 */
public class Sl651FrameDecoder extends ByteToMessageDecoder {

    // 帧起始符
    static final byte FRAME_START = 0x7E;

    // 最小帧长度（起始符+中心站+遥测站+密码+功能码+长度=…，留足余量）
    private static final int MIN_FRAME_LENGTH = 14;

    // 长度字段起始位置（相对帧起始）：
    // 双起始符: 7E 7E 中心站(1) 遥测站(5) 密码(2) 功能码(1) => 长度字段在偏移 11
    // 单起始符: 7E 中心站(1) 遥测站(5) 密码(2) 功能码(1) => 长度字段在偏移 10
    private static final int LEN_FIELD_OFFSET_DOUBLE = 11;
    private static final int LEN_FIELD_OFFSET_SINGLE = 10;

    private final SL651Parser parser;
    private final int maxFrameLength;

    public Sl651FrameDecoder(SL651Parser parser) {
        this(parser, 2048);
    }

    public Sl651FrameDecoder(SL651Parser parser, int maxFrameLength) {
        this.parser = parser;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int startIndex = in.readerIndex();
        int readable = in.readableBytes();

        if (readable < 2) {
            return;
        }

        // 1. 同步到帧起始符（逐字节寻找 0x7E，丢弃前面乱码）
        int frameStart = -1;
        for (int i = 0; i < readable && i <= maxFrameLength; i++) {
            if (in.getUnsignedByte(startIndex + i) == FRAME_START) {
                frameStart = startIndex + i;
                break;
            }
        }
        if (frameStart < 0) {
            in.skipBytes(readable);
            return;
        }
        in.readerIndex(frameStart);
        int base = in.readerIndex();
        int available = in.readableBytes();

        // 2. SL651 帧以结束符(ETX 0x03 / ETB 0x17) + 2字节CRC 收尾。
        //    SL651 长度字段对部分终端/帧并不可靠（低位12位并不等于实际正文长度），
        //    故不依赖长度字段预切帧，而是逐个候选结束符 + 解析器CRC校验来定位整帧。
        for (int j = 1; j < available && j <= maxFrameLength; j++) {
            int b = in.getUnsignedByte(base + j);
            if (b != ControlChar.ETX && b != ControlChar.ETB) {
                continue;
            }
            int frameLength = j + 3; // 结束符(1) + CRC(2)
            if (frameLength > maxFrameLength) {
                continue;
            }
            if (available < frameLength) {
                return; // 结束符之后的CRC字节尚未到齐，等待更多数据
            }
            ByteBuf candidate = in.retainedSlice(base, frameLength);
            SL651Frame frame = parser.parse(candidate);
            candidate.release();
            if (frame != null && frame.isValid()) {
                byte[] raw = new byte[frameLength];
                in.getBytes(base, raw);
                frame.setRawFrame(raw);
                in.readerIndex(base + frameLength);
                out.add(frame);
                return;
            }
            // 该候选结束符不构成有效帧（多为数据字节恰为0x03/0x17），继续向后寻找真实结束符
        }

        // 3. 未找到有效整帧：超过上限则丢弃（防内存滞留），否则等待更多数据
        if (available > maxFrameLength) {
            in.skipBytes(available);
        }
    }
}