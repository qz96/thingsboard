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
 * 功能码定义
 * 基于SL651-2016附录B
 */
public class FunctionCode {
    private static final Map<Integer, String> FUNCTION_CODE_MAP = new HashMap<>();

    static {
        // 初始化功能码
        FUNCTION_CODE_MAP.put(0x00, "保留");
        FUNCTION_CODE_MAP.put(0x01, "保留");
        FUNCTION_CODE_MAP.put(0x2F, "链路维持报");
        FUNCTION_CODE_MAP.put(0x30, "测试报");
        FUNCTION_CODE_MAP.put(0x31, "均匀时段水文信息报");
        FUNCTION_CODE_MAP.put(0x32, "遥测站定时报");
        FUNCTION_CODE_MAP.put(0x33, "遥测站加报");
        FUNCTION_CODE_MAP.put(0x34, "遥测站小时报");
        FUNCTION_CODE_MAP.put(0x35, "遥测站人工置数报");
        FUNCTION_CODE_MAP.put(0x36, "遥测站图片报");
        FUNCTION_CODE_MAP.put(0x37, "中心站查询遥测站实时数据");
        FUNCTION_CODE_MAP.put(0x38, "中心站查询遥测站时段数据");
        FUNCTION_CODE_MAP.put(0x39, "中心站查询遥测站人工置数");
        FUNCTION_CODE_MAP.put(0x3A, "中心站查询遥测站指定要素数据");
        FUNCTION_CODE_MAP.put(0x3B, "保留");
        FUNCTION_CODE_MAP.put(0x40, "中心站修改遥测站基本配置表");
        FUNCTION_CODE_MAP.put(0x41, "中心站读取遥测站基本配置表");
        FUNCTION_CODE_MAP.put(0x42, "中心站修改遥测站运行参数配置表");
        FUNCTION_CODE_MAP.put(0x43, "中心站读取遥测站运行参数配置表");
        FUNCTION_CODE_MAP.put(0x44, "中心站查询水泵电机数据");
        FUNCTION_CODE_MAP.put(0x45, "中心站查询软件版本");
        FUNCTION_CODE_MAP.put(0x46, "中心站查询状态和报警信息");
        FUNCTION_CODE_MAP.put(0x47, "初始化固态存储数据");
        FUNCTION_CODE_MAP.put(0x48, "恢复终端出厂设置");
        FUNCTION_CODE_MAP.put(0x49, "修改密码");
        FUNCTION_CODE_MAP.put(0x4A, "设置时钟");
        FUNCTION_CODE_MAP.put(0x4B, "设置IC卡状态");
        FUNCTION_CODE_MAP.put(0x4C, "控制水泵");
        FUNCTION_CODE_MAP.put(0x4D, "控制阀门");
        FUNCTION_CODE_MAP.put(0x4E, "控制闸门");
        FUNCTION_CODE_MAP.put(0x4F, "水量定值控制");
        FUNCTION_CODE_MAP.put(0x50, "查询事件记录");
    }

    public static String getName(int functionCode) {
        return FUNCTION_CODE_MAP.getOrDefault(functionCode,
                String.format("未知功能码(0x%02X)", functionCode));
    }
}