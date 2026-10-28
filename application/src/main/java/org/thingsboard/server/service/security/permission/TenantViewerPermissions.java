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
package org.thingsboard.server.service.security.permission;

import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.service.security.model.SecurityUser;

/**
 * Read-only role inside a single tenant (region staff).
 * Allows reading devices, assets, entity views, dashboards, alarms and their attributes/telemetry.
 * Every write operation is denied.
 */
@Component(value = "tenantViewerPermissions")
public class TenantViewerPermissions extends AbstractPermissions {

    private static final PermissionChecker tenantReadOnlyChecker =
            new PermissionChecker.GenericPermissionChecker(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY) {
                @Override
                public boolean hasPermission(SecurityUser user, Operation operation, EntityId entityId, HasTenantId entity) {
                    if (!super.hasPermission(user, operation, entityId, entity)) {
                        return false;
                    }
                    return entity == null
                            || user.getTenantId() == null
                            || user.getTenantId().equals(entity.getTenantId());
                }
            };

    private static final PermissionChecker profileReadOnlyChecker =
            new PermissionChecker.GenericPermissionChecker(Operation.READ) {
                @Override
                public boolean hasPermission(SecurityUser user, Operation operation, EntityId entityId, HasTenantId entity) {
                    if (!super.hasPermission(user, operation, entityId, entity)) {
                        return false;
                    }
                    return entity == null
                            || user.getTenantId() == null
                            || user.getTenantId().equals(entity.getTenantId());
                }
            };

    public TenantViewerPermissions() {
        super();
        put(Resource.DEVICE, tenantReadOnlyChecker);
        put(Resource.ASSET, tenantReadOnlyChecker);
        put(Resource.ENTITY_VIEW, tenantReadOnlyChecker);
        put(Resource.DASHBOARD, tenantReadOnlyChecker);
        put(Resource.ALARM, tenantReadOnlyChecker);
        put(Resource.CUSTOMER, tenantReadOnlyChecker);
        put(Resource.DEVICE_PROFILE, profileReadOnlyChecker);
        put(Resource.ASSET_PROFILE, profileReadOnlyChecker);
        put(Resource.TB_RESOURCE, profileReadOnlyChecker);
        put(Resource.RPC, new PermissionChecker.GenericPermissionChecker());
        put(Resource.USER, new PermissionChecker.GenericPermissionChecker(Operation.READ));
    }

}
