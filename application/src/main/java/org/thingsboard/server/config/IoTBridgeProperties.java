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
package org.thingsboard.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IoT bridge configuration: water platform -> IoT platform passwordless jump (assertion exchange + account provisioning).
 * Binds to {@code security.iot_bridge} section of thingsboard.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.iot-bridge")
public class IoTBridgeProperties {

    /** Master switch of the IoT bridge endpoints. */
    private boolean enabled;

    /** RSA public key (X.509 SubjectPublicKeyInfo, Base64 without PEM headers) of the assertion signer (water platform). */
    private String rsaPublicKey;

    /** Expected issuer (iss) claim of the assertion. */
    private String issuer;

    /** Expected audience (aud) claim of the assertion. */
    private String audience;

    /** Allowed clock skew in seconds when verifying assertion exp/iat. */
    private long clockSkewSeconds = 60;

    /** TTL in seconds for consumed assertion jti (must be >= assertion validity window). */
    private long jtiCacheTtlSeconds = 300;

}
