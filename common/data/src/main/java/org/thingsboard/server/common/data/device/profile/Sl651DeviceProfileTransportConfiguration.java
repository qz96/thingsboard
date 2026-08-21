/**
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
package org.thingsboard.server.common.data.device.profile;

import lombok.Data;
import org.thingsboard.server.common.data.DeviceTransportType;

import java.util.ArrayList;
import java.util.List;

/**
 * SL651 Device Profile 传输配置。
 *
 * 通过 DeviceProfileData.transportConfiguration 存取，Jackson 按 "type":"SL651" 反序列化。
 * 承载 SL651 协议在「设备配置集（Profile）」层的参数，用于传输层决定遥测键前缀、
 * 状态/事件报是否落入属性等。
 */
@Data
public class Sl651DeviceProfileTransportConfiguration implements DeviceProfileTransportConfiguration {

    private static final long serialVersionUID = 9117350494668919285L;

    /**
     * 遥测键前缀。为空或不等于默认占位时生效：键名 = 前缀 + "_" + 要素名。
     * 默认 "sl651" 表示不额外加前缀，键名直接使用要素名。
     */
    private String telemetryKeyPrefix = "sl651";

    /**
     * 状态/事件报是否上报为设备属性（client attribute）而非遥测。默认 true。
     */
    private boolean statusReportAsAttribute = true;

    /**
     * 允许接入的上报类型（定时报/加报/小时报等），用于接入侧白名单/统计，可为空。
     */
    private List<String> reportTypes = new ArrayList<>();

    @Override
    public DeviceTransportType getType() {
        return DeviceTransportType.SL651;
    }

}