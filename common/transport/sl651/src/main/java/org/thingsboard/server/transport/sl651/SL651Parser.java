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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SL651协议解析器
 * 支持HEX/BCD编码格式报文解析
 * 支持String、byte[]和ByteBuf多种输入格式
 *
 * 新增严格模式校验开关配置：
 * - 严格模式 (strictMode=true): 完全按照SL651-2014标准校验
 * - 宽松模式 (strictMode=false): 忽略部分非关键字段校验，优先解析数据
 *
 * 由 sl-gateway 项目迁移而来，去掉 Spring 注解改为普通工具类，配置项提供 setter 以被上层注入。
 */
public class SL651Parser {

    private static final Logger log = LoggerFactory.getLogger(SL651Parser.class);

    // 严格模式开关，默认false（宽松模式），与源项目 sl-gateway 保持一致；
    // 严格模式下会因部分终端/测试帧长度字段宽松导致解析被拒绝
    private boolean strictMode = false;

    // 启用/禁用CRC校验
    private boolean crcCheck = true;

    // 启用/禁用长度校验
    private boolean lengthCheck = false;

    // 是否允许非标准起始符/结束符
    private boolean allowNonStandardChars = true;

    // CRC字节序: little=小端序(低字节在前, SL651标准默认), big=大端序(高字节在前)
    private String crcByteOrder = "little";

    public SL651Parser() {
    }

