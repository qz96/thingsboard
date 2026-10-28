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
package org.thingsboard.server.dao.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.SortOrder;
import org.thingsboard.server.common.data.platform.RegionDeviceInfo;
import org.thingsboard.server.common.data.platform.RegionOverview;
import org.thingsboard.server.common.data.platform.RegionSummary;
import org.thingsboard.server.dao.sql.platform.PlatformRegionStatsRepository;
import org.thingsboard.server.dao.tenant.TenantService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformRegionStatsServiceImpl implements PlatformRegionStatsService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ALL", "ACTIVE", "OFFLINE", "ALARM");

    /**
     * Whitelist of sortable properties mapped to SQL expressions.
     * Prevents SQL injection through the sortProperty parameter.
     */
    private static final Map<String, String> SORTABLE_PROPERTIES = new LinkedHashMap<>();

    static {
        SORTABLE_PROPERTIES.put("name", "d.name");
        SORTABLE_PROPERTIES.put("type", "d.type");
        SORTABLE_PROPERTIES.put("label", "d.label");
        SORTABLE_PROPERTIES.put("active", "COALESCE(act.bool_v, false)");
        SORTABLE_PROPERTIES.put("alarmCount", "COALESCE(al.cnt, 0)");
        SORTABLE_PROPERTIES.put("createdTime", "d.created_time");
    }

    private static final String REGION_DEVICES_SQL_TEMPLATE = """
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
            ORDER BY %s %s, d.name ASC
            LIMIT :limit OFFSET :offset
            """;

    @PersistenceContext
    private EntityManager entityManager;

    private final PlatformRegionStatsRepository regionStatsRepository;
    private final TenantService tenantService;

    @Override
    public List<RegionOverview> findRegionOverviews() {
        List<Object[]> rows = regionStatsRepository.findRegionOverviews();
        List<RegionOverview> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long deviceCount = toLong(row[2]);
            long activeCount = toLong(row[3]);
            result.add(new RegionOverview(
                    (UUID) row[0],
                    (String) row[1],
                    deviceCount,
                    activeCount,
                    Math.max(deviceCount - activeCount, 0),
                    toLong(row[4])));
        }
        return result;
    }

    @Override
    public RegionSummary findRegionSummary(TenantId tenantId) {
        RegionSummary summary = new RegionSummary();
        summary.setTenantId(tenantId.getId());
        Tenant tenant = tenantService.findTenantById(tenantId);
        if (tenant != null) {
            summary.setTitle(tenant.getTitle());
        }
        List<Object[]> rows = regionStatsRepository.findRegionSummary(tenantId.getId());
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            long deviceCount = toLong(row[2]);
            long activeCount = toLong(row[3]);
            summary.setDeviceCount(deviceCount);
            summary.setActiveCount(activeCount);
            summary.setOfflineCount(Math.max(deviceCount - activeCount, 0));
            summary.setAlarmCount(toLong(row[4]));
        }
        for (Object[] row : regionStatsRepository.countAlarmsBySeverity(tenantId.getId())) {
            summary.getAlarmBySeverity().put((String) row[0], toLong(row[1]));
        }
        return summary;
    }

    @Override
    public PageData<RegionDeviceInfo> findRegionDevices(TenantId tenantId, PageLink pageLink, String status) {
        String normalizedStatus = status == null || status.isBlank() ? "ALL" : status.toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            normalizedStatus = "ALL";
        }
        String search = pageLink.getTextSearch() == null ? "" : pageLink.getTextSearch().trim();
        int pageSize = Math.max(pageLink.getPageSize(), 1);
        int page = Math.max(pageLink.getPage(), 0);
        int offset = page * pageSize;

        long totalElements = regionStatsRepository.countRegionDevices(tenantId.getId(), search, normalizedStatus);
        List<RegionDeviceInfo> data = new ArrayList<>();
        if (totalElements > offset) {
            for (Object[] row : findRegionDeviceRows(tenantId.getId(), search, normalizedStatus,
                    resolveSortProperty(pageLink.getSortOrder()), resolveSortDirection(pageLink.getSortOrder()),
                    pageSize, offset)) {
                data.add(new RegionDeviceInfo(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        row[4] != null && (Boolean) row[4],
                        toLong(row[5])));
            }
        }
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean hasNext = page + 1 < totalPages;
        return new PageData<>(data, totalPages, totalElements, hasNext);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> findRegionDeviceRows(UUID tenantId, String search, String status,
                                                String sortColumn, String sortDirection,
                                                int limit, int offset) {
        String sql = String.format(REGION_DEVICES_SQL_TEMPLATE, sortColumn, sortDirection);
        return entityManager.createNativeQuery(sql)
                .setParameter("tenantId", tenantId)
                .setParameter("search", search)
                .setParameter("status", status)
                .setParameter("limit", limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    private static String resolveSortProperty(SortOrder sortOrder) {
        if (sortOrder == null || sortOrder.getProperty() == null) {
            return SORTABLE_PROPERTIES.get("name");
        }
        String column = SORTABLE_PROPERTIES.get(sortOrder.getProperty());
        return column != null ? column : SORTABLE_PROPERTIES.get("name");
    }

    private static String resolveSortDirection(SortOrder sortOrder) {
        if (sortOrder != null && SortOrder.Direction.DESC.equals(sortOrder.getDirection())) {
            return "DESC";
        }
        return "ASC";
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

}
