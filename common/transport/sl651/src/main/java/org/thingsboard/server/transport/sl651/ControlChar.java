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

/**
 * 控制字符定义
 * 基于SL651-2016表10
 */
public class ControlChar {
    // ASCII字符编码帧起始
    public static final byte SOH = 0x01;

    // HEX/BCD编码帧起始
    public static final byte[] START_MARKER = {(byte) 0x7E, (byte) 0x7E};

    // 传输正文起始
    public static final byte STX = 0x02;

    // 多包传输正文起始
    public static final byte SYN = 0x16;

    // 报文结束，后续无报文
    public static final byte ETX = 0x03;

    // 报文结束，后续有报文
    public static final byte ETB = 0x17;

    // 询问
    public static final byte ENQ = 0x05;

    // 传输结束，退出
    public static final byte EOT = 0x04;

    // 肯定确认，继续发送
    public static final byte ACK = 0x06;

    // 否定应答，反馈重发
    public static final byte NAK = 0x15;

    // 传输结束，终端保持在线
    public static final byte ESC = 0x1B;

    /**
     * 获取控制字符名称
     */
    public static String getName(byte controlChar) {
        switch (controlChar) {
            case SOH: return "SOH(帧起始-ASCII)";
            case STX: return "STX(正文起始)";
            case SYN: return "SYN(多包正文起始)";
            case ETX: return "ETX(报文结束)";
            case ETB: return "ETB(报文结束，后续有报文)";
            case ENQ: return "ENQ(询问)";
            case EOT: return "EOT(传输结束)";
            case ACK: return "ACK(肯定确认)";
            case NAK: return "NAK(否定应答)";
            case ESC: return "ESC(传输结束，终端保持在线)";
            default:
                if (controlChar == START_MARKER[0]) {
                    return "7E(帧起始-HEX/BCD)";
                }
                return String.format("未知控制字符(0x%02X)", controlChar);
        }
    }
}