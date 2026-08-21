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
 * CRC16校验工具类
 * CRC算法：SL651使用CRC-16，生成多项式X^16+X^15+X^2+1(0x8005)，初始值0xFFFF
 * 使用位反转多项式0xA001实现，低位在前
 * 校验范围：从帧起始符7E7E到报文结束符03（不包括CRC本身）
 * <p>
 * 由 sl-gateway 项目迁移而来，保持算法一致。
 */
public class CRC16 {
    private static final int INITIAL_VALUE = 0xFFFF;
    private static final int POLYNOMIAL_REV = 0xA001; // 0x8005的位反转

    /**
     * 计算CRC16校验码
     * @param data 数据字节数组
     * @return CRC16校验码
     */
    public static int calculate(byte[] data) {
        if (data == null || data.length == 0) {
            return INITIAL_VALUE;
        }
        return calculate(data, 0, data.length);
    }

    /**
     * 计算CRC16校验码（支持偏移量和长度）
     * @param data 数据字节数组
     * @param offset 起始偏移量
     * @param length 数据长度
     * @return CRC16校验码
     */
    public static int calculate(byte[] data, int offset, int length) {
        int crc = INITIAL_VALUE;

        if (data == null || data.length == 0) {
            return crc;
        }

        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException("偏移量超出数组范围: " + offset);
        }

        if (length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("长度参数无效: offset=" + offset + ", length=" + length);
        }

        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ POLYNOMIAL_REV;
                } else {
                    crc >>= 1;
                }
            }
        }

        return crc;
    }

    /**
     * 验证CRC16校验码
     * @param data 数据字节数组
     * @param offset 起始偏移量
     * @param length 数据长度（不包括CRC的2字节）
     * @param receivedCrc 接收到的CRC校验码
     * @return 验证结果
     */
    public static boolean verify(byte[] data, int offset, int length, int receivedCrc) {
        int calculatedCrc = calculate(data, offset, length);
        return calculatedCrc == receivedCrc;
    }
}