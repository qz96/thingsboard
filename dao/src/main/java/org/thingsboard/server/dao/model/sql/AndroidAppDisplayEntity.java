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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.androidapp.AndroidAppDisplay;
import org.thingsboard.server.common.data.androidapp.AndroidAppDisplayPlatform;
import org.thingsboard.server.common.data.id.AndroidAppDisplayId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.TENANT_ID_COLUMN;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.ANDROID_APP_DISPLAY_TABLE_NAME)
public class AndroidAppDisplayEntity extends BaseSqlEntity<AndroidAppDisplay> {

    @Column(name = TENANT_ID_COLUMN)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.ANDROID_APP_DISPLAY_PLATFORM_TYPE_PROPERTY)
    private AndroidAppDisplayPlatform platformType;

    @Column(name = ModelConstants.ANDROID_APP_DISPLAY_APP_NAME_PROPERTY)
    private String appName;

    @Column(name = ModelConstants.ANDROID_APP_DISPLAY_DOWNLOAD_URL_PROPERTY)
    private String downloadUrl;

    @Column(name = ModelConstants.ANDROID_APP_DISPLAY_SHOW_ON_HOME_PROPERTY)
    private Boolean showOnHome;

    @Column(name = ModelConstants.ANDROID_APP_DISPLAY_SORT_ORDER_PROPERTY)
    private Integer sortOrder;

    @Column(name = ModelConstants.UPDATED_TIME_PROPERTY)
    private Long updatedTime;

    public AndroidAppDisplayEntity() {
        super();
    }

    public AndroidAppDisplayEntity(AndroidAppDisplay androidAppDisplay) {
        super(androidAppDisplay);
        if (androidAppDisplay.getTenantId() != null) {
            this.tenantId = androidAppDisplay.getTenantId().getId();
        }
        this.platformType = androidAppDisplay.getPlatformType();
        this.appName = androidAppDisplay.getAppName();
        this.downloadUrl = androidAppDisplay.getDownloadUrl();
        this.showOnHome = androidAppDisplay.getShowOnHome();
        this.sortOrder = androidAppDisplay.getSortOrder();
        this.updatedTime = androidAppDisplay.getUpdatedTime();
    }

    @JsonIgnore
    @Override
    public AndroidAppDisplay toData() {
        AndroidAppDisplay androidAppDisplay = new AndroidAppDisplay(new AndroidAppDisplayId(id));
        if (tenantId != null) {
            androidAppDisplay.setTenantId(TenantId.fromUUID(tenantId));
        }
        androidAppDisplay.setCreatedTime(createdTime);
        androidAppDisplay.setPlatformType(platformType);
        androidAppDisplay.setAppName(appName);
        androidAppDisplay.setDownloadUrl(downloadUrl);
        androidAppDisplay.setShowOnHome(showOnHome);
        androidAppDisplay.setSortOrder(sortOrder);
        androidAppDisplay.setUpdatedTime(updatedTime);
        return androidAppDisplay;
    }
}
