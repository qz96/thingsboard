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
package org.thingsboard.server.dao.model.sql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersion;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus;
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.AndroidAppVersionId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.TENANT_ID_COLUMN;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.ANDROID_APP_VERSION_TABLE_NAME)
public class AndroidAppVersionEntity extends BaseSqlEntity<AndroidAppVersion> {

    @Column(name = TENANT_ID_COLUMN)
    private UUID tenantId;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_APP_ID_PROPERTY)
    private UUID appId;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_NAME_PROPERTY)
    private String versionName;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_CODE_PROPERTY)
    private Integer versionCode;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_RELEASE_NOTES_PROPERTY)
    private String releaseNotes;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_APK_URL_PROPERTY)
    private String apkUrl;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_APK_FILE_NAME_PROPERTY)
    private String apkFileName;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_APK_SIZE_PROPERTY)
    private Long apkSize;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_APK_MD5_PROPERTY)
    private String apkMd5;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_PREVIEW_URL_PROPERTY)
    private String previewUrl;

    @Column(name = ModelConstants.ANDROID_APP_VERSION_FORCE_UPDATE_PROPERTY)
    private boolean forceUpdate;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.ANDROID_APP_VERSION_STATUS_PROPERTY)
    private AndroidAppVersionStatus status;

    @Column(name = ModelConstants.UPDATED_TIME_PROPERTY)
    private Long updatedTime;

    public AndroidAppVersionEntity() {
        super();
    }

    public AndroidAppVersionEntity(AndroidAppVersion version) {
        super(version);
        if (version.getTenantId() != null) {
            this.tenantId = version.getTenantId().getId();
        }
        if (version.getAppId() != null) {
            this.appId = version.getAppId().getId();
        }
        this.versionName = version.getVersionName();
        this.versionCode = version.getVersionCode();
        this.releaseNotes = version.getReleaseNotes();
        this.apkUrl = version.getApkUrl();
        this.apkFileName = version.getApkFileName();
        this.apkSize = version.getApkSize();
        this.apkMd5 = version.getApkMd5();
        this.previewUrl = version.getPreviewUrl();
        this.forceUpdate = version.isForceUpdate();
        this.status = version.getStatus();
        this.updatedTime = version.getUpdatedTime();
    }

    @Override
    public AndroidAppVersion toData() {
        AndroidAppVersion version = new AndroidAppVersion(new AndroidAppVersionId(id));
        if (tenantId != null) {
            version.setTenantId(TenantId.fromUUID(tenantId));
        }
        if (appId != null) {
            version.setAppId(new AndroidAppId(appId));
        }
        version.setCreatedTime(createdTime);
        version.setVersionName(versionName);
        version.setVersionCode(versionCode);
        version.setReleaseNotes(releaseNotes);
        version.setApkUrl(apkUrl);
        version.setApkFileName(apkFileName);
        version.setApkSize(apkSize);
        version.setApkMd5(apkMd5);
        version.setPreviewUrl(previewUrl);
        version.setForceUpdate(forceUpdate);
        version.setStatus(status);
        version.setUpdatedTime(updatedTime);
        return version;
    }
}
