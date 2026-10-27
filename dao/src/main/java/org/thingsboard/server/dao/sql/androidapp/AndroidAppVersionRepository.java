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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus;
import org.thingsboard.server.dao.model.sql.AndroidAppVersionEntity;

import java.util.List;
import java.util.UUID;

public interface AndroidAppVersionRepository extends JpaRepository<AndroidAppVersionEntity, UUID> {

    @Query("SELECT v FROM AndroidAppVersionEntity v WHERE v.tenantId = :tenantId AND v.appId = :appId AND " +
            "(:status is NULL OR v.status = :status) AND " +
            "(:searchText is NULL OR ilike(v.versionName, concat('%', :searchText, '%')) = true)")
    Page<AndroidAppVersionEntity> findByAppId(@Param("tenantId") UUID tenantId,
                                              @Param("appId") UUID appId,
                                              @Param("status") AndroidAppVersionStatus status,
                                              @Param("searchText") String searchText,
                                              Pageable pageable);

    AndroidAppVersionEntity findFirstByAppIdAndStatusOrderByVersionCodeDesc(UUID appId, AndroidAppVersionStatus status);

    @Query("SELECT v FROM AndroidAppVersionEntity v JOIN AndroidAppEntity a ON v.appId = a.id " +
            "WHERE a.pkgName = :pkgName AND v.status = org.thingsboard.server.common.data.androidapp.AndroidAppVersionStatus.PUBLISHED " +
            "ORDER BY v.versionCode DESC")
    List<AndroidAppVersionEntity> findLatestPublishedByPkgName(@Param("pkgName") String pkgName, Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM AndroidAppVersionEntity v WHERE v.tenantId = :tenantId")
    void deleteByTenantId(@Param("tenantId") UUID tenantId);

    @Transactional
    @Modifying
    @Query("DELETE FROM AndroidAppVersionEntity v WHERE v.appId = :appId")
    void deleteByAppId(@Param("appId") UUID appId);

}
