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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.androidapp.AndroidApp;
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

import java.util.Optional;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

@Slf4j
@Service
public class AndroidAppServiceImpl extends AbstractEntityService implements AndroidAppService {

    @Autowired
    private AndroidAppDao androidAppDao;
    @Autowired
    private DataValidator<AndroidApp> androidAppDataValidator;

    @Override
    public AndroidApp saveAndroidApp(TenantId tenantId, AndroidApp androidApp) {
        log.trace("Executing saveAndroidApp [{}]", androidApp);
        androidAppDataValidator.validate(androidApp, a -> tenantId);
        try {
            AndroidApp savedAndroidApp = androidAppDao.save(tenantId, androidApp);
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedAndroidApp).build());
            return savedAndroidApp;
        } catch (Exception e) {
            checkConstraintViolation(e, "tb_android_app_tenant_pkg_unq_key", "Android app with such package name already exists!");
            throw e;
        }
    }

    @Override
    public AndroidApp findAndroidAppById(TenantId tenantId, AndroidAppId androidAppId) {
        log.trace("Executing findAndroidAppById [{}] [{}]", tenantId, androidAppId);
        return androidAppDao.findById(tenantId, androidAppId.getId());
    }

    @Override
    public PageData<AndroidApp> findAndroidAppsByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findAndroidAppsByTenantId [{}]", tenantId);
        Validator.validatePageLink(pageLink);
        return androidAppDao.findByTenantId(tenantId, pageLink);
    }

    @Override
    public void deleteAndroidAppById(TenantId tenantId, AndroidAppId androidAppId) {
        log.trace("Executing deleteAndroidAppById [{}]", androidAppId.getId());
        androidAppDao.removeById(tenantId, androidAppId.getId());
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(androidAppId).build());
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findAndroidAppById(tenantId, new AndroidAppId(entityId.getId())));
    }

    @Override
    public FluentFuture<Optional<HasId<?>>> findEntityAsync(TenantId tenantId, EntityId entityId) {
        return FluentFuture.from(androidAppDao.findByIdAsync(tenantId, entityId.getId()))
                .transform(Optional::ofNullable, directExecutor());
    }

    @Override
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteAndroidAppById(tenantId, (AndroidAppId) id);
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        log.trace("Executing deleteByTenantId, tenantId [{}]", tenantId);
        androidAppDao.deleteByTenantId(tenantId);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ANDROID_APP;
    }

}
