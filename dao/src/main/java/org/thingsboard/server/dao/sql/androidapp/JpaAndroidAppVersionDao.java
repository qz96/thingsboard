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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersion;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus;
import org.thingsboard.server.common.data.id.AndroidAppVersionId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.androidapp.AndroidAppVersionDao;
import org.thingsboard.server.dao.model.sql.AndroidAppVersionEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@SqlDao
public class JpaAndroidAppVersionDao extends JpaAbstractDao<AndroidAppVersionEntity, AndroidAppVersion> implements AndroidAppVersionDao {

    private final AndroidAppVersionRepository androidAppVersionRepository;

    @Override
    protected Class<AndroidAppVersionEntity> getEntityClass() {
        return AndroidAppVersionEntity.class;
    }

    @Override
    protected JpaRepository<AndroidAppVersionEntity, UUID> getRepository() {
        return androidAppVersionRepository;
    }

    @Override
    public PageData<AndroidAppVersion> findByAppId(TenantId tenantId, UUID appId, AndroidAppVersionStatus status, PageLink pageLink) {
        return DaoUtil.toPageData(androidAppVersionRepository.findByAppId(tenantId.getId(), appId, status, pageLink.getTextSearch(), DaoUtil.toPageable(pageLink)));
    }

    @Override
    public Optional<AndroidAppVersion> findLatestPublishedByAppId(UUID appId) {
        return Optional.ofNullable(androidAppVersionRepository.findFirstByAppIdAndStatusOrderByVersionCodeDesc(appId, AndroidAppVersionStatus.PUBLISHED)).map(DaoUtil::getData);
    }

    @Override
    public Optional<AndroidAppVersion> findLatestPublishedByPkgName(String pkgName) {
        return androidAppVersionRepository.findLatestPublishedByPkgName(pkgName, PageRequest.of(0, 1)).stream().findFirst().map(DaoUtil::getData);
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        androidAppVersionRepository.deleteByTenantId(tenantId.getId());
    }

    @Override
    public void deleteByAppId(UUID appId) {
        androidAppVersionRepository.deleteByAppId(appId);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ANDROID_APP_VERSION;
    }

}
