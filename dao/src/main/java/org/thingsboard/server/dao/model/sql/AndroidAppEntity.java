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
import org.thingsboard.server.common.data.androidapp.AndroidApp;
import org.thingsboard.server.common.data.androidapp.AndroidAppStatus;
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.TENANT_ID_COLUMN;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.ANDROID_APP_TABLE_NAME)
public class AndroidAppEntity extends BaseSqlEntity<AndroidApp> {

    @Column(name = TENANT_ID_COLUMN)
    private UUID tenantId;

    @Column(name = ModelConstants.ANDROID_APP_TITLE_PROPERTY)
    private String title;

    @Column(name = ModelConstants.ANDROID_APP_PKG_NAME_PROPERTY)
    private String pkgName;

    @Column(name = ModelConstants.ANDROID_APP_DESCRIPTION_PROPERTY)
    private String description;

    @Column(name = ModelConstants.ANDROID_APP_ICON_URL_PROPERTY)
    private String iconUrl;

    @Column(name = ModelConstants.ANDROID_APP_ICON_FILE_NAME_PROPERTY)
    private String iconFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.ANDROID_APP_STATUS_PROPERTY)
    private AndroidAppStatus status;

    @Column(name = ModelConstants.UPDATED_TIME_PROPERTY)
    private Long updatedTime;

    public AndroidAppEntity() {
        super();
    }

    public AndroidAppEntity(AndroidApp androidApp) {
        super(androidApp);
        if (androidApp.getTenantId() != null) {
            this.tenantId = androidApp.getTenantId().getId();
        }
        this.title = androidApp.getTitle();
        this.pkgName = androidApp.getPkgName();
        this.description = androidApp.getDescription();
        this.iconUrl = androidApp.getIconUrl();
        this.iconFileName = androidApp.getIconFileName();
        this.status = androidApp.getStatus();
        this.updatedTime = androidApp.getUpdatedTime();
    }

    @Override
    public AndroidApp toData() {
        AndroidApp androidApp = new AndroidApp(new AndroidAppId(id));
        if (tenantId != null) {
            androidApp.setTenantId(TenantId.fromUUID(tenantId));
        }
        androidApp.setCreatedTime(createdTime);
        androidApp.setTitle(title);
        androidApp.setPkgName(pkgName);
        androidApp.setDescription(description);
        androidApp.setIconUrl(iconUrl);
        androidApp.setIconFileName(iconFileName);
        androidApp.setStatus(status);
        androidApp.setUpdatedTime(updatedTime);
        return androidApp;
    }
}
