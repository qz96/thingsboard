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
 * 跨租户规则链同步请求。
 * 语义：遍历所有其他租户，按名称定位同名规则链；
 * 不存在时按 {@link #missingTargetStrategy} 决定 新增 或 跳过，存在时覆盖写回。
 */
@Data
public class SyncRuleChainRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源租户 ID（SYS_ADMIN 不属于任何租户，必须显式指定）。必填。 */
    private String sourceTenantId;

    /** 源规则链 ID（精确）。与 sourceRuleChainName 二选一。 */
    private String sourceRuleChainId;

    /** 源规则链名称。与 sourceRuleChainId 二选一，需配合 ruleChainType 定位。 */
    private String sourceRuleChainName;

    /** 规则链类型，默认 CORE。可选 CORE / EDGE 等。 */
    private String ruleChainType;

    /** 目标租户无同名链时的策略，默认 CREATE（新增）。可选 SKIP（跳过）。 */
    private MissingTargetStrategy missingTargetStrategy = MissingTargetStrategy.CREATE;

    /** 仅预览将要影响的租户，不实际写入。默认 false。 */
    private boolean dryRun = false;
}
