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
package org.thingsboard.server.dao.sql.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.DeviceEntity;

import java.util.List;
import java.util.UUID;

/**
 * Read-only statistics queries for the platform (cross-tenant) device overview.
 * Region is represented by a tenant, so all aggregations are grouped by tenant id.
 * The "active" flag is read from SERVER_SCOPE attributes, the same way as
 * device_info_active_attribute_view does (see schema-views.sql).
 */
public interface PlatformRegionStatsRepository extends JpaRepository<DeviceEntity, UUID> {

    @Query(value = """
            SELECT t.id AS tenant_id,
                   t.title AS tenant_title,
                   COUNT(DISTINCT d.id) AS device_cnt,
                   COUNT(DISTINCT CASE WHEN act.bool_v THEN d.id END) AS active_cnt,
                   COALESCE(al.cnt, 0) AS alarm_cnt
            FROM tenant t
            LEFT JOIN device d ON d.tenant_id = t.id
            LEFT JOIN attribute_kv act
                   ON act.entity_id = d.id
                  AND act.attribute_type = 2
                  AND act.attribute_key = (SELECT key_id FROM key_dictionary WHERE key = 'active')
            LEFT JOIN (
                SELECT tenant_id, COUNT(*) AS cnt
                FROM alarm
                WHERE cleared = false
                GROUP BY tenant_id
            ) al ON al.tenant_id = t.id
            GROUP BY t.id, t.title, al.cnt
            ORDER BY t.title
            """, nativeQuery = true)
    List<Object[]> findRegionOverviews();

    @Query(value = """
            SELECT t.id AS tenant_id,
                   t.title AS tenant_title,
                   COUNT(DISTINCT d.id) AS device_cnt,
                   COUNT(DISTINCT CASE WHEN act.bool_v THEN d.id END) AS active_cnt,
                   COALESCE(al.cnt, 0) AS alarm_cnt
            FROM tenant t
            LEFT JOIN device d ON d.tenant_id = t.id
            LEFT JOIN attribute_kv act
                   ON act.entity_id = d.id
                  AND act.attribute_type = 2
                  AND act.attribute_key = (SELECT key_id FROM key_dictionary WHERE key = 'active')
            LEFT JOIN (
                SELECT tenant_id, COUNT(*) AS cnt
                FROM alarm
                WHERE cleared = false
                GROUP BY tenant_id
            ) al ON al.tenant_id = t.id
            WHERE t.id = :tenantId
            GROUP BY t.id, t.title, al.cnt
            """, nativeQuery = true)
    List<Object[]> findRegionSummary(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT severity, COUNT(*) AS cnt
            FROM alarm
            WHERE tenant_id = :tenantId AND cleared = false
            GROUP BY severity
            """, nativeQuery = true)
    List<Object[]> countAlarmsBySeverity(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT d.id, d.name, d.type, d.label,
                   COALESCE(act.bool_v, false) AS active,
                   COALESCE(al.cnt, 0) AS alarm_cnt
            FROM device d
            LEFT JOIN attribute_kv act
                   ON act.entity_id = d.id
                  AND act.attribute_type = 2
                  AND act.attribute_key = (SELECT key_id FROM key_dictionary WHERE key = 'active')
            LEFT JOIN (
                SELECT originator_id, COUNT(*) AS cnt
                FROM alarm
                WHERE tenant_id = :tenantId AND cleared = false
                GROUP BY originator_id
            ) al ON al.originator_id = d.id
            WHERE d.tenant_id = :tenantId
              AND (:search = '' OR d.name ILIKE CONCAT('%', :search, '%') OR d.label ILIKE CONCAT('%', :search, '%'))
              AND (:status = 'ALL'
                   OR (:status = 'ACTIVE' AND COALESCE(act.bool_v, false) = true)
                   OR (:status = 'OFFLINE' AND COALESCE(act.bool_v, false) = false)
                   OR (:status = 'ALARM' AND COALESCE(al.cnt, 0) > 0))
            ORDER BY d.name ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findRegionDevices(@Param("tenantId") UUID tenantId,
                                     @Param("search") String search,
                                     @Param("status") String status,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*)
            FROM device d
            LEFT JOIN attribute_kv act
                   ON act.entity_id = d.id
                  AND act.attribute_type = 2
                  AND act.attribute_key = (SELECT key_id FROM key_dictionary WHERE key = 'active')
            LEFT JOIN (
                SELECT originator_id, COUNT(*) AS cnt
                FROM alarm
                WHERE tenant_id = :tenantId AND cleared = false
                GROUP BY originator_id
            ) al ON al.originator_id = d.id
            WHERE d.tenant_id = :tenantId
              AND (:search = '' OR d.name ILIKE CONCAT('%', :search, '%') OR d.label ILIKE CONCAT('%', :search, '%'))
              AND (:status = 'ALL'
                   OR (:status = 'ACTIVE' AND COALESCE(act.bool_v, false) = true)
                   OR (:status = 'OFFLINE' AND COALESCE(act.bool_v, false) = false)
                   OR (:status = 'ALARM' AND COALESCE(al.cnt, 0) > 0))
            """, nativeQuery = true)
    long countRegionDevices(@Param("tenantId") UUID tenantId,
                            @Param("search") String search,
                            @Param("status") String status);

}
