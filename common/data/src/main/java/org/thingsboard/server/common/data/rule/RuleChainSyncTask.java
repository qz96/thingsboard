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
 * 异步规则链同步任务（平台模板批量下发到几十个租户时使用）。
 * 由 {@code POST /api/admin/ruleChain/sync/async} 创建，通过 {@code GET /api/admin/ruleChain/sync/task/{id}} 轮询。
 */
@Data
public class RuleChainSyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /** RUNNING / DONE / FAILED */
    private String status;

    /** 目标租户总数 */
    private int total;

    /** 已处理租户数（含跳过/失败） */
    private int processed;

    /** 当前正在处理的租户名，便于前端显示进度 */
    private String currentTenant;

    private long startedAt;

    private long finishedAt;

    /** 失败时的整体原因（源链非自包含、参数错误等） */
    private String message;

    /** 逐租户明细（任务完成后填充，与同步接口同结构） */
    private SyncRuleChainResult result;
}
