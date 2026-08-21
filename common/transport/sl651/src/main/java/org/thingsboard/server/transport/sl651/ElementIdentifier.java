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

import java.util.HashMap;
import java.util.Map;

/**
 * 要素标识符定义
 * 基于SL651-2016附录C（遥测信息编码要素及标识符汇总表）
 *
 * SL651数据要素格式：
 * - 标识符：2字节
 *   - 第1字节（引导符）：要素类型
 *   - 第2字节（数据属性）：
 *     - D7~D3（高5位）：数据长度（字节数）
 *     - D2~D0（低3位）：小数位数
 * - 数据：BCD编码（每字节表示两位十进制数）
 */
public class ElementIdentifier {

    // 引导符 -> 要素信息映射
    private static final Map<Integer, ElementInfo> ELEMENT_MAP = new HashMap<>();

    static {
        initializeElementDefinitions();
    }

    private static void initializeElementDefinitions() {
        // 表C.1 序号1~14 引导符引导符（F0H~FDH）
        putElement(0xF0, "观测时间", "");
        putElement(0xF1, "测站编码", "");
        putElement(0xF2, "人工置数", "");
        putElement(0xF3, "图片信息", "");
        putElement(0xF4, "1小时时段雨量(DRP)", "0.1mm");
        putElement(0xF5, "1小时时段相对水位1(DRZ1)", "0.01m");
        putElement(0xF6, "1小时时段相对水位2(DRZ2)", "0.01m");
        putElement(0xF7, "1小时时段相对水位3(DRZ3)", "0.01m");
        putElement(0xF8, "1小时时段相对水位4(DRZ4)", "0.01m");
        putElement(0xF9, "1小时时段相对水位5(DRZ5)", "0.01m");
        putElement(0xFA, "1小时时段相对水位6(DRZ6)", "0.01m");
        putElement(0xFB, "1小时时段相对水位7(DRZ7)", "0.01m");
        putElement(0xFC, "1小时时段相对水位8(DRZ8)", "0.01m");
        putElement(0xFD, "流速批量传输(DATA)", "");

        // 表C.1 序号15~26 (01H~0CH)
        putElement(0x00, "未定义", "");
        putElement(0x01, "断面面积", "m²");
        putElement(0x02, "瞬时气温", "℃");
        putElement(0x03, "瞬时水温", "℃");
        putElement(0x04, "时间步长码", "");
        putElement(0x05, "时段长", "h.min");
        putElement(0x06, "日蒸发量", "mm");
        putElement(0x07, "当前蒸发", "mm");
        putElement(0x08, "气压", "hPa");
        putElement(0x09, "闸门开启高度", "m");
        putElement(0x0A, "输水设备编号", "");
        putElement(0x0B, "输水设备类别", "");
        putElement(0x0C, "闸门开启孔数", "孔");

        // 表C.1 序号27~29 (0DH~0FH)
        putElement(0x0D, "地温", "℃");
        putElement(0x0E, "地下水瞬时埋深", "m");
        putElement(0x0F, "波浪高度", "m");

        // 表C.1 序号30~38 (10H~18H) 不同深度土壤含水量/湿度
        int[] soilDepthCm = {10, 20, 30, 40, 50, 60, 80, 100};
        for (int i = 0; i < soilDepthCm.length; i++) {
            putElement(0x10 + i, soilDepthCm[i] + "cm土壤含水量", "%");
        }
        putElement(0x18, "湿度", "%");
        putElement(0x19, "开机台数", "台");
        putElement(0x1A, "1小时时段降水量", "mm");
        putElement(0x1B, "2小时时段降水量", "mm");
        putElement(0x1C, "3小时时段降水量", "mm");
        putElement(0x1D, "6小时时段降水量", "mm");
        putElement(0x1E, "12小时时段降水量", "mm");
        putElement(0x1F, "日降水量", "mm");

        // 表C.1 序号46~52 (20H~26H) 降水量
        putElement(0x20, "当前降水量", "mm");
        putElement(0x21, "1分钟时段降水量", "mm");
        putElement(0x22, "5分钟时段降水量", "mm");
        putElement(0x23, "10分钟时段降水量", "mm");
        putElement(0x24, "30分钟时段降水量", "mm");
        putElement(0x25, "暴雨量", "mm");
        putElement(0x26, "降水量累计值", "mm");

        // 表C.1 序号53~61 (27H~2FH) 瞬时/取排水口流量
        putElement(0x27, "瞬时流量", "m³/s");
        putElement(0x28, "取(排)水口流量1", "m³/s");
        putElement(0x29, "取(排)水口流量2", "m³/s");
        putElement(0x2A, "取(排)水口流量3", "m³/s");
        putElement(0x2B, "取(排)水口流量4", "m³/s");
        putElement(0x2C, "取(排)水口流量5", "m³/s");
        putElement(0x2D, "取(排)水口流量6", "m³/s");
        putElement(0x2E, "取(排)水口流量7", "m³/s");
        putElement(0x2F, "取(排)水口流量8", "m³/s");

        // 表C.1 序号62~73 (30H~39H) 出库/输水流量、输沙量、风向/风力/风速、断面流速、瞬时流速、电源电压、水位
        putElement(0x30, "总出库流量", "m³/s");
        putElement(0x31, "输水设备流量", "m³/s");
        putElement(0x32, "输沙量", "万吨");
        putElement(0x33, "风向", "");
        putElement(0x34, "风力", "级");
        putElement(0x35, "风速", "m/s");
        putElement(0x36, "断面平均流速", "m/s");
        putElement(0x37, "当前瞬时流速", "m/s");
        putElement(0x38, "电源电压", "V");
        putElement(0x39, "瞬时河道水位", "m");

        // 表C.1 序号72~81 (3AH~43H) 库(闸)上下水位、取排水口水位
        putElement(0x3A, "库(闸)下水位", "m");
        putElement(0x3B, "库(闸)上水位", "m");
        putElement(0x3C, "取(排)水口水位1", "m");
        putElement(0x3D, "取(排)水口水位2", "m");
        putElement(0x3E, "取(排)水口水位3", "m");
        putElement(0x3F, "取(排)水口水位4", "m");
        putElement(0x40, "取(排)水口水位5", "m");
        putElement(0x41, "取(排)水口水位6", "m");
        putElement(0x42, "取(排)水口水位7", "m");
        putElement(0x43, "取(排)水口水位8", "m");

        // 表C.1 序号82~93 (44H~4FH) 水质参数
        putElement(0x44, "含沙量", "kg/m³");
        putElement(0x45, "遥测站状态及报警信息", "");
        putElement(0x46, "pH值", "");
        putElement(0x47, "溶解氧", "mg/L");
        putElement(0x48, "电导率", "μS/cm");
        putElement(0x49, "浊度", "度");
        putElement(0x4A, "高锰酸盐指数", "mg/L");
        putElement(0x4B, "氧化还原电位", "mV");
        putElement(0x4C, "氨氮", "mg/L");
        putElement(0x4D, "总磷", "mg/L");
        putElement(0x4E, "总氮", "mg/L");
        putElement(0x4F, "总有机碳", "mg/L");

        // 表C.1 序号94~101 (50H~57H) 金属/叶绿素
        putElement(0x50, "铜", "mg/L");
        putElement(0x51, "锌", "mg/L");
        putElement(0x52, "硒", "mg/L");
        putElement(0x53, "砷", "mg/L");
        putElement(0x54, "总汞", "mg/L");
        putElement(0x55, "镉", "mg/L");
        putElement(0x56, "铅", "mg/L");
        putElement(0x57, "叶绿素a", "mg/L");

        // 表C.1 序号102~109 (58H~5FH) 水压
        for (int i = 0x58; i <= 0x5F; i++) {
            putElement(i, "水压" + (i - 0x58 + 1), "kPa");
        }

        // 表C.1 序号110~125 (60H~6FH) 水表剩余水量/每小时水量
        for (int i = 0x60; i <= 0x67; i++) {
            putElement(i, "水表" + (i - 0x60 + 1) + "剩余水量", "m³");
        }
        for (int i = 0x68; i <= 0x6F; i++) {
            putElement(i, "水表" + (i - 0x68 + 1) + "每小时水量", "m³/h");
        }

        // 表C.1 序号126~131 (70H~75H) 交流电压/电流
        putElement(0x70, "交流A相电压", "V");
        putElement(0x71, "交流B相电压", "V");
        putElement(0x72, "交流C相电压", "V");
        putElement(0x73, "交流A相电流", "A");
        putElement(0x74, "交流B相电流", "A");
        putElement(0x75, "交流C相电流", "A");

        // 表C.1 序号132 76H~EFH 待定保留
        // 表C.1 序号133 FFXXH 用户自定义扩展区（引导符为FFH，其后增加1字节扩展标识符）
        for (int i = 0x76; i <= 0xEF; i++) {
            putElement(i, "保留要素", "");
        }
        putElement(0xFF, "用户自定义扩展区", "");
    }

    private static void putElement(int guideByte, String name, String unit) {
        ELEMENT_MAP.put(guideByte, new ElementInfo(name, unit));
    }

    /**
     * 从数据属性字节获取数据长度（字节数）
     * 属性字节 D7~D3（高5位）= 数据长度
     */
    public static int getDataLength(int attributeByte) {
        return (attributeByte >> 3) & 0x1F;
    }

    /**
     * 从数据属性字节获取小数位数
     * 属性字节 D2~D0（低3位）= 小数位数
     */
    public static int getDecimalPlaces(int attributeByte) {
        return attributeByte & 0x07;
    }

    /**
     * 根据引导符获取要素名称
     */
    public static String getName(int guideByte) {
        ElementInfo info = ELEMENT_MAP.get(guideByte);
        return info != null ? info.name : String.format("未知要素(0x%02X)", guideByte);
    }

    /**
     * 根据引导符获取要素单位
     */
    public static String getUnit(int guideByte) {
        ElementInfo info = ELEMENT_MAP.get(guideByte);
        return info != null ? info.unit : "";
    }

    public static class ElementInfo {
        public final String name;
        public final String unit;

        ElementInfo(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }
}