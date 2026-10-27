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
package org.thingsboard.server.dao.service.validator;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.androidapp.AndroidApp;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersion;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.androidapp.AndroidAppDao;
import org.thingsboard.server.dao.androidapp.AndroidAppVersionDao;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

@Component
@AllArgsConstructor
public class AndroidAppVersionDataValidator extends DataValidator<AndroidAppVersion> {

    private final AndroidAppDao androidAppDao;
    private final AndroidAppVersionDao androidAppVersionDao;

    @Override
    protected void validateDataImpl(TenantId tenantId, AndroidAppVersion version) {
        Validator.validateString("Version name", version.getVersionName());
        if (version.getVersionCode() == null || version.getVersionCode() <= 0) {
            throw new DataValidationException("Version code must be a positive integer!");
        }
        AndroidApp androidApp = androidAppDao.findById(tenantId, version.getAppId().getId());
        if (androidApp == null) {
            throw new DataValidationException("Android app with such id does not exist!");
        }
        if (version.getStatus() == AndroidAppVersionStatus.PUBLISHED &&
                (version.getApkUrl() == null || version.getApkUrl().isBlank())) {
            throw new DataValidationException("APK url is required for published versions!");
        }
    }

    @Override
    protected void validateCreate(TenantId tenantId, AndroidAppVersion version) {
        if (version.getCreatedTime() == 0) {
            version.setCreatedTime(System.currentTimeMillis());
        }
    }

    @Override
    protected AndroidAppVersion validateUpdate(TenantId tenantId, AndroidAppVersion version) {
        AndroidAppVersion existing = androidAppVersionDao.findById(tenantId, version.getId().getId());
        if (existing == null) {
            throw new DataValidationException("Android app version with such id does not exist!");
        }
        if (version.getStatus() == AndroidAppVersionStatus.PUBLISHED && existing.getStatus() != AndroidAppVersionStatus.PUBLISHED &&
                (version.getApkUrl() == null || version.getApkUrl().isBlank())) {
            throw new DataValidationException("APK url is required for published versions!");
        }
        return existing;
    }

}
