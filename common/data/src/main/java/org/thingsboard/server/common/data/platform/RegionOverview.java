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
package org.thingsboard.server.common.data.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Schema(description = "Region (tenant) overview with device statistics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionOverview {

    @Schema(description = "Tenant id which represents the region")
    private UUID tenantId;
    @Schema(description = "Region (tenant) title")
    private String title;
    @Schema(description = "Total number of devices in the region")
    private long deviceCount;
    @Schema(description = "Number of active devices")
    private long activeCount;
    @Schema(description = "Number of offline devices")
    private long offlineCount;
    @Schema(description = "Number of not cleared alarms")
    private long alarmCount;

}
