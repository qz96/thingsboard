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
package org.thingsboard.server.service.security.auth.iotbridge;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.dao.sso.SsoAccountDao;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.TbLogEntityActionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Idempotent provisioning of IoT platform accounts from a verified water platform assertion.
 * <p>
 * Model (v2):
 * <ul>
 *   <li>One <b>project</b> (assertion {@code projectId}) = one tenant. The tenant title is {@code projectName}.</li>
 *   <li>One <b>(waterUserId, projectId)</b> pair = one IoT account, tracked in {@code tb_sso_account}.</li>
 *   <li>Assertion {@code roles} contains {@code PROJECT_ADMIN} -&gt; {@link Authority#TENANT_ADMIN},
 *       otherwise {@link Authority#TENANT_VIEWER} (read only).</li>
 *   <li>At most one tenant administrator per tenant. Promotion transfers the administrator role:
 *       the previous administrator is demoted to {@code TENANT_VIEWER}.</li>
 *   <li>Last-admin protection: an administrator is never demoted when he is the only one left, even if the
 *       assertion no longer carries {@code PROJECT_ADMIN} (prevents an unmanageable region).</li>
 * </ul>
 */
@Service
@TbCoreComponent
@Slf4j
@RequiredArgsConstructor
public class IoTProvisionService {

    public static final String WATER_USER_ID = "waterUserId";
    public static final String WATER_PROJECT_ID = "waterProjectId";
    public static final String WATER_PRIMARY_ADMIN = "waterPrimaryAdmin";
    public static final String WATER_EMAIL = "waterEmail";
    public static final String WATER_ORG_ID = "waterOrgId";
    public static final String WATER_AREA_ID = "waterAreaId";

    public static final String ROLE_PROJECT_ADMIN = "PROJECT_ADMIN";

    private static final String LOGIN_PREFIX = "sso-";
    private static final String LOGIN_DOMAIN_SUFFIX = ".iot";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-z0-9-]");

    private final TenantService tenantService;
    private final UserService userService;
    private final SsoAccountDao ssoAccountDao;
    private final TbLogEntityActionService logEntityActionService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Ensures the project tenant and the account exist, and keeps the account role in sync with the assertion.
     */
    public synchronized ProvisionResult ensureAccount(Claims claims) throws ThingsboardException {
        String waterUserId = trimToNull(claims.getSubject());
        String projectId = trimToNull(readTopClaim(claims, "projectId"));
        String projectName = trimToNull(readTopClaim(claims, "projectName"));
        List<String> roles = readRoles(claims);
        String waterLogin = trimToNull(readProfileClaim(claims, "login"));
        String userName = trimToNull(readProfileClaim(claims, "userName"));
        String firstName = trimToNull(readProfileClaim(claims, "firstName"));
        String jobNumber = trimToNull(readProfileClaim(claims, "jobNumber"));

        if (waterUserId == null) {
            throw new ThingsboardException("Assertion subject (water user id) is missing", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (projectId == null) {
            throw new ThingsboardException("Assertion projectId is missing", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (waterLogin != null && !EMAIL_PATTERN.matcher(waterLogin).matches()) {
            throw new ThingsboardException("Assertion profile.login is invalid", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (userName == null && projectName == null) {
            throw new ThingsboardException("Assertion projectName or profile.userName is missing", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        Authority targetAuthority = roles.stream().anyMatch(r -> ROLE_PROJECT_ADMIN.equalsIgnoreCase(r))
                ? Authority.TENANT_ADMIN : Authority.TENANT_VIEWER;
        boolean primaryAdmin = Boolean.parseBoolean(String.valueOf(claims.getOrDefault("primaryAdmin", "false")));

        Tenant tenant = resolveTenant(projectId, projectName != null ? projectName : userName, claims);
        Authority effectiveAuthority = resolveEffectiveAuthority(tenant, waterUserId, targetAuthority, projectId, primaryAdmin);

        // 1. Existing mapping -> sync role only
        UUID mappedUserId = ssoAccountDao.findUserId(waterUserId, projectId);
        if (mappedUserId != null) {
            User existing = userService.findUserById(tenant.getId(), new UserId(mappedUserId));
            if (existing == null) {
                throw new ThingsboardException("SSO mapping points to a missing account [" + mappedUserId + "]",
                        ThingsboardErrorCode.GENERAL);
            }
            syncRole(tenant, existing, effectiveAuthority, projectId, roles, waterUserId);
            ssoAccountDao.upsert(waterUserId, projectId, existing.getId().getId(), currentAuthority(existing).name());
            return new ProvisionResult(existing, tenant, false);
        }

        // 2. Account exists but mapping is missing (e.g. the mapping table was rebuilt) -> adopt it
        String email = buildAccountLogin(waterUserId, projectId);
        UUID existingByEmail = ssoAccountDao.findUserIdByEmail(email);
        if (existingByEmail != null) {
            User existing = userService.findUserById(tenant.getId(), new UserId(existingByEmail));
            if (existing == null) {
                throw new ThingsboardException("Account [" + email + "] belongs to another tenant",
                        ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
            ssoAccountDao.upsert(waterUserId, projectId, existing.getId().getId(), currentAuthority(existing).name());
            syncRole(tenant, existing, effectiveAuthority, projectId, roles, waterUserId);
            return new ProvisionResult(existing, tenant, false);
        }

        // 3. Create a new account
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setAuthority(effectiveAuthority);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName : userName);
        var info = JacksonUtil.newObjectNode();
        info.put(WATER_USER_ID, waterUserId);
        info.put(WATER_PROJECT_ID, projectId);
        putIfPresent(info, WATER_EMAIL, waterLogin);
        putIfPresent(info, "jobNumber", jobNumber);
        putIfPresent(info, WATER_ORG_ID, readTopClaim(claims, WATER_ORG_ID));
        putIfPresent(info, WATER_AREA_ID, readTopClaim(claims, WATER_AREA_ID));
        user.setAdditionalInfo(info);
        user = userService.saveUser(tenant.getId(), user);

        ssoAccountDao.upsert(waterUserId, projectId, user.getId().getId(), effectiveAuthority.name());
        activateCredentials(tenant.getId(), user, waterUserId);

        log.info("[SSO] Created account [{}] as {} in project [{}] (tenant [{}])",
                email, effectiveAuthority, projectId, tenant.getTitle());
        return new ProvisionResult(user, tenant, true);
    }

    private Tenant resolveTenant(String projectId, String title, Claims claims) {
        UUID tenantId = ssoAccountDao.findTenantIdByProjectId(projectId);
        if (tenantId != null) {
            Tenant tenant = tenantService.findTenantById(new TenantId(tenantId));
            if (tenant != null) {
                return tenant;
            }
        }
        Tenant tenant = new Tenant();
        tenant.setTitle(title);
        var info = JacksonUtil.newObjectNode();
        info.put(WATER_PROJECT_ID, projectId);
        putIfPresent(info, WATER_ORG_ID, readTopClaim(claims, WATER_ORG_ID));
        putIfPresent(info, WATER_AREA_ID, readTopClaim(claims, WATER_AREA_ID));
        tenant.setAdditionalInfo(info);
        tenant = tenantService.saveTenant(tenant);
        log.info("[SSO] Created tenant [{}] for project [{}]", tenant.getTitle(), projectId);
        return tenant;
    }

    /**
     * Single-administrator rule: when the project already has an administrator, another PROJECT_ADMIN account
     * only takes over if it is explicitly flagged as primary or has a "smaller" water user id (deterministic,
     * so that repeated logins converge instead of flipping the role back and forth).
     */
    private Authority resolveEffectiveAuthority(Tenant tenant, String waterUserId, Authority targetAuthority,
                                                String projectId, boolean primaryAdmin) {
        if (targetAuthority != Authority.TENANT_ADMIN) {
            return targetAuthority;
        }
        List<UUID> adminIds = ssoAccountDao.findUserIdsByAuthority(tenant.getId().getId(), Authority.TENANT_ADMIN.name());

        // A sticky "primary administrator" designation wins over the deterministic id based tie-break,
        // otherwise a non-primary account with a smaller id would take the role back on its next login.
        String recordedPrimary = readTenantPrimaryAdmin(tenant);
        if (primaryAdmin) {
            if (recordedPrimary == null || !recordedPrimary.equalsIgnoreCase(waterUserId)) {
                recordTenantPrimaryAdmin(tenant, waterUserId);
            }
        } else if (recordedPrimary != null && !recordedPrimary.equalsIgnoreCase(waterUserId)) {
            log.info("[SSO] Project [{}] has primary administrator [{}]; '{}' stays TENANT_VIEWER",
                    projectId, recordedPrimary, waterUserId);
            return Authority.TENANT_VIEWER;
        }

        if (adminIds.isEmpty()) {
            return Authority.TENANT_ADMIN;
        }
        String currentAdminWaterId = ssoAccountDao.findWaterUserIdByUserId(adminIds.get(0));
        if (currentAdminWaterId != null && currentAdminWaterId.equalsIgnoreCase(waterUserId)) {
            return Authority.TENANT_ADMIN;
        }
        boolean takeover = primaryAdmin
                || currentAdminWaterId == null
                || waterUserId.compareToIgnoreCase(currentAdminWaterId) < 0;
        if (!takeover) {
            log.warn("[SSO] Project [{}] already has administrator [{}]; '{}' keeps TENANT_VIEWER " +
                    "(multiple PROJECT_ADMIN in assertion)", projectId, currentAdminWaterId, waterUserId);
            return Authority.TENANT_VIEWER;
        }
        return Authority.TENANT_ADMIN;
    }

    private String readTenantPrimaryAdmin(Tenant tenant) {
        if (tenant.getAdditionalInfo() == null || !tenant.getAdditionalInfo().hasNonNull(WATER_PRIMARY_ADMIN)) {
            return null;
        }
        String value = tenant.getAdditionalInfo().get(WATER_PRIMARY_ADMIN).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private void recordTenantPrimaryAdmin(Tenant tenant, String waterUserId) {
        var info = tenant.getAdditionalInfo() instanceof com.fasterxml.jackson.databind.node.ObjectNode node
                ? node : JacksonUtil.newObjectNode();
        info.put(WATER_PRIMARY_ADMIN, waterUserId);
        tenant.setAdditionalInfo(info);
        tenantService.saveTenant(tenant);
        log.info("[SSO] Recorded primary administrator [{}] for tenant [{}]", waterUserId, tenant.getTitle());
    }

    private void syncRole(Tenant tenant, User user, Authority targetAuthority, String projectId, List<String> roles,
                          String waterUserId) {
        Authority current = currentAuthority(user);
        if (current == targetAuthority) {
            return;
        }
        List<UUID> adminIds = ssoAccountDao.findUserIdsByAuthority(tenant.getId().getId(), Authority.TENANT_ADMIN.name());

        if (targetAuthority == Authority.TENANT_ADMIN) {
            // transfer: demote the previous administrator(s), then promote this account
            for (UUID adminId : adminIds) {
                if (!adminId.equals(user.getId().getId())) {
                    User previousAdmin = userService.findUserById(tenant.getId(), new UserId(adminId));
                    ssoAccountDao.updateAuthority(adminId, Authority.TENANT_VIEWER.name());
                    if (previousAdmin != null) {
                        previousAdmin.setAuthority(Authority.TENANT_VIEWER);
                    }
                    logEntityActionService.logEntityAction(tenant.getId(), new UserId(adminId),
                            previousAdmin != null ? previousAdmin : user, ActionType.UPDATED, user,
                            "SSO role transfer: TENANT_ADMIN -> TENANT_VIEWER (replaced by " + user.getEmail() + ")");
                    log.warn("[SSO] Transferred administrator of tenant [{}] from [{}] to [{}]",
                            tenant.getTitle(), adminId, user.getEmail());
                }
            }
            ssoAccountDao.updateAuthority(user.getId().getId(), Authority.TENANT_ADMIN.name());
            user.setAuthority(Authority.TENANT_ADMIN);
            logEntityActionService.logEntityAction(tenant.getId(), user.getId(), user, ActionType.UPDATED, user,
                    "SSO role promotion: " + current + " -> TENANT_ADMIN (PROJECT_ADMIN granted)");
        } else {
            if (adminIds.size() <= 1 && adminIds.contains(user.getId().getId())) {
                log.warn("[SSO] Last admin protection: [{}] keeps TENANT_ADMIN although PROJECT_ADMIN is absent " +
                        "(tenant [{}], project [{}], roles {})", user.getEmail(), tenant.getTitle(), projectId, roles);
                return;
            }
            ssoAccountDao.updateAuthority(user.getId().getId(), Authority.TENANT_VIEWER.name());
            user.setAuthority(Authority.TENANT_VIEWER);
            logEntityActionService.logEntityAction(tenant.getId(), user.getId(), user, ActionType.UPDATED, user,
                    "SSO role demotion: " + current + " -> TENANT_VIEWER (PROJECT_ADMIN absent)");
        }
        log.info("[SSO] Role of [{}] in project [{}] changed: {} -> {}",
                user.getEmail(), projectId, current, targetAuthority);
    }

    private void activateCredentials(TenantId tenantId, User user, String waterUserId) {
        try {
            UserCredentials credentials = userService.findUserCredentialsByUserId(tenantId, user.getId());
            if (credentials == null) {
                return;
            }
            // SSO accounts never log in with a password; use a random one to keep credentials valid but unknown.
            String rawPassword = UUID.randomUUID() + "$" + waterUserId;
            userService.activateUserCredentials(tenantId, credentials.getActivateToken(), passwordEncoder.encode(rawPassword));
        } catch (Exception e) {
            log.warn("[SSO] Failed to activate credentials for [{}]: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Builds a stable login which is decoupled from the real email address, so that changing the email in the
     * water platform never creates a duplicate account.
     */
    static String buildAccountLogin(String waterUserId, String projectId) {
        String code = sanitize(projectId);
        if (code.isEmpty()) {
            code = "project";
        }
        return LOGIN_PREFIX + sanitize(waterUserId) + "@" + code + LOGIN_DOMAIN_SUFFIX;
    }

    private static String sanitize(String value) {
        String result = UNSAFE_CHARS.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        result = result.replaceAll("-{2,}", "-");
        result = result.replaceAll("^-+", "").replaceAll("-+$", "");
        return result;
    }

    private static Authority currentAuthority(User user) {
        return user.getAuthority() != null ? user.getAuthority() : Authority.TENANT_VIEWER;
    }

    private static void putIfPresent(com.fasterxml.jackson.databind.node.ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readRoles(Claims claims) {
        List<String> result = new ArrayList<>();
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object code = map.get("code");
                    if (code == null) {
                        code = map.get("name");
                    }
                    if (code != null) {
                        result.add(stripRolePrefix(String.valueOf(code)));
                    }
                } else if (item != null) {
                    result.add(stripRolePrefix(String.valueOf(item)));
                }
            }
        } else if (raw instanceof String str) {
            for (String part : str.split(",")) {
                result.add(stripRolePrefix(part.trim()));
            }
        }
        return result;
    }

    private static String stripRolePrefix(String value) {
        return value.startsWith("ROLE_") ? value.substring("ROLE_".length()) : value;
    }

    private static String readTopClaim(Claims claims, String key) {
        Object value = claims.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    @SuppressWarnings("unchecked")
    private static String readProfileClaim(Claims claims, String field) {
        Object profileObj = claims.get("profile");
        if (profileObj instanceof Map) {
            Object value = ((Map<String, Object>) profileObj).get(field);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        Object flat = claims.get("profile." + field);
        return flat != null ? String.valueOf(flat) : null;
    }

    public record ProvisionResult(User user, Tenant tenant, boolean created) {
    }

}
