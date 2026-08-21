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

import java.util.ArrayList;
import java.util.List;

/**
 * SL651数据帧结构
 * 基于SL651-2016标准表20（HEX/BCD编码上行帧结构）
 */
public class SL651Frame {
    // 帧头部分
    private byte[] startMarker = new byte[]{(byte) 0x7E, (byte) 0x7E}; // 帧起始符
    private int centerStationAddress;     // 中心站地址（1字节）
    private byte[] telemetryStationAddress = new byte[5]; // 遥测站地址（5字节）
    private int password;                 // 密码（2字节）
    private int functionCode;             // 功能码（1字节）
    private int lengthField;              // 报文上下行标识及长度（2字节）
    private byte startOfText;             // 报文起始符STX（0x02）

    // 报文正文
    private int serialNumber;             // 流水号（2字节）
    private byte[] reportTime = new byte[6]; // 发报时间（6字节BCD）
    private byte[] addressIdentifier = new byte[2]; // 地址标识符（2字节）
    private byte[] stationAddressInBody = new byte[5]; // 正文中的遥测站地址
    private int stationTypeCode;          // 遥测站分类码（1字节）
    private byte[] timeIdentifier = new byte[2]; // 观测时间标识符（2字节）
    private byte[] observationTime = new byte[5]; // 观测时间（5字节BCD）

    // 数据要素列表
    private List<DataElement> dataElements = new ArrayList<>();

    // 帧尾部分
    private byte endOfText;               // 报文结束符ETX（0x03）
    private int crc;                      // CRC校验码（2字节）

    // 解析状态
    private boolean valid = false;
    private String msg;

    // 新增字段
    private int originalLengthField;      // 原始长度字段（用于调试）
    private int adjustedBodyLength;       // 调整后的正文长度
    private String reportTimeStr;         // 发报时间字符串
    private String observationTimeStr;    // 观测时间字符串
    private int dataElementCount;         // 数据要素数量
    private boolean crcValid;             // CRC校验是否通过
    private boolean addressIdentifierValid; // 地址标识符是否有效（正文地址标识符应为F1H）
    private int parsedBytes;              // 已解析的字节数
    private int totalBytes;               // 总字节数

    private byte[] rawFrame;              // 原始整帧字节（解码器填充，用于留存排查）

    // Getter和Setter方法
    public byte[] getStartMarker() { return startMarker; }
    public void setStartMarker(byte[] startMarker) { this.startMarker = startMarker; }

    public int getCenterStationAddress() { return centerStationAddress; }
    public void setCenterStationAddress(int centerStationAddress) {
        this.centerStationAddress = centerStationAddress;
    }

    public byte[] getTelemetryStationAddress() { return telemetryStationAddress; }
    public void setTelemetryStationAddress(byte[] telemetryStationAddress) {
        System.arraycopy(telemetryStationAddress, 0, this.telemetryStationAddress, 0, 5);
    }

    public int getPassword() { return password; }
    public void setPassword(int password) { this.password = password; }

    public int getFunctionCode() { return functionCode; }
    public void setFunctionCode(int functionCode) { this.functionCode = functionCode; }

    public int getLengthField() { return lengthField; }
    public void setLengthField(int lengthField) { this.lengthField = lengthField; }

    public byte getStartOfText() { return startOfText; }
    public void setStartOfText(byte startOfText) { this.startOfText = startOfText; }

    public int getSerialNumber() { return serialNumber; }
    public void setSerialNumber(int serialNumber) { this.serialNumber = serialNumber; }

    public byte[] getReportTime() { return reportTime; }
    public void setReportTime(byte[] reportTime) {
        System.arraycopy(reportTime, 0, this.reportTime, 0, 6);
    }

    public byte[] getAddressIdentifier() { return addressIdentifier; }
    public void setAddressIdentifier(byte[] addressIdentifier) {
        System.arraycopy(addressIdentifier, 0, this.addressIdentifier, 0, 2);
    }

