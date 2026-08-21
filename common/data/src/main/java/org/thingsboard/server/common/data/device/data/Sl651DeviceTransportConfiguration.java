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
package org.thingsboard.server.common.data.device.data;

import lombok.Data;
import org.thingsboard.server.common.data.DeviceTransportType;

/**
 * SL651 设备级传输配置。
 *
 * 通过 DeviceData.transportConfiguration 存取，Jackson 按 "type":"SL651" 反序列化。
 * 存放单个设备的 SL651 专属参数（例如该设备的遥测站地址、接入密钥）。
 */
@Data
public class Sl651DeviceTransportConfiguration implements DeviceTransportConfiguration {

    private static final long serialVersionUID = 1031518372359512174L;

    /**
     * 遥测站地址（8 位十六进制站码）。可为空，缺省时以设备 Access Token 作为站码。
     */
    private String stationAddress;

    /**
     * 接入密钥（可选，增强认证用）。
     */
    private Integer key;

    @Override
    public DeviceTransportType getType() {
        return DeviceTransportType.SL651;
    }

}