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
 * 遥测站分类码
 * 基于SL651-2016附录A 表A.1（规范性附录）
 *
 * 表A.1 遥测站分类码（HEX编码）：
 *  降水=50H 河道=48H 水库(湖泊)=4BH 闸坝=5AH 泵站=44H 潮汐=54H
 *  墒情=4DH 地下水=47H 水质=51H 取水口=49H 排水口=4FH 其他=自定义
 */
public class StationType {
    private static final Map<Integer, String> STATION_TYPE_MAP = new HashMap<>();

    static {
        // 初始化遥测站分类码（附录A 表A.1）
        STATION_TYPE_MAP.put(0x50, "降水站");
        STATION_TYPE_MAP.put(0x48, "河道站");
        STATION_TYPE_MAP.put(0x4B, "水库(湖泊)站");
        STATION_TYPE_MAP.put(0x5A, "闸坝站");
        STATION_TYPE_MAP.put(0x44, "泵站");
        STATION_TYPE_MAP.put(0x54, "潮汐站");
        STATION_TYPE_MAP.put(0x4D, "墒情站");
        STATION_TYPE_MAP.put(0x47, "地下水站");
        STATION_TYPE_MAP.put(0x51, "水质站");
        STATION_TYPE_MAP.put(0x49, "取水口站");
        STATION_TYPE_MAP.put(0x4F, "排水口站");
    }

    private StationType() {
    }

    public static String getName(int typeCode) {
        return STATION_TYPE_MAP.getOrDefault(typeCode,
                String.format("未知类型(0x%02X)", typeCode));
    }
}