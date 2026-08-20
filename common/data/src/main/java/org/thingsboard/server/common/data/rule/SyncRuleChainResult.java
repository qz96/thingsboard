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
import java.util.ArrayList;
import java.util.List;

/**
 * 跨租户规则链同步的整体结果。
 */
@Data
public class SyncRuleChainResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源链是否通过自包含检测。false 时同步整体终止。 */
    private boolean sourceSelfContained;

    /** 参与遍历的租户总数（不含源租户）。 */
    private int totalTenants;

    /** 覆盖更新的租户数。 */
    private int updated;

    /** 新建的租户数。 */
    private int created;

    /** 跳过的租户数。 */
    private int skipped;

    /** 失败租户数。 */
    private int failed;

    /** 整体备注（如源链非自包含时的违规说明）。 */
    private String message;

    /** 每租户明细。 */
    private List<TenantSyncDetail> tenants = new ArrayList<>();
}
