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
package org.thingsboard.server.dao.sql.androidapp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.androidapp.AndroidAppDisplay;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.androidapp.AndroidAppDisplayDao;
import org.thingsboard.server.dao.model.sql.AndroidAppDisplayEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@SqlDao
public class JpaAndroidAppDisplayDao extends JpaAbstractDao<AndroidAppDisplayEntity, AndroidAppDisplay> implements AndroidAppDisplayDao {

    private final AndroidAppDisplayRepository androidAppDisplayRepository;

    @Override
    protected Class<AndroidAppDisplayEntity> getEntityClass() {
        return AndroidAppDisplayEntity.class;
    }

    @Override
    protected JpaRepository<AndroidAppDisplayEntity, UUID> getRepository() {
        return androidAppDisplayRepository;
    }

    @Override
    public PageData<AndroidAppDisplay> findByTenantId(TenantId tenantId, PageLink pageLink) {
        return DaoUtil.toPageData(androidAppDisplayRepository.findByTenantId(tenantId.getId(), pageLink.getTextSearch(), DaoUtil.toPageable(pageLink)));
    }

    @Override
    public List<AndroidAppDisplay> findByTenantIdAndShowOnHome(TenantId tenantId, boolean showOnHome) {
        return DaoUtil.convertDataList(androidAppDisplayRepository.findByTenantIdAndShowOnHome(tenantId.getId(), showOnHome));
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        androidAppDisplayRepository.deleteByTenantId(tenantId.getId());
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ANDROID_APP_DISPLAY;
    }

}
