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
 * 单个租户同步结果状态。
 */
public enum TenantSyncStatus {

    /** 目标租户已存在同名链，已覆盖更新。 */
    UPDATED,

    /** 目标租户无同名链，已新增。 */
    CREATED,

    /** 目标租户无同名链，按 SKIP 策略跳过。 */
    SKIPPED,

    /** 同步失败（异常）。 */
    FAILED,

    /** 源链非自包含，整体终止（整体级别，不按租户）。 */
    SOURCE_SELF_CONTAINED_FAIL
}
