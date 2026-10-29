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
import org.thingsboard.server.common.data.androidapp.AndroidAppDisplay;
import org.thingsboard.server.common.data.id.AndroidAppDisplayId;
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

import java.util.List;
import java.util.Optional;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

@Slf4j
@Service
public class AndroidAppDisplayServiceImpl extends AbstractEntityService implements AndroidAppDisplayService {

    @Autowired
    private AndroidAppDisplayDao androidAppDisplayDao;
    @Autowired
    private DataValidator<AndroidAppDisplay> androidAppDisplayDataValidator;

    @Override
    public AndroidAppDisplay saveAndroidAppDisplay(TenantId tenantId, AndroidAppDisplay androidAppDisplay) {
        log.trace("Executing saveAndroidAppDisplay [{}]", androidAppDisplay);
        androidAppDisplayDataValidator.validate(androidAppDisplay, d -> tenantId);
        AndroidAppDisplay savedAndroidAppDisplay = androidAppDisplayDao.save(tenantId, androidAppDisplay);
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedAndroidAppDisplay).build());
        return savedAndroidAppDisplay;
    }

    @Override
    public AndroidAppDisplay findAndroidAppDisplayById(TenantId tenantId, AndroidAppDisplayId androidAppDisplayId) {
        log.trace("Executing findAndroidAppDisplayById [{}] [{}]", tenantId, androidAppDisplayId);
        return androidAppDisplayDao.findById(tenantId, androidAppDisplayId.getId());
    }

    @Override
    public PageData<AndroidAppDisplay> findAndroidAppDisplaysByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findAndroidAppDisplaysByTenantId [{}]", tenantId);
        Validator.validatePageLink(pageLink);
        return androidAppDisplayDao.findByTenantId(tenantId, pageLink);
    }

    @Override
    public AndroidAppDisplay setAndroidAppDisplayShowOnHome(TenantId tenantId, AndroidAppDisplayId androidAppDisplayId, boolean showOnHome) {
        log.trace("Executing setAndroidAppDisplayShowOnHome [{}] [{}] [{}]", tenantId, androidAppDisplayId, showOnHome);
        AndroidAppDisplay androidAppDisplay = getAndroidAppDisplayForTenant(tenantId, androidAppDisplayId);
        androidAppDisplay.setShowOnHome(showOnHome);
        androidAppDisplay.setUpdatedTime(System.currentTimeMillis());
        AndroidAppDisplay savedAndroidAppDisplay = androidAppDisplayDao.save(tenantId, androidAppDisplay);
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedAndroidAppDisplay).build());
        return savedAndroidAppDisplay;
    }

    @Override
    public List<AndroidAppDisplay> findAndroidAppDisplaysByShowOnHome(TenantId tenantId, boolean showOnHome) {
        log.trace("Executing findAndroidAppDisplaysByShowOnHome [{}] [{}]", tenantId, showOnHome);
        return androidAppDisplayDao.findByTenantIdAndShowOnHome(tenantId, showOnHome);
    }

    @Override
    public void deleteAndroidAppDisplayById(TenantId tenantId, AndroidAppDisplayId androidAppDisplayId) {
        log.trace("Executing deleteAndroidAppDisplayById [{}]", androidAppDisplayId.getId());
        androidAppDisplayDao.removeById(tenantId, androidAppDisplayId.getId());
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(androidAppDisplayId).build());
    }

    private AndroidAppDisplay getAndroidAppDisplayForTenant(TenantId tenantId, AndroidAppDisplayId androidAppDisplayId) {
        AndroidAppDisplay androidAppDisplay = androidAppDisplayDao.findById(tenantId, androidAppDisplayId.getId());
        if (androidAppDisplay == null) {
            throw new DataValidationException("Android app display config with such id does not exist!");
        }
        return androidAppDisplay;
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findAndroidAppDisplayById(tenantId, new AndroidAppDisplayId(entityId.getId())));
    }

    @Override
    public FluentFuture<Optional<HasId<?>>> findEntityAsync(TenantId tenantId, EntityId entityId) {
        return FluentFuture.from(androidAppDisplayDao.findByIdAsync(tenantId, entityId.getId()))
                .transform(Optional::ofNullable, directExecutor());
    }

    @Override
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteAndroidAppDisplayById(tenantId, (AndroidAppDisplayId) id);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ANDROID_APP_DISPLAY;
    }

}
