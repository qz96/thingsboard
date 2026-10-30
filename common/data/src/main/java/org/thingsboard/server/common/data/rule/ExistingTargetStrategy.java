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

/**
 * 目标租户「已存在同名规则链」时的处理策略。
 */
public enum ExistingTargetStrategy {

    /** 无条件覆盖（第一次铺模板或明确要强推时使用）。 */
    OVERWRITE_ALWAYS,

    /**
     * 仅覆盖「上次同步后未被本地修改」的租户（默认）：
     * 目标链当前 checksum == 上次同步写入的 checksum 才覆盖；
     * 被本地改过的租户标记为 DRIFTED 跳过，留给人工确认。
     */
    OVERWRITE_UNCHANGED,

    /** 已存在即跳过，只给缺失的租户新增。 */
    SKIP_EXISTING
}
