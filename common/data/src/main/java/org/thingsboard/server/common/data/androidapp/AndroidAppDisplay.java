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
package org.thingsboard.server.common.data.androidapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.id.AndroidAppDisplayId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.Length;

/**
 * 安卓端管理-首页展示配置：选择平台类型，填写应用名称与下载路径，
 * 前端根据下载路径生成二维码预览，并可通过 showOnHome 灵活控制是否展示到首页。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class AndroidAppDisplay extends BaseData<AndroidAppDisplayId> implements HasTenantId, HasName {

    @Schema(description = "JSON object with Tenant Id")
    private TenantId tenantId;
    @Schema(description = "Platform type: ANDROID or IOS", requiredMode = Schema.RequiredMode.REQUIRED, example = "ANDROID")
    @NotNull
    private AndroidAppDisplayPlatform platformType;
    @Schema(description = "Application name shown on the home page", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Length(fieldName = "appName")
    private String appName;
    @Schema(description = "Application download URL used to generate the QR code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Length(fieldName = "downloadUrl", max = 2048)
    private String downloadUrl;
    @Schema(description = "Whether this entry is displayed on the home page", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean showOnHome;
    @Schema(description = "Sort order on the home page, smaller value comes first", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Integer sortOrder;
    @Schema(description = "Timestamp of the last update, in milliseconds", accessMode = Schema.AccessMode.READ_ONLY)
    private Long updatedTime;

    public AndroidAppDisplay() {
        super();
    }

    public AndroidAppDisplay(AndroidAppDisplayId id) {
        super(id);
    }

    public AndroidAppDisplay(AndroidAppDisplay androidAppDisplay) {
        super(androidAppDisplay);
        this.tenantId = androidAppDisplay.tenantId;
        this.platformType = androidAppDisplay.platformType;
        this.appName = androidAppDisplay.appName;
        this.downloadUrl = androidAppDisplay.downloadUrl;
        this.showOnHome = androidAppDisplay.showOnHome;
        this.sortOrder = androidAppDisplay.sortOrder;
        this.updatedTime = androidAppDisplay.updatedTime;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Application name", accessMode = Schema.AccessMode.READ_ONLY)
    public String getName() {
        return appName;
    }
}
