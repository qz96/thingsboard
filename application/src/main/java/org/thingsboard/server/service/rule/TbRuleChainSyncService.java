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
package org.thingsboard.server.service.rule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rule.MissingTargetStrategy;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.SyncRuleChainRequest;
import org.thingsboard.server.common.data.rule.SyncRuleChainResult;
import org.thingsboard.server.common.data.rule.TenantSyncDetail;
import org.thingsboard.server.common.data.rule.TenantSyncStatus;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.common.data.sync.ie.EntityImportSettings;
import org.thingsboard.server.common.data.sync.ie.RuleChainExportData;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.sync.ie.importing.impl.RuleChainImportService;
import org.thingsboard.server.service.sync.vc.data.EntitiesImportCtx;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 跨租户规则链同步编排服务。
 *
 * <p>复用 ThingsBoard 现成的 {@link RuleChainImportService}（导入框架）做覆盖写回，
 * 由其内置：按名匹配更新/创建、节点 id 重映射、{@code saveRuleChainMetaData}，保证结构与节点配置一致。
 *
 * <p>关键实现要点（规避导入框架暗坑）：
 * <ul>
 *   <li>源链配置仅在开始时提取一次，组装为 {@link RuleChainExportData}；每个目标租户用
 *       {@link JacksonUtil#clone(Object)} 深拷贝一份，因为 {@code importEntity} 会就地修改 exportData。</li>
 *   <li>每个目标租户构造独立的 {@link EntitiesImportCtx}，并强制 {@code finalImportAttempt=true}，
 *       否则 {@code RuleChainImportService.saveOrUpdate} 不会调用 {@code saveRuleChainMetaData}，导致节点/连接不写入。</li>
 *   <li>跨租户路由靠 {@link EntitiesImportCtx#getTenantId()}，其返回 {@code user.getTenantId()}，
 *       因此为每个目标租户构造一个 tenantId=目标租户 的 {@link User}（操作人身份沿用 SYS_ADMIN 便于审计）。</li>
 *   <li>覆盖写回时保留目标租户原有规则链的 root 标记，避免错误抢占目标租户的消息入口。</li>
 *   <li>逐租户 try/catch 错误隔离，单租户失败不影响其余租户。</li>
 * </ul>
 */
@Service
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class TbRuleChainSyncService {

    private final RuleChainService ruleChainService;
    private final RuleChainImportService ruleChainImportService;
    private final TenantService tenantService;
    private final RuleChainSelfContainedChecker selfContainedChecker;

    public SyncRuleChainResult syncRuleChain(SyncRuleChainRequest request, User operator) throws ThingsboardException {
        TenantId srcTenant = parseTenantId(request.getSourceTenantId());
        RuleChain srcChain = resolveSource(srcTenant, request);
        RuleChainMetaData srcMeta = ruleChainService.loadRuleChainMetaData(srcTenant, srcChain.getId());

        List<String> violations = selfContainedChecker.check(srcChain, srcMeta);
        SyncRuleChainResult result = new SyncRuleChainResult();
        if (!violations.isEmpty()) {
            result.setSourceSelfContained(false);
            result.setMessage("源规则链非自包含，同步已终止: " + String.join("; ", violations));
            return result;
        }
        result.setSourceSelfContained(true);

        // 一次性提取源链配置（实体 + 元数据），后续每租户深拷贝
        RuleChainExportData original = new RuleChainExportData();
        original.setEntity(srcChain);
        original.setMetaData(srcMeta);
        // entityType 是 @JsonTbEntity/@JsonTypeInfo 多态类型判定依据，缺失会导致 JacksonUtil.clone 反序列化失败
        original.setEntityType(EntityType.RULE_CHAIN);

        RuleChainType type = srcChain.getType();
        String name = srcChain.getName();

        PageLink pageLink = new PageLink(50);
        PageData<Tenant> tenants;
        do {
            tenants = tenantService.findTenants(pageLink);
            for (Tenant tenant : tenants.getData()) {
                if (tenant.getId().equals(srcTenant)) {
                    continue;
                }
                result.setTotalTenants(result.getTotalTenants() + 1);
                result.getTenants().add(processTenant(tenant, type, name, original, request, operator, result));
            }
            pageLink = pageLink.nextPageLink();
        } while (tenants.hasNext());

        return result;
    }

    private TenantSyncDetail processTenant(Tenant tenant, RuleChainType type, String name,
                                           RuleChainExportData original, SyncRuleChainRequest request,
                                           User operator, SyncRuleChainResult result) {
        TenantId tenantId = tenant.getId();
        TenantSyncDetail detail = new TenantSyncDetail();
        detail.setTenantId(tenantId.toString());
        detail.setTenantName(tenant.getName());

        Collection<RuleChain> existingChains = ruleChainService.findTenantRuleChainsByTypeAndName(tenantId, type, name);
        RuleChain existing = (existingChains != null && !existingChains.isEmpty()) ? existingChains.iterator().next() : null;
        boolean hasExisting = existing != null;

        // SKIP 策略且无同名链 → 跳过
        if (!hasExisting && request.getMissingTargetStrategy() == MissingTargetStrategy.SKIP) {
            detail.setStatus(TenantSyncStatus.SKIPPED);
            detail.setMessage("目标租户无同名规则链，按 SKIP 跳过");
            result.setSkipped(result.getSkipped() + 1);
            return detail;
        }

        // dryRun：仅预览，不写入
        if (request.isDryRun()) {
            detail.setStatus(hasExisting ? TenantSyncStatus.UPDATED : TenantSyncStatus.CREATED);
            detail.setMessage("dryRun 预览，未实际写入");
            return detail;
        }

        try {
            RuleChainExportData data = JacksonUtil.clone(original);
            if (hasExisting) {
                // 保留目标租户原有 root 标记
                data.getEntity().setRoot(existing.isRoot());
            }

            User targetUser = new User();
            targetUser.setId(operator.getId());
            targetUser.setTenantId(tenantId);
            targetUser.setEmail(operator.getEmail());

            EntitiesImportCtx ctx = new EntitiesImportCtx(
                    UUID.randomUUID(), targetUser, null,
                    EntityImportSettings.builder().findExistingByName(true).build());
            ctx.setFinalImportAttempt(true);

            EntityImportResult<RuleChain> importResult = ruleChainImportService.importEntity(ctx, data);
            if (importResult.getSavedEntity() != null && importResult.getSavedEntity().getId() != null) {
                detail.setTargetRuleChainId(importResult.getSavedEntity().getId().toString());
            }
            if (importResult.isCreated()) {
                detail.setStatus(TenantSyncStatus.CREATED);
                result.setCreated(result.getCreated() + 1);
            } else {
                detail.setStatus(TenantSyncStatus.UPDATED);
                result.setUpdated(result.getUpdated() + 1);
            }
        } catch (Exception e) {
            log.warn("[{}] 同步规则链到租户失败: chainName={}", tenantId, name, e);
            detail.setStatus(TenantSyncStatus.FAILED);
            detail.setMessage(e.getMessage());
            result.setFailed(result.getFailed() + 1);
        }
        return detail;
    }

    private RuleChain resolveSource(TenantId srcTenant, SyncRuleChainRequest request) throws ThingsboardException {
        if (request.getSourceRuleChainId() != null) {
            RuleChain chain = ruleChainService.findRuleChainById(srcTenant, new RuleChainId(UUID.fromString(request.getSourceRuleChainId())));
            if (chain == null) {
                throw new ThingsboardException("源规则链不存在: " + request.getSourceRuleChainId(), ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
            return chain;
        }
        if (request.getSourceRuleChainName() != null) {
            RuleChainType type = request.getRuleChainType() != null
                    ? RuleChainType.valueOf(request.getRuleChainType()) : RuleChainType.CORE;
            return ruleChainService.findTenantRuleChainsByTypeAndName(srcTenant, type, request.getSourceRuleChainName())
                    .stream().findFirst()
                    .orElseThrow(() -> new ThingsboardException(
                            "源规则链不存在: name=" + request.getSourceRuleChainName() + ", type=" + type,
                            ThingsboardErrorCode.ITEM_NOT_FOUND));
        }
        throw new ThingsboardException("必须提供 sourceRuleChainId 或 sourceRuleChainName", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }

    private TenantId parseTenantId(String id) throws ThingsboardException {
        if (id == null || id.isBlank()) {
            throw new ThingsboardException("sourceTenantId 必填", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        try {
            return TenantId.fromUUID(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("sourceTenantId 非法: " + id, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }
}