    public byte[] getStationAddressInBody() { return stationAddressInBody; }
    public void setStationAddressInBody(byte[] stationAddressInBody) {
        System.arraycopy(stationAddressInBody, 0, this.stationAddressInBody, 0, 5);
    }

    public int getStationTypeCode() { return stationTypeCode; }
    public void setStationTypeCode(int stationTypeCode) {
        this.stationTypeCode = stationTypeCode;
    }

    public byte[] getTimeIdentifier() { return timeIdentifier; }
    public void setTimeIdentifier(byte[] timeIdentifier) {
        System.arraycopy(timeIdentifier, 0, this.timeIdentifier, 0, 2);
    }

    public byte[] getObservationTime() { return observationTime; }
    public void setObservationTime(byte[] observationTime) {
        System.arraycopy(observationTime, 0, this.observationTime, 0, 5);
    }

    public List<DataElement> getDataElements() { return dataElements; }
    public void setDataElements(List<DataElement> dataElements) {
        this.dataElements = dataElements;
    }
    public void addDataElement(DataElement element) {
        this.dataElements.add(element);
    }

    public byte getEndOfText() { return endOfText; }
    public void setEndOfText(byte endOfText) { this.endOfText = endOfText; }

    public int getCrc() { return crc; }
    public void setCrc(int crc) { this.crc = crc; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getMsg() { return msg; }
    public void setMsg(String errorMessage) { this.msg = errorMessage; }

    // 新增字段的getter和setter
    public int getOriginalLengthField() {
        return originalLengthField;
    }

    public void setOriginalLengthField(int originalLengthField) {
        this.originalLengthField = originalLengthField;
    }

    public int getAdjustedBodyLength() {
        return adjustedBodyLength;
    }

    public void setAdjustedBodyLength(int adjustedBodyLength) {
        this.adjustedBodyLength = adjustedBodyLength;
    }

    public String getReportTimeStr() {
        return reportTimeStr;
    }

    public void setReportTimeStr(String reportTimeStr) {
        this.reportTimeStr = reportTimeStr;
    }

    public String getObservationTimeStr() {
        return observationTimeStr;
    }

    public void setObservationTimeStr(String observationTimeStr) {
        this.observationTimeStr = observationTimeStr;
    }

    public int getDataElementCount() {
        return dataElementCount;
    }

    public void setDataElementCount(int dataElementCount) {
        this.dataElementCount = dataElementCount;
    }

    public boolean isCrcValid() { return crcValid; }

    public byte[] getRawFrame() { return rawFrame; }
    public void setRawFrame(byte[] rawFrame) { this.rawFrame = rawFrame; }

    public void setCrcValid(boolean crcValid) {
        this.crcValid = crcValid;
    }

    public boolean isAddressIdentifierValid() {
        return addressIdentifierValid;
    }

    public void setAddressIdentifierValid(boolean addressIdentifierValid) {
        this.addressIdentifierValid = addressIdentifierValid;
    }

    public int getParsedBytes() {
        return parsedBytes;
    }

    public void setParsedBytes(int parsedBytes) {
        this.parsedBytes = parsedBytes;
    }

    public int getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(int totalBytes) {
        this.totalBytes = totalBytes;
    }

    /**
     * 获取遥测站地址字符串（BCD码转十进制）
     */
    public String getTelemetryStationAddressString() {
        return bcdToString(telemetryStationAddress);
    }

    /**
     * 获取发报时间字符串
     */
    public String getReportTimeString() {
        return bcdTimeToString(reportTime, true);
    }

    /**
     * 获取观测时间字符串
     */
    public String getObservationTimeString() {
        return bcdTimeToString(observationTime, false);
    }

    /**
     * 获取上行/下行标识
     * @return true-上行, false-下行
     */
    public boolean isUplink() {
        return (lengthField >> 12) == 0;
    }

    /**
     * 获取正文长度
     */
    public int getBodyLength() {
        return lengthField & 0x0FFF;
    }

    /**
     * BCD字节数组转字符串
     */
    private String bcdToString(byte[] bcd) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bcd) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * BCD时间转字符串
     * @param timeBytes 时间字节数组
     * @param includeSeconds 是否包含秒
     */
    private String bcdTimeToString(byte[] timeBytes, boolean includeSeconds) {
        if (timeBytes == null || timeBytes.length < 5) {
            return "未知时间";
        }

        StringBuilder sb = new StringBuilder();
        // 年（2位）
        sb.append(String.format("%02X", timeBytes[0]));
        // 月
        sb.append(String.format("%02X", timeBytes[1]));
        // 日
        sb.append(String.format("%02X", timeBytes[2]));
        // 时
        sb.append(String.format("%02X", timeBytes[3]));
        // 分
        sb.append(String.format("%02X", timeBytes[4]));

        if (includeSeconds && timeBytes.length >= 6) {
            // 秒
            sb.append(String.format("%02X", timeBytes[5]));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SL651帧信息:\n");
        sb.append("  帧起始符: ");
        for (byte b : startMarker) {
            sb.append(String.format("%02X", b));
        }
        sb.append("\n");
        sb.append("  中心站地址: ").append(centerStationAddress).append("\n");
        sb.append("  遥测站地址: ").append(getTelemetryStationAddressString()).append("\n");
        sb.append("  密码: ").append(String.format("%04X", password)).append("\n");
        sb.append("  功能码: ").append(String.format("%02X", functionCode)).append(" (")
                .append(FunctionCode.getName(functionCode)).append(")\n");
        sb.append("  长度字段: ").append(String.format("%04X", lengthField)).append(" (")
                .append(isUplink() ? "上行" : "下行").append(", 正文长度: ").append(getBodyLength()).append(")\n");
        sb.append("  报文起始符: ").append(String.format("%02X", startOfText)).append("\n");
        sb.append("  流水号: ").append(String.format("%04X", serialNumber)).append("\n");
        sb.append("  发报时间: ").append(getReportTimeString()).append("\n");
        sb.append("  地址标识符: ").append(String.format("%02X%02X", addressIdentifier[0], addressIdentifier[1])).append("\n");
        sb.append("  正文遥测站地址: ").append(bcdToString(stationAddressInBody)).append("\n");
        sb.append("  遥测站分类码: ").append(String.format("%02X", stationTypeCode)).append(" (")
                .append(StationType.getName(stationTypeCode)).append(")\n");
        sb.append("  观测时间标识符: ").append(String.format("%02X%02X", timeIdentifier[0], timeIdentifier[1])).append("\n");
        sb.append("  观测时间: ").append(getObservationTimeString()).append("\n");
        sb.append("  数据要素数量: ").append(dataElements.size()).append("\n");

        for (int i = 0; i < dataElements.size(); i++) {
            sb.append("    要素").append(i + 1).append(": ").append(dataElements.get(i)).append("\n");
        }

        sb.append("  报文结束符: ").append(String.format("%02X", endOfText)).append("\n");
        sb.append("  CRC校验码: ").append(String.format("%04X", crc)).append("\n");
        sb.append("  解析状态: ").append(valid ? "有效" : "无效").append("\n");
        if (msg != null) {
            if (valid) {
                sb.append("  信息: ").append(msg).append("\n");
            }else {
                sb.append("  错误信息: ").append(msg).append("\n");
            }
        }

        // 添加新增字段的输出
        sb.append("  原始长度字段: ").append(String.format("%04X", originalLengthField)).append("\n");
        sb.append("  调整后正文长度: ").append(adjustedBodyLength).append("\n");
        sb.append("  CRC验证状态: ").append(crcValid ? "通过" : "失败").append("\n");
        sb.append("  解析字节数: ").append(parsedBytes).append("/").append(totalBytes).append("\n");

        return sb.toString();
    }
}