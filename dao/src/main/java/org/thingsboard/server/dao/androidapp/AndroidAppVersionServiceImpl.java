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
package org.thingsboard.server.dao.androidapp;

import com.google.common.util.concurrent.FluentFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.androidapp.AndroidAppUpdateInfo;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersion;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus;
import org.thingsboard.server.common.data.id.AndroidAppVersionId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

import java.util.Optional;
import java.util.UUID;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

@Slf4j
@Service
public class AndroidAppVersionServiceImpl extends AbstractEntityService implements AndroidAppVersionService {

    @Autowired
    private AndroidAppVersionDao androidAppVersionDao;
    @Autowired
    private DataValidator<AndroidAppVersion> androidAppVersionDataValidator;

    @Override
    public AndroidAppVersion saveAndroidAppVersion(TenantId tenantId, AndroidAppVersion version) {
        log.trace("Executing saveAndroidAppVersion [{}]", version);
        androidAppVersionDataValidator.validate(version, v -> tenantId);
        try {
            AndroidAppVersion savedVersion = androidAppVersionDao.save(tenantId, version);
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedVersion).build());
            return savedVersion;
        } catch (Exception e) {
            checkConstraintViolation(e, "tb_android_app_version_app_code_unq_key", "Version code is already used for this app!");
            checkConstraintViolation(e, "uq_tb_android_app_version_published", "Another version of this app is already published!");
            throw e;
        }
    }

    @Override
    public AndroidAppVersion findAndroidAppVersionById(TenantId tenantId, AndroidAppVersionId versionId) {
        log.trace("Executing findAndroidAppVersionById [{}] [{}]", tenantId, versionId);
        return androidAppVersionDao.findById(tenantId, versionId.getId());
    }

    @Override
    public PageData<AndroidAppVersion> findAndroidAppVersionsByAppId(TenantId tenantId, UUID appId, PageLink pageLink) {
        log.trace("Executing findAndroidAppVersionsByAppId [{}] [{}]", tenantId, appId);
        Validator.validatePageLink(pageLink);
        return androidAppVersionDao.findByAppId(tenantId, appId, null, pageLink);
    }

    @Override
    @Transactional
    public AndroidAppVersion publishAndroidAppVersion(TenantId tenantId, AndroidAppVersionId versionId) {
        log.trace("Executing publishAndroidAppVersion [{}] [{}]", tenantId, versionId);
        AndroidAppVersion version = getVersionForTenant(tenantId, versionId);
        // Retract the currently published version of the same app, if any;
        androidAppVersionDao.findLatestPublishedByAppId(version.getAppId().getId())
                .filter(published -> !published.getId().equals(versionId.getId()))
                .ifPresent(published -> {
                    published.setStatus(AndroidAppVersionStatus.RETRACTED);
                    published.setUpdatedTime(System.currentTimeMillis());
                    androidAppVersionDao.save(tenantId, published);
                });
        version.setStatus(AndroidAppVersionStatus.PUBLISHED);
        version.setUpdatedTime(System.currentTimeMillis());
        AndroidAppVersion savedVersion = androidAppVersionDao.save(tenantId, version);
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedVersion).build());
        return savedVersion;
    }

    @Override
    public AndroidAppVersion retractAndroidAppVersion(TenantId tenantId, AndroidAppVersionId versionId) {
        log.trace("Executing retractAndroidAppVersion [{}] [{}]", tenantId, versionId);
        AndroidAppVersion version = getVersionForTenant(tenantId, versionId);
        version.setStatus(AndroidAppVersionStatus.RETRACTED);
        version.setUpdatedTime(System.currentTimeMillis());
        AndroidAppVersion savedVersion = androidAppVersionDao.save(tenantId, version);
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedVersion).build());
        return savedVersion;
    }

    @Override
    public void deleteAndroidAppVersionById(TenantId tenantId, AndroidAppVersionId versionId) {
        log.trace("Executing deleteAndroidAppVersionById [{}]", versionId.getId());
        androidAppVersionDao.removeById(tenantId, versionId.getId());
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(versionId).build());
    }

    @Override
    public AndroidAppUpdateInfo checkAppUpdate(String pkgName, int clientVersionCode) {
        log.trace("Executing checkAppUpdate, pkgName [{}], clientVersionCode [{}]", pkgName, clientVersionCode);
        return androidAppVersionDao.findLatestPublishedByPkgName(pkgName)
                .map(latest -> {
                    boolean hasUpdate = latest.getVersionCode() != null && latest.getVersionCode() > clientVersionCode;
                    return new AndroidAppUpdateInfo(hasUpdate, latest.getVersionName(), latest.getVersionCode(),
                            latest.getReleaseNotes(), latest.getApkUrl(), latest.isForceUpdate());
                })
                .orElse(new AndroidAppUpdateInfo(false, null, null, null, null, false));
    }

    private AndroidAppVersion getVersionForTenant(TenantId tenantId, AndroidAppVersionId versionId) {
        AndroidAppVersion version = androidAppVersionDao.findById(tenantId, versionId.getId());
        if (version == null) {
            throw new DataValidationException("Android app version with such id does not exist!");
        }
        return version;
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findAndroidAppVersionById(tenantId, new AndroidAppVersionId(entityId.getId())));
    }

    @Override
    public FluentFuture<Optional<HasId<?>>> findEntityAsync(TenantId tenantId, EntityId entityId) {
        return FluentFuture.from(androidAppVersionDao.findByIdAsync(tenantId, entityId.getId()))
                .transform(Optional::ofNullable, directExecutor());
    }

    @Override
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteAndroidAppVersionById(tenantId, (AndroidAppVersionId) id);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ANDROID_APP_VERSION;
    }

}
