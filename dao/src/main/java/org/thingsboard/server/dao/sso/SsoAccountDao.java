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
package org.thingsboard.server.dao.sso;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the external identity mapping used by the water platform SSO bridge.
 * <p>
 * The mapping row is keyed by {@code (water_user_id, project_id)}: one water platform account inside one project
 * maps to exactly one IoT platform account. Role changes are applied directly to {@code tb_user.authority}
 * (the standard validator forbids authority updates on purpose, see {@code UserDataValidator#validateUpdate}),
 * so this DAO is the single place where the bridge is allowed to bypass that rule.
 */
@Slf4j
@Service
public class SsoAccountDao {

    @PersistenceContext
    private EntityManager em;

    public UUID findUserId(String waterUserId, String projectId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT tb_user_id FROM tb_sso_account WHERE water_user_id = :waterUserId AND project_id = :projectId")
                .setParameter("waterUserId", waterUserId)
                .setParameter("projectId", projectId)
                .getResultList();
        return rows.isEmpty() ? null : (UUID) rows.get(0);
    }

    public String findRole(String waterUserId, String projectId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT role FROM tb_sso_account WHERE water_user_id = :waterUserId AND project_id = :projectId")
                .setParameter("waterUserId", waterUserId)
                .setParameter("projectId", projectId)
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    @Transactional
    public void upsert(String waterUserId, String projectId, UUID tbUserId, String role) {
        long now = System.currentTimeMillis();
        int updated = em.createNativeQuery(
                        "UPDATE tb_sso_account SET role = :role, synced_time = :now " +
                                "WHERE water_user_id = :waterUserId AND project_id = :projectId")
                .setParameter("role", role)
                .setParameter("now", now)
                .setParameter("waterUserId", waterUserId)
                .setParameter("projectId", projectId)
                .executeUpdate();
        if (updated == 0) {
            em.createNativeQuery(
                            "INSERT INTO tb_sso_account (water_user_id, project_id, tb_user_id, role, created_time, synced_time) " +
                                    "VALUES (:waterUserId, :projectId, :tbUserId, :role, :now, :now)")
                    .setParameter("waterUserId", waterUserId)
                    .setParameter("projectId", projectId)
                    .setParameter("tbUserId", tbUserId)
                    .setParameter("role", role)
                    .setParameter("now", now)
                    .executeUpdate();
        }
    }

    public UUID findTenantIdByProjectId(String projectId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT id FROM tenant " +
                                "WHERE additional_info IS NOT NULL AND NULLIF(additional_info, '') IS NOT NULL " +
                                "  AND NULLIF(additional_info, '')::jsonb ->> 'waterProjectId' = :projectId " +
                                "LIMIT 1")
                .setParameter("projectId", projectId)
                .getResultList();
        return rows.isEmpty() ? null : (UUID) rows.get(0);
    }

    public UUID findUserIdByEmail(String email) {
        List<?> rows = em.createNativeQuery("SELECT id FROM tb_user WHERE lower(email) = :email LIMIT 1")
                .setParameter("email", email)
                .getResultList();
        return rows.isEmpty() ? null : (UUID) rows.get(0);
    }

    public String findWaterUserIdByUserId(UUID userId) {
        List<?> rows = em.createNativeQuery("SELECT water_user_id FROM tb_sso_account WHERE tb_user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    public List<UUID> findUserIdsByAuthority(UUID tenantId, String authority) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery(
                        "SELECT id FROM tb_user WHERE tenant_id = :tenantId AND authority = :authority")
                .setParameter("tenantId", tenantId)
                .setParameter("authority", authority)
                .getResultList();
        return rows;
    }

    /**
     * Updates the authority directly. Only the SSO bridge must call this method.
     */
    @Transactional
    public void updateAuthority(UUID userId, String authority) {
        int updated = em.createNativeQuery(
                        "UPDATE tb_user SET authority = :authority, version = COALESCE(version, 1) + 1 WHERE id = :userId")
                .setParameter("authority", authority)
                .setParameter("userId", userId)
                .executeUpdate();
        if (updated == 0) {
            log.warn("[SSO] Failed to update authority for user [{}] - user not found", userId);
        }
    }

}
