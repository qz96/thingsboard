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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.AndroidAppVersionId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.Length;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class AndroidAppVersion extends BaseData<AndroidAppVersionId> implements HasTenantId {

    @Schema(description = "JSON object with Tenant Id")
    private TenantId tenantId;
    @Schema(description = "JSON object with the Android app Id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private AndroidAppId appId;
    @Schema(description = "Version name, e.g. 1.2.3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Length(fieldName = "versionName")
    private String versionName;
    @Schema(description = "Version code, monotonically increasing integer. Unique within the app", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Positive
    private Integer versionCode;
    @Schema(description = "Release notes")
    @Length(fieldName = "releaseNotes")
    private String releaseNotes;
    @Schema(description = "APK download URL, hosted by the company shared file service")
    @Length(fieldName = "apkUrl", max = 2048)
    private String apkUrl;
    @Schema(description = "APK original file name")
    @Length(fieldName = "apkFileName")
    private String apkFileName;
    @Schema(description = "APK file size in bytes")
    @Positive
    private Long apkSize;
    @Schema(description = "APK file MD5 checksum")
    @Length(fieldName = "apkMd5")
    private String apkMd5;
    @Schema(description = "Attachment preview URL, provided by the company shared file preview service")
    @Length(fieldName = "previewUrl", max = 2048)
    private String previewUrl;
    @Schema(description = "Whether the update is mandatory")
    private boolean forceUpdate;
    @Schema(description = "Version status: DRAFT, PUBLISHED or RETRACTED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private AndroidAppVersionStatus status;
    @Schema(description = "Timestamp of the last update, in milliseconds", accessMode = Schema.AccessMode.READ_ONLY)
    private Long updatedTime;

    public AndroidAppVersion() {
        super();
    }

    public AndroidAppVersion(AndroidAppVersionId id) {
        super(id);
    }

    public AndroidAppVersion(AndroidAppVersion version) {
        super(version);
        this.tenantId = version.tenantId;
        this.appId = version.appId;
        this.versionName = version.versionName;
        this.versionCode = version.versionCode;
        this.releaseNotes = version.releaseNotes;
        this.apkUrl = version.apkUrl;
        this.apkFileName = version.apkFileName;
        this.apkSize = version.apkSize;
        this.apkMd5 = version.apkMd5;
        this.previewUrl = version.previewUrl;
        this.forceUpdate = version.forceUpdate;
        this.status = version.status;
        this.updatedTime = version.updatedTime;
    }
}
