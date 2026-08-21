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
import io.netty.buffer.Unpooled;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * SL651 下行命令帧编码器（中心站 → 遥测站）。
 *
 * 组成（与 {@link SL651Parser} 解析的上行结构对称）：
 *   起始符 7E 7E | 中心站地址(1) | 遥测站地址(5) | 密码(2) | 功能码(1)
 *   | 长度字段(2，bit15=1 表示下行 + 低12位正文长度)
 *   | STX(0x02) | 正文(流水号2 + 参数) | ETX(0x03) | CRC16(2，小端序)
 *
 * 上下行标识：SL651 约定 0x8000 位为下行标志（上行该位为 0）。
 */
public class Sl651CommandEncoder {

    private final AtomicInteger serial = new AtomicInteger(0);

    /** 下行帧回程所需的帧头信息（取自已解析的上行帧） */
    public static class Header {
        public final int centerStation;
        public final byte[] stationAddress; // 5 字节 BCD 遥测站地址
        public final int password;

        public Header(int centerStation, byte[] stationAddress, int password) {
            this.centerStation = centerStation;
            this.stationAddress = stationAddress;
            this.password = password;
        }
    }

    public int nextSerial() {
        return serial.incrementAndGet() & 0xFFFF;
    }

    /**
     * 构建下行命令帧
     *
     * @param header      寻址信息
     * @param functionCode 功能码（见 {@link FunctionCode}）
     * @param params       命令参数（可空），置于正文流水号之后
     * @return 完整帧字节
     */
    public byte[] build(Header header, int functionCode, byte[] params) {
        int bodyLength = 2 + (params == null ? 0 : params.length); // 流水号(2) + 参数
        int serialNumber = nextSerial();

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(ControlChar.START_MARKER[0]);
        buf.writeByte(ControlChar.START_MARKER[1]);
        buf.writeByte(header.centerStation & 0xFF);
        buf.writeBytes(header.stationAddress, 0, 5);
        buf.writeShort(header.password & 0xFFFF);
        buf.writeByte(functionCode & 0xFF);
        buf.writeShort(0x8000 | (bodyLength & 0x0FFF)); // 下行标识 0x8000 + 正文长度
        buf.writeByte(ControlChar.STX);
        buf.writeShort(serialNumber);
        if (params != null && params.length > 0) {
            buf.writeBytes(params);
        }
        buf.writeByte(ControlChar.ETX);

        byte[] head = new byte[buf.readableBytes()];
        buf.readBytes(head);

        int crc = CRC16.calculate(head, 0, head.length);
        ByteBuf out = Unpooled.buffer(head.length + 2);
        out.writeBytes(head);
        out.writeByte(crc & 0xFF);
        out.writeByte((crc >> 8) & 0xFF);

        byte[] result = new byte[out.readableBytes()];
        out.readBytes(result);
        out.release();
        buf.release();
        return result;
    }
}