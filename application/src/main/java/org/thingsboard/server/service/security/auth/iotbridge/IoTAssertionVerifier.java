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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.config.IoTBridgeProperties;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Verifies IoT bridge assertions signed by the water platform (RS256, RSA >= 2048 bits).
 * <p>
 * Validates signature, issuer, audience, expiration and consumes {@code jti} exactly once
 * (Caffeine-based in-memory store; replace with a shared cache/Redis for clustered deployments).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IoTAssertionVerifier {

    private static final String JTI_NAMESPACE = "iotBridge:jti:";

    private final IoTBridgeProperties properties;

    private JwtParser jwtParser;
    private Cache<String, Boolean> consumedJtiCache;

    @PostConstruct
    public void init() throws Exception {
        if (!properties.isEnabled()) {
            log.info("IoT bridge is disabled. Assertion verification is inactive.");
            return;
        }
        if (properties.getRsaPublicKey() == null || properties.getRsaPublicKey().isBlank()) {
            throw new IllegalStateException("security.iot_bridge.rsaPublicKey must be configured when IoT bridge is enabled");
        }
        byte[] der = Base64.getDecoder().decode(properties.getRsaPublicKey().trim());
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        this.jwtParser = Jwts.parser()
                .clockSkewSeconds(properties.getClockSkewSeconds())
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .verifyWith(publicKey)
                .build();
        this.consumedJtiCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getJtiCacheTtlSeconds()))
                .maximumSize(100_000)
                .build();
    }

    /**
     * Verifies the assertion and atomically consumes its jti.
     *
     * @return verified claims
     * @throws ThingsboardException on any invalid / replayed / expired assertion
     */
    public Claims verify(String assertion) throws ThingsboardException {
        if (!properties.isEnabled()) {
            throw new ThingsboardException("IoT bridge is disabled", ThingsboardErrorCode.GENERAL);
        }
        if (assertion == null || assertion.isBlank()) {
            throw new ThingsboardException("Missing assertion", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        Jws<Claims> jws;
        try {
            jws = jwtParser.parseSignedClaims(assertion);
        } catch (Exception e) {
            log.warn("IoT bridge assertion verification failed: {}", e.getMessage());
            throw new ThingsboardException("Invalid assertion", ThingsboardErrorCode.AUTHENTICATION);
        }
        Claims claims = jws.getPayload();
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new ThingsboardException("Assertion missing jti", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        // atomic consume: putIfAbsent returns null when the key was absent -> first use wins
        Boolean previous = consumedJtiCache.asMap().putIfAbsent(JTI_NAMESPACE + jti, Boolean.TRUE);
        if (previous != null) {
            log.warn("IoT bridge assertion jti replayed: {}", jti);
            throw new ThingsboardException("Assertion already used", ThingsboardErrorCode.AUTHENTICATION);
        }
        return claims;
    }

}
