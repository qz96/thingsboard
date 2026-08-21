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
 * 数据要素类
 * 基于SL651-2016附录C（遥测信息编码要素及标识符汇总表）
 *
 * 数据要素结构：
 * - 引导符（1字节）：要素类型
 * - 数据属性（1字节）：高5位=数据长度，低3位=小数位数
 * - 数据（N字节）：BCD编码
 */
public class DataElement {
    private int guideByte;       // 引导符（第1字节）
    private int attributeByte;   // 数据属性（第2字节）
    private byte[] data;         // 数据值（BCD编码）
    private int dataLength;      // 数据长度（字节数，从属性字节解析）
    private int decimalPlaces;   // 小数位数（从属性字节解析）
    private int extensionByte;   // 扩展标识符XX（引导符为FFH时有效，取值0x00~0xFE，-1表示无扩展标识符）

    public DataElement(int guideByte, int attributeByte, byte[] data) {
        this(guideByte, -1, attributeByte, data);
    }

    public DataElement(int guideByte, int extensionByte, int attributeByte, byte[] data) {
        this.guideByte = guideByte;
        this.extensionByte = extensionByte;
        this.attributeByte = attributeByte;
        this.data = data;
        this.dataLength = ElementIdentifier.getDataLength(attributeByte);
        this.decimalPlaces = ElementIdentifier.getDecimalPlaces(attributeByte);
    }

    /**
     * 返回引导符（用于映射查找）
     */
    public int getIdentifier() { return guideByte; }

    public int getGuideByte() { return guideByte; }
    public int getAttributeByte() { return attributeByte; }

    /**
     * 返回完整的2字节标识符
     */
    public int getFullIdentifier() {
        return (guideByte << 8) | attributeByte;
    }

    public byte[] getData() { return data; }
    public int getDataLength() { return dataLength; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public int getExtensionByte() { return extensionByte; }
    public boolean hasExtension() { return extensionByte >= 0; }

    public String getElementName() {
        // 引导符为FFH时，按 FFXXH 用户自定义扩展区显示，XX 为扩展标识符
        if (guideByte == 0xFF && extensionByte >= 0) {
            return String.format("用户自定义扩展区(XX=0x%02X)", extensionByte);
        }
        return ElementIdentifier.getName(guideByte);
    }

    /**
     * 获取数据值（BCD解码后根据小数位数转换）
     * BCD编码：每字节表示两位十进制数
     *
     * 负数识别遵循 SL651-2016 §6.6.3.3 a)：
     *  - BCD数据最高位字节为 0xFF 表示负数
     *  - 数据位数是奇数且为负数时，数据高位前插 FF0（符号FF + 半字节0补齐，即2字节），数据自第3字节起
     *  - 数据位数是偶数且为负数时，数据高位前插 1 字节 FF，数据自第2字节起
     *  - 数据长度（属性字节高5位）已包含符号位所占字节
     * 数据中为非法BCD（含高半字节或低半字节大于9）时，视为无效数据返回NaN
     */
    public double getValue() {
        if (data == null || data.length == 0) {
            return 0.0;
        }

        boolean negative = false;
        int start = 0;

        // 首字节为0xFF表示负数
        if ((data[0] & 0xFF) == 0xFF) {
            negative = true;
            start = 1;
            // 奇数位负数：符号为 FF0（FF + 00补齐），数据自第3字节起
            if (data.length > 1 && (data[1] & 0xFF) == 0x00) {
                start = 2;
            }
        }

        // BCD解码：每字节高低4位各为一位十进制数
        long longValue = 0;
        for (int i = start; i < data.length; i++) {
            int high = (data[i] >> 4) & 0x0F;
            int low = data[i] & 0x0F;
            // 非法BCD数据（如0xFFFF等无效值）
            if (high > 9 || low > 9) {
                return Double.NaN;
            }
            longValue = longValue * 100 + high * 10 + low;
        }

        if (negative) {
            longValue = -longValue;
        }
        return longValue / Math.pow(10, decimalPlaces);
    }

    public String getUnit() {
        return ElementIdentifier.getUnit(guideByte);
    }

    @Override
    public String toString() {
        return String.format("引导符: 0x%02X 属性: 0x%02X (%s), 值: %s %s, 原始数据: %s",
                guideByte, attributeByte, getElementName(), formatValue(), getUnit(), bytesToHex(data));
    }

    private String formatValue() {
        double value = getValue();
        if (Double.isNaN(value)) {
            return "无效数据";
        }
        if (decimalPlaces <= 0) {
            return String.valueOf((long) value);
        }
        return String.format("%." + decimalPlaces + "f", value);
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}