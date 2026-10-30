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
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.Length;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class AndroidApp extends BaseData<AndroidAppId> implements HasTenantId, HasName {

    @Schema(description = "JSON object with Tenant Id")
    private TenantId tenantId;
    @Schema(description = "Application title", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Length(fieldName = "title")
    private String title;
    @Schema(description = "Android application package name. Unique within the tenant", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.lihero.iot")
    @NotBlank
    @Length(fieldName = "pkgName")
    private String pkgName;
    @Schema(description = "Application description")
    @Length(fieldName = "description", max = 1024)
    private String description;
    @Schema(description = "Application icon URL, hosted by the company shared file service")
    @Length(fieldName = "iconUrl", max = 2048)
    private String iconUrl;
    @Schema(description = "Application icon original file name")
    @Length(fieldName = "iconFileName")
    private String iconFileName;
    @Schema(description = "Application status: ACTIVE or DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private AndroidAppStatus status;
    @Schema(description = "Timestamp of the last update, in milliseconds", accessMode = Schema.AccessMode.READ_ONLY)
    private Long updatedTime;

    public AndroidApp() {
        super();
    }

    public AndroidApp(AndroidAppId id) {
        super(id);
    }

    public AndroidApp(AndroidApp androidApp) {
        super(androidApp);
        this.tenantId = androidApp.tenantId;
        this.title = androidApp.title;
        this.pkgName = androidApp.pkgName;
        this.description = androidApp.description;
        this.iconUrl = androidApp.iconUrl;
        this.iconFileName = androidApp.iconFileName;
        this.status = androidApp.status;
        this.updatedTime = androidApp.updatedTime;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Android application title", accessMode = Schema.AccessMode.READ_ONLY)
    public String getName() {
        return title;
    }
}
