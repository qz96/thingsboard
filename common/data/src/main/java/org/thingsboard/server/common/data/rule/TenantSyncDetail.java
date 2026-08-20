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
package org.thingsboard.server.common.data.rule;

import lombok.Data;

import java.io.Serializable;

/**
 * 单个租户的同步明细。
 */
@Data
public class TenantSyncDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String tenantName;
    private TenantSyncStatus status;

    /** 写入后的规则链 ID（成功时）。 */
    private String targetRuleChainId;

    /** 说明 / 告警（如非自包含、异常原因）。 */
    private String message;
}
