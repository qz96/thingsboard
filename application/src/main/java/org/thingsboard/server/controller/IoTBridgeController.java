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
package org.thingsboard.server.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.security.model.JwtPair;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.auth.iotbridge.IoTAssertionVerifier;
import org.thingsboard.server.service.security.auth.iotbridge.IoTProvisionService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IoT bridge endpoints (no-auth, guarded by assertion signature).
 * <p>
 * {@code exchange}  - verify assertion, provision (idempotent) tenant-admin, issue token pair with {@code act} claims.
 * {@code provision} - verify assertion, provision (idempotent) tenant-admin, return account info (no tokens).
 */
@RestController
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/noauth/iot")
public class IoTBridgeController {

    private final IoTAssertionVerifier assertionVerifier;
    private final IoTProvisionService provisionService;
    private final JwtTokenFactory tokenFactory;

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody ExchangeRequest request) throws ThingsboardException {
        if (request == null || request.getAssertion() == null) {
            throw new ThingsboardException("Missing assertion", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        Claims claims = assertionVerifier.verify(request.getAssertion());
        IoTProvisionService.ProvisionResult result = provisionService.ensureAccount(claims);

        SecurityUser securityUser = toSecurityUser(result.user());
        Map<String, Object> extraClaims = buildExtraClaims(claims);
        JwtPair pair = tokenFactory.createTokenPair(securityUser, extraClaims);
        return new ResponseEntity<>(pair, HttpStatus.OK);
    }

    @PostMapping("/provision")
    public ResponseEntity<?> provision(@RequestBody ExchangeRequest request) throws ThingsboardException {
        if (request == null || request.getAssertion() == null) {
            throw new ThingsboardException("Missing assertion", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        Claims claims = assertionVerifier.verify(request.getAssertion());
        IoTProvisionService.ProvisionResult result = provisionService.ensureAccount(claims);
        User user = result.user();
        Tenant tenant = result.tenant();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId().getId().toString());
        body.put("tenantId", tenant.getId().getId().toString());
        body.put("tenantTitle", tenant.getTitle());
        body.put("email", user.getEmail());
        body.put("authority", user.getAuthority().name());
        body.put("created", result.created());
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    private static SecurityUser toSecurityUser(User user) {
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
        return new SecurityUser(user, true, principal);
    }

    /**
     * Builds the extra claims injected into the issued access token: {@code act} (real operator) and {@code sso*} audit markers.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildExtraClaims(Claims claims) {
        Map<String, Object> extra = new LinkedHashMap<>();

        Map<String, Object> act = new LinkedHashMap<>();
        Object actObj = claims.get("act");
        if (actObj instanceof Map) {
            Map<String, Object> actMap = (Map<String, Object>) actObj;
            if (actMap.get("sub") != null) act.put("sub", actMap.get("sub"));
            if (actMap.get("name") != null) act.put("name", actMap.get("name"));
            if (actMap.get("uname") != null) act.put("uname", actMap.get("uname"));
        }
        if (!act.containsKey("sub")) act.put("sub", claims.getSubject());
        extra.put("act", act);

        extra.put("ssoSrc", "WATER");
        extra.put("ssoSid", claims.getId());
        extra.put("ssoAt", System.currentTimeMillis());
        return extra;
    }

    public static class ExchangeRequest {
        private String assertion;

        public String getAssertion() {
            return assertion;
        }

        public void setAssertion(String assertion) {
            this.assertion = assertion;
        }
    }

}