    public SL651Parser(boolean strictMode, boolean crcCheck, boolean lengthCheck, boolean allowNonStandardChars, String crcByteOrder) {
        this.strictMode = strictMode;
        this.crcCheck = crcCheck;
        this.lengthCheck = lengthCheck;
        this.allowNonStandardChars = allowNonStandardChars;
        this.crcByteOrder = crcByteOrder;
    }

    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }
    public void setCrcCheck(boolean crcCheck) { this.crcCheck = crcCheck; }
    public void setLengthCheck(boolean lengthCheck) { this.lengthCheck = lengthCheck; }
    public void setAllowNonStandardChars(boolean allowNonStandardChars) { this.allowNonStandardChars = allowNonStandardChars; }
    public void setCrcByteOrder(String crcByteOrder) { this.crcByteOrder = crcByteOrder; }

    /**
     * 解析SL651报文（十六进制字符串格式）
     *
     * @param hexString 十六进制字符串格式的报文
     * @return 解析后的SL651Frame对象
     */
    public SL651Frame parse(String hexString) {
        // 去除空格
        hexString = hexString.replaceAll("\\s+", "");

        // 转换为字节数组
        byte[] data = hexStringToBytes(hexString);

        return parse(data);
    }

    /**
     * 解析SL651报文（字节数组格式）
     * 内部包装为ByteBuf后复用统一解析逻辑
     */
    public SL651Frame parse(byte[] data) {
        if (data == null || data.length < 20) {
            SL651Frame frame = new SL651Frame();
            frame.setValid(false);
            frame.setMsg("报文长度不足");
            return frame;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        return parse(buf);
    }

    /**
     * 解析SL651报文（ByteBuf格式）
     * 专为Netty ByteBuf设计，避免内存拷贝
     *
     * @param rawData ByteBuf格式的报文
     * @return 解析后的SL651Frame对象
     */
    public SL651Frame parse(ByteBuf rawData) {
        SL651Frame frame = new SL651Frame();
        if (rawData == null || rawData.readableBytes() < 20) {
            frame.setValid(false);
            frame.setMsg("ByteBuf数据为空或长度不足");
            return frame;
        }

        int originalReaderIndex = rawData.readerIndex();
        int totalLength = rawData.readableBytes();

        try {
            // 1. 验证帧起始符并自动检测格式
            // SL651-2014标准: 双7E起始符 (7E 7E)
            // 部分设备实现: 单7E起始符 (7E)
            byte start1 = rawData.readByte();
            if (start1 != ControlChar.START_MARKER[0]) {
                frame.setValid(false);
                frame.setMsg("无效的帧起始符");
                rawData.readerIndex(originalReaderIndex);
                return frame;
            }

            byte start2 = rawData.readByte();
            boolean singleStartMarker;
            if (start2 == ControlChar.START_MARKER[1]) {
                // 标准7E 7E双起始符格式
                singleStartMarker = false;
                frame.setStartMarker(new byte[]{start1, start2});
            } else {
                // 单7E起始符格式，start2是中心站地址
                singleStartMarker = true;
                frame.setStartMarker(new byte[]{start1});
                rawData.readerIndex(rawData.readerIndex() - 1); // 回退1字节
                log.debug("检测到单7E起始符格式");
            }

            // 2. 解析中心站地址（1字节）
            int centerStation = rawData.readUnsignedByte();
            frame.setCenterStationAddress(centerStation);

            // 3. 解析遥测站地址（5字节）
            byte[] stationAddr = new byte[5];
            rawData.readBytes(stationAddr);
            frame.setTelemetryStationAddress(stationAddr);

            // 4. 解析密码（2字节）
            int password = rawData.readUnsignedShort();
            frame.setPassword(password);

            // 5. 解析功能码（1字节）
            int functionCode = rawData.readUnsignedByte();
            frame.setFunctionCode(functionCode);
            log.debug("功能码: {}", functionCode);

            // 6. 解析报文上下行标识及长度（2字节）
            int lengthField = rawData.readUnsignedShort();
            frame.setLengthField(lengthField);

            // 记录原始长度字段
            frame.setOriginalLengthField(lengthField);
            log.debug("ByteBuf解析 - 长度字段: 0x{}", Integer.toHexString(lengthField).toUpperCase());

            // 7. 解析报文起始符（1字节）
            byte stx = rawData.readByte();
            frame.setStartOfText(stx);

            // 根据配置决定是否验证起始符
            if (strictMode && !allowNonStandardChars) {
                if (stx != ControlChar.STX && stx != ControlChar.SYN) {
                    frame.setValid(false);
                    frame.setMsg(String.format("非标准报文起始符: 0x%02X", stx));
                    rawData.readerIndex(originalReaderIndex);
                    return frame;
                }
            } else {
                if (stx != ControlChar.STX && stx != ControlChar.SYN) {
                    log.debug("非标准报文起始符: 0x{}", String.format("%02X", stx));
                }
            }

            // 8. 动态计算正文长度
            int declaredBodyLength = frame.getBodyLength();
            int remainingBytes = rawData.readableBytes() - 3; // 减去结束符(1)和CRC(2)

            int actualBodyLength = Math.min(declaredBodyLength, remainingBytes);
            if (actualBodyLength < 0) {
                actualBodyLength = remainingBytes;
            }

            // 长度校验
            if ((strictMode || lengthCheck) && declaredBodyLength > remainingBytes + 3) {
                frame.setValid(false);
                frame.setMsg(String.format(
                        "报文长度与声明不符，声明正文长度: %d字节，实际可用: %d字节",
                        declaredBodyLength, remainingBytes));
                rawData.readerIndex(originalReaderIndex);
                return frame;
            } else if (declaredBodyLength != remainingBytes) {
                log.debug("长度差异: 声明长度={}字节，实际长度={}字节",
                        declaredBodyLength, remainingBytes);
            }

            frame.setAdjustedBodyLength(actualBodyLength);

            // 保存正文开始位置
            int bodyStart = rawData.readerIndex();

            // 9.1 流水号（2字节）
            int serialNumber = rawData.readUnsignedShort();
            frame.setSerialNumber(serialNumber);

            // 9.2 发报时间（6字节BCD）
            byte[] reportTime = new byte[6];
            rawData.readBytes(reportTime);
            frame.setReportTime(reportTime);

            try {
                String timeStr = parseBCDTime(reportTime);
                frame.setReportTimeStr(timeStr);
            } catch (Exception e) {
                log.debug("发报时间解析异常: {}", e.getMessage());
            }

            // 9.3 地址标识符（2字节）
            // SL651-2016 表24/附录C注b：遥测站地址编码长度固定，HEX/BCD编码时数据定义字节固定用F1H表示
            // 即地址标识符高字节为引导符F1H（ST测站编码），低字节为数据定义（长度）
            byte[] addressIdentifier = new byte[2];
            rawData.readBytes(addressIdentifier);
            frame.setAddressIdentifier(addressIdentifier);
            boolean addressIdValid = (addressIdentifier[0] & 0xFF) == 0xF1;
            frame.setAddressIdentifierValid(addressIdValid);
            if (!addressIdValid) {
                log.debug("地址标识符异常: 应为F1H(测站编码引导符)，实际为0x{}",
                        String.format("%02X%02X", addressIdentifier[0], addressIdentifier[1]));
            }

            // 9.4 遥测站地址（5字节）
            byte[] stationAddressInBody = new byte[5];
            rawData.readBytes(stationAddressInBody);
            frame.setStationAddressInBody(stationAddressInBody);

            // 9.5 遥测站分类码（1字节）
            int stationTypeCode = rawData.readUnsignedByte();
            frame.setStationTypeCode(stationTypeCode);

            // 9.6 观测时间标识符（2字节）
            byte[] timeIdentifier = new byte[2];
            rawData.readBytes(timeIdentifier);
            frame.setTimeIdentifier(timeIdentifier);

            // 9.7 观测时间（5字节BCD）
            byte[] observationTime = new byte[5];
            rawData.readBytes(observationTime);
            frame.setObservationTime(observationTime);

            try {
                String obsTimeStr = parseBCDTime(observationTime);
                frame.setObservationTimeStr(obsTimeStr);
            } catch (Exception e) {
                log.debug("观测时间解析异常: {}", e.getMessage());
            }

            // 9.8 解析数据要素
            // SL651数据要素格式：2字节标识符（引导符+数据属性）+ N字节BCD数据
            //   - 引导符（1字节）：要素类型
            //   - 数据属性（1字节）：高5位=数据长度，低3位=小数位数
            List<DataElement> dataElements = new ArrayList<>();
            int bytesRead = rawData.readerIndex() - bodyStart;

            while (bytesRead < actualBodyLength - 1 && rawData.readableBytes() > 0) {
                if (rawData.readableBytes() < 2) {
                    break;
                }

                // 读取要素引导符（1字节）
                int guideByte = rawData.readUnsignedByte();
                bytesRead += 1;

                // 引导符为FFH时，为FFXXH用户自定义扩展区格式：其后增加1字节扩展标识符XX
                int extensionByte = -1;
                int attributeByte;
                if (guideByte == 0xFF) {
                    if (rawData.readableBytes() < 1) {
                        rawData.readerIndex(rawData.readerIndex() - 1);
                        break;
                    }
                    extensionByte = rawData.readUnsignedByte();
                    bytesRead += 1;
                    if (rawData.readableBytes() < 1) {
                        rawData.readerIndex(rawData.readerIndex() - 2);
                        break;
                    }
                    attributeByte = rawData.readUnsignedByte();
                    bytesRead += 1;
                } else {
                    if (rawData.readableBytes() < 1) {
                        rawData.readerIndex(rawData.readerIndex() - 1);
                        break;
                    }
                    attributeByte = rawData.readUnsignedByte();
                    bytesRead += 1;
                }

                // 从数据属性字节解析数据长度（高5位）和小数位数（低3位）
                int dataLength = ElementIdentifier.getDataLength(attributeByte);

                int identifierRead = (guideByte == 0xFF) ? 3 : 2;
                if (rawData.readableBytes() < dataLength) {
                    // 回退已读取的标识符字节（FFXXH为3字节，普通为2字节）
                    rawData.readerIndex(rawData.readerIndex() - identifierRead);
                    bytesRead -= identifierRead;
                    break;
                }

                byte[] elementData = new byte[dataLength];
                rawData.readBytes(elementData);
                bytesRead += dataLength;

                // 创建数据要素对象
                DataElement element = new DataElement(guideByte, extensionByte, attributeByte, elementData);
                dataElements.add(element);

                log.debug("解析数据要素: 引导符=0x{}{}, 属性=0x{}(长度={}, 小数={}), 数据={}",
                        String.format("%02X", guideByte),
                        guideByte == 0xFF ? "(扩展标识符=" + String.format("%02X", extensionByte) + ")" : "",
                        String.format("%02X", attributeByte),
                        dataLength, ElementIdentifier.getDecimalPlaces(attributeByte), bytesToHex(elementData));
            }
            frame.setDataElements(dataElements);
            frame.setDataElementCount(dataElements.size());

            // 10. 解析报文结束符（1字节）
            byte etx = rawData.readByte();
            frame.setEndOfText(etx);

            if (strictMode && !allowNonStandardChars) {
                if (etx != ControlChar.ETX && etx != ControlChar.ETB) {
                    frame.setValid(false);
                    frame.setMsg(String.format("非标准报文结束符: 0x%02X", etx));
                    rawData.readerIndex(originalReaderIndex);
                    return frame;
                }
            } else {
                if (etx != ControlChar.ETX && etx != ControlChar.ETB) {
                    log.debug("非标准报文结束符: 0x{}", String.format("%02X", etx));
                }
            }

            // 11. 解析CRC校验码（2字节）
            if (rawData.readableBytes() < 2) {
                frame.setValid(false);
                frame.setMsg("CRC校验码缺失");
                rawData.readerIndex(originalReaderIndex);
                return frame;
            }

            // CRC字节序根据配置决定: little=小端序(低字节在前), big=大端序(高字节在前)
            int crcFirst = rawData.readUnsignedByte();
            int crcSecond = rawData.readUnsignedByte();
            int receivedCrc;
            if ("big".equalsIgnoreCase(crcByteOrder)) {
                receivedCrc = (crcFirst << 8) | crcSecond;
            } else {
                receivedCrc = (crcSecond << 8) | crcFirst;
            }
            frame.setCrc(receivedCrc);

            // 12. CRC校验
            if (crcCheck) {
                // 重新读取整个帧（不包含CRC部分）进行CRC计算
                rawData.readerIndex(originalReaderIndex);
                int frameLengthWithoutCRC = totalLength - 2;

                byte[] frameBytes = new byte[frameLengthWithoutCRC];
                rawData.readBytes(frameBytes, 0, frameLengthWithoutCRC);

                int calculatedCrc = CRC16.calculate(frameBytes, 0, frameLengthWithoutCRC);

                // 重新定位读取位置到CRC之后
                rawData.readerIndex(originalReaderIndex + totalLength);

                if (receivedCrc != calculatedCrc) {
                    if (strictMode) {
                        frame.setValid(false);
                        frame.setMsg(String.format("CRC校验失败，接收: 0x%04X，计算: 0x%04X",
                                receivedCrc, calculatedCrc));
                        rawData.readerIndex(originalReaderIndex);
                        return frame;
                    } else {
                        log.debug("CRC校验失败，接收: 0x{}，计算: 0x{}",
                                Integer.toHexString(receivedCrc).toUpperCase(),
                                Integer.toHexString(calculatedCrc).toUpperCase());
                        frame.setCrcValid(false);
                    }
                } else {
                    frame.setCrcValid(true);
                    log.debug("CRC校验通过");
                }
            } else {
                frame.setCrcValid(true);
            }

            // 13. 设置解析结果
            frame.setValid(true);
            frame.setMsg("解析成功");
            frame.setParsedBytes(rawData.readerIndex() - originalReaderIndex);
            frame.setTotalBytes(totalLength);

            log.debug("ByteBuf解析成功: 遥测站={}, 数据要素={}个",
                    bytesToHex(stationAddr), dataElements.size());

            return frame;

        } catch (Exception e) {
            log.error("ByteBuf解析异常", e);
            frame.setValid(false);
            frame.setMsg("ByteBuf解析异常: " + e.getMessage());
            rawData.readerIndex(originalReaderIndex);
            return frame;
        }
    }

    /**
     * 解析BCD时间格式
     */
    private String parseBCDTime(byte[] bcdTime) {
        if (bcdTime == null) return "";

        StringBuilder sb = new StringBuilder();
        for (byte b : bcdTime) {
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;

            // 验证是否为有效BCD
            if (high > 9 || low > 9) {
                sb.append(String.format("%02X", b & 0xFF));
            } else {
                sb.append(high).append(low);
            }
        }
        return sb.toString();
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("无效的十六进制字符串");
        }

        int len = hexString.length() / 2;
        byte[] data = new byte[len];

        for (int i = 0; i < len; i++) {
            int index = i * 2;
            int value = Integer.parseInt(hexString.substring(index, index + 2), 16);
            data[i] = (byte) value;
        }
        return data;
    }

    /**
     * 获取当前解析器配置
     */
    public String getParserConfig() {
        return String.format("SL651解析器配置: strictMode=%s, crcCheck=%s, lengthCheck=%s, allowNonStandardChars=%s",
                strictMode, crcCheck, lengthCheck, allowNonStandardChars);
    }

    /**
     * 动态更新解析器配置
     */
    public void updateConfig(boolean strictMode, boolean crcCheck, boolean lengthCheck, boolean allowNonStandardChars) {
        this.strictMode = strictMode;
        this.crcCheck = crcCheck;
        this.lengthCheck = lengthCheck;
        this.allowNonStandardChars = allowNonStandardChars;
        log.info("解析器配置已更新: {}", getParserConfig());
    }
}