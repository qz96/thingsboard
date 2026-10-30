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
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.androidapp.AndroidAppDao;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

@Component
@AllArgsConstructor
public class AndroidAppDataValidator extends DataValidator<AndroidApp> {

    private final AndroidAppDao androidAppDao;

    @Override
    protected void validateDataImpl(TenantId tenantId, AndroidApp androidApp) {
        Validator.validateString("Android app title", androidApp.getTitle());
        Validator.validateString("Android app package name", androidApp.getPkgName());
        if (androidAppDao.findByTenantIdAndPkgName(tenantId, androidApp.getPkgName())
                .filter(found -> !found.getId().equals(androidApp.getId()))
                .isPresent()) {
            throw new DataValidationException("Android app with such package name already exists!");
        }
    }

    @Override
    protected void validateCreate(TenantId tenantId, AndroidApp androidApp) {
        if (androidApp.getCreatedTime() == 0) {
            androidApp.setCreatedTime(System.currentTimeMillis());
        }
    }

}
