/*
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.rule.ExistingTargetStrategy;
import org.thingsboard.server.common.data.rule.MissingTargetStrategy;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainSyncTask;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 跨租户规则链同步编排服务（平台默认模板下发）。
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
 *
 * <p>平台模板能力（本类新增）：
 * <ul>
 *   <li><b>版本戳</b>：导入成功后把源链 checksum 写入目标链 additionalInfo（{@value #STAMP_CHECKSUM}），
 *       用于判断"该租户是否已是当前模板版本"。</li>
 *   <li><b>策略机</b>：{@link ExistingTargetStrategy#OVERWRITE_UNCHANGED}（默认）只覆盖
 *       上次同步后未被本地修改的租户；被改过的标记 DRIFTED 跳过，避免静默覆盖租户定制。</li>
 *   <li><b>异步任务</b>：几十个租户串行导入无法在一个 HTTP 请求内完成，{@link #startAsync}
 *       提交后台单线程执行，前端轮询 {@link #getTask} 获取进度。</li>
 *   <li><b>新租户自动应用</b>：{@link #applyTemplateToNewTenant} 供租户创建后调用（开关控制）。</li>
 * </ul>
 */
@Service
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class TbRuleChainSyncService {

    /** additionalInfo 中记录「上次同步写入的源链 checksum」 */
    static final String STAMP_CHECKSUM = "platformTemplateChecksum";
    /** additionalInfo 中记录「上次同步时间」 */
    static final String STAMP_APPLIED_AT = "platformTemplateAppliedAt";
    /** additionalInfo 中记录「源租户」 */
    static final String STAMP_SOURCE_TENANT = "platformTemplateSourceTenant";
    /** additionalInfo 中记录「操作人」 */
    static final String STAMP_APPLIED_BY = "platformTemplateAppliedBy";

    private static final int MAX_KEPT_TASKS = 20;
    private static final int TENANT_PAGE_SIZE = 50;

    private final RuleChainService ruleChainService;
    private final RuleChainImportService ruleChainImportService;
    private final TenantService tenantService;
    private final RuleChainSelfContainedChecker selfContainedChecker;

    /** 平台模板总开关 */
    @Value("${platform.rulechain-template.enabled:false}")
    private boolean templateEnabled;

    /** 平台模板源租户 ID（平台管理员维护基准规则链的租户） */
    @Value("${platform.rulechain-template.source-tenant-id:}")
    private String templateSourceTenantId;

    /** 平台模板规则链名称（与 source-tenant-id 配合定位源链） */
    @Value("${platform.rulechain-template.source-rule-chain-name:}")
    private String templateRuleChainName;

    /** 新租户创建后是否自动应用平台模板 */
    @Value("${platform.rulechain-template.auto-apply-on-tenant-create:true}")
    private boolean autoApplyOnTenantCreate;

    private final Map<String, RuleChainSyncTask> tasks = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rule-chain-sync-worker");
        t.setDaemon(true);
        return t;
    });

    // ==================== 同步入口 ====================

    /**
     * 同步执行（保留原语义）：几十个租户时会阻塞较长，建议改用 {@link #startAsync}。
     */
    public SyncRuleChainResult syncRuleChain(SyncRuleChainRequest request, User operator) throws ThingsboardException {
        TenantId srcTenant = parseTenantId(request.getSourceTenantId());
        RuleChainSyncSource source = resolveSourceWithValidation(srcTenant, request);
        SyncRuleChainResult result = new SyncRuleChainResult();
        doSyncWithSource(request, operator, source, null, null, result);
        return result;
    }

    /**
     * 异步执行：立即返回任务对象，后台单线程按租户串行处理，进度可轮询。
     *
     * <p>源链解析、自包含校验在提交前同步完成——参数错误和"源链不自包含"能立刻返回，
     * 不必让前端等一个注定失败的任务。
     */
    public RuleChainSyncTask startAsync(SyncRuleChainRequest request, User operator) throws ThingsboardException {
        TenantId srcTenant = parseTenantId(request.getSourceTenantId());
        RuleChainSyncSource source = resolveSourceWithValidation(srcTenant, request);

        RuleChainSyncTask task = new RuleChainSyncTask();
        task.setId(UUID.randomUUID().toString());
        task.setStatus("RUNNING");
        task.setStartedAt(System.currentTimeMillis());
        task.setResult(new SyncRuleChainResult());
        tasks.put(task.getId(), task);
        trimTasks();

        executor.submit(() -> {
            try {
                doSyncWithSource(request, operator, source,
                        task::setProcessed, task::setCurrentTenant, task.getResult());
                task.setStatus("DONE");
            } catch (Exception e) {
                log.error("[{}] 异步规则链同步失败: chain={}", task.getId(), source.chain().getName(), e);
                task.setStatus("FAILED");
                task.setMessage(e.getMessage());
            } finally {
                task.setCurrentTenant(null);
                task.setFinishedAt(System.currentTimeMillis());
            }
        });
        return task;
    }

    public RuleChainSyncTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    private void trimTasks() {
        if (tasks.size() <= MAX_KEPT_TASKS) {
            return;
        }
        tasks.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getStartedAt()))
                .limit(tasks.size() - MAX_KEPT_TASKS)
                .map(Map.Entry::getKey)
                .forEach(tasks::remove);
    }

    // ==================== 新租户自动应用平台模板 ====================

    /**
     * 新租户创建后被调用：把平台默认规则链应用过去。
     *
     * <p>为什么用 OVERWRITE_ALWAYS 而不是 OVERWRITE_UNCHANGED：
     * 新租户刚由 TB 自动生成默认 root 链（没有版本戳），若按"仅覆盖未改动"会被判为 DRIFTED 而跳过，
     * 导致模板永远铺不上去。此处是"全新租户"的确定场景，强覆盖是安全的。
     *
     * <p>异常一律吞掉并记日志：模板下发失败不能影响租户创建本身。
     */
    public void applyTemplateToNewTenant(TenantId tenantId) {
        if (!templateEnabled || !autoApplyOnTenantCreate) {
            return;
        }
        if (isBlank(templateSourceTenantId) || isBlank(templateRuleChainName)) {
            log.warn("平台模板未配置（source-tenant-id / source-rule-chain-name 为空），跳过新租户 {} 的模板应用", tenantId);
            return;
        }
        try {
            SyncRuleChainRequest request = new SyncRuleChainRequest();
            request.setSourceTenantId(templateSourceTenantId);
            request.setSourceRuleChainName(templateRuleChainName);
            request.setMissingTargetStrategy(MissingTargetStrategy.CREATE);
            request.setExistingTargetStrategy(ExistingTargetStrategy.OVERWRITE_ALWAYS);
            request.setTenantIds(List.of(tenantId.toString()));
            request.setDryRun(false);

            User operator = new User();
            operator.setEmail("system@platform-template");
            operator.setTenantId(TenantId.SYS_TENANT_ID);

            RuleChainSyncTask task = startAsync(request, operator);
            log.info("[{}] 新租户 {} 已提交平台模板应用任务: chain={}", task.getId(), tenantId, templateRuleChainName);
        } catch (Exception e) {
            log.error("新租户 {} 应用平台模板失败（不影响租户创建）: {}", tenantId, e.getMessage(), e);
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 逐租户执行同步。
     */
    private void doSyncWithSource(SyncRuleChainRequest request,
                                  User operator,
                                  RuleChainSyncSource source,
                                  IntConsumer onProgress,
                                  Consumer<String> onTenant,
                                  SyncRuleChainResult result) throws ThingsboardException {
        RuleChain srcChain = source.chain();
        RuleChainMetaData srcMeta = source.meta();
        String sourceChecksum = computeChecksum(srcChain, srcMeta);

        result.setSourceSelfContained(true);
        result.setSourceChecksum(sourceChecksum);
        result.setStrategy(request.getExistingTargetStrategy());

        RuleChainType type = srcChain.getType();
        String name = srcChain.getName();

        RuleChainExportData original = new RuleChainExportData();
        original.setEntity(srcChain);
        original.setMetaData(srcMeta);
        // entityType 是 @JsonTbEntity/@JsonTypeInfo 多态类型判定依据，缺失会导致 JacksonUtil.clone 反序列化失败
        original.setEntityType(EntityType.RULE_CHAIN);

        List<Tenant> tenants = resolveTargetTenants(request, source.sourceTenant());
        result.setTotalTenants(tenants.size());
        int processed = 0;
        for (Tenant tenant : tenants) {
            if (onTenant != null) {
                onTenant.accept(tenant.getName());
            }
            result.getTenants().add(processTenant(tenant, type, name, original, sourceChecksum, request, operator, result));
            processed++;
            if (onProgress != null) {
                onProgress.accept(processed);
            }
        }
    }

    /**
     * 目标租户列表：显式指定优先，否则遍历全部（排除源租户）。
     */
    private List<Tenant> resolveTargetTenants(SyncRuleChainRequest request, TenantId srcTenant) throws ThingsboardException {
        List<Tenant> tenants = new ArrayList<>();
        if (request.getTenantIds() != null && !request.getTenantIds().isEmpty()) {
            for (String raw : request.getTenantIds()) {
                TenantId tenantId = parseTenantId(raw);
                if (tenantId.equals(srcTenant)) {
                    continue;
                }
                Tenant tenant = tenantService.findTenantById(tenantId);
                if (tenant == null) {
                    log.warn("指定的目标租户不存在，已忽略: {}", raw);
                    continue;
                }
                tenants.add(tenant);
            }
            return tenants;
        }
        PageLink pageLink = new PageLink(TENANT_PAGE_SIZE);
        PageData<Tenant> page;
        do {
            page = tenantService.findTenants(pageLink);
            for (Tenant tenant : page.getData()) {
                if (!tenant.getId().equals(srcTenant)) {
                    tenants.add(tenant);
                }
            }
            pageLink = pageLink.nextPageLink();
        } while (page.hasNext());
        return tenants;
    }

    private TenantSyncDetail processTenant(Tenant tenant, RuleChainType type, String name,
                                           RuleChainExportData original, String sourceChecksum,
                                           SyncRuleChainRequest request, User operator,
                                           SyncRuleChainResult result) {
        TenantId tenantId = tenant.getId();
        TenantSyncDetail detail = new TenantSyncDetail();
        detail.setTenantId(tenantId.toString());
        detail.setTenantName(tenant.getName());

        Collection<RuleChain> existingChains = ruleChainService.findTenantRuleChainsByTypeAndName(tenantId, type, name);
        RuleChain existing = (existingChains != null && !existingChains.isEmpty()) ? existingChains.iterator().next() : null;
        boolean hasExisting = existing != null;

        ExistingTargetStrategy strategy = request.getExistingTargetStrategy() == null
                ? ExistingTargetStrategy.OVERWRITE_UNCHANGED
                : request.getExistingTargetStrategy();

        // 1) 目标缺失：按 missingTargetStrategy 决定新增或跳过
        if (!hasExisting) {
            if (request.getMissingTargetStrategy() == MissingTargetStrategy.SKIP) {
                detail.setStatus(TenantSyncStatus.SKIPPED);
                detail.setMessage("目标租户无同名规则链，按 SKIP 跳过");
                result.setSkipped(result.getSkipped() + 1);
                return detail;
            }
            return importInto(detail, tenantId, original, sourceChecksum, operator, request, result,
                    TenantSyncStatus.CREATED, "租户无同名规则链，新建");
        }

        // 2) 目标已存在：按策略判定
        String stampedChecksum = readStamp(existing);
        if (strategy == ExistingTargetStrategy.SKIP_EXISTING) {
            detail.setStatus(TenantSyncStatus.SKIPPED);
            detail.setMessage("目标已存在同名规则链，按 SKIP_EXISTING 跳过");
            detail.setTargetRuleChainId(existing.getId().getId().toString());
            result.setSkipped(result.getSkipped() + 1);
            return detail;
        }
        if (strategy == ExistingTargetStrategy.OVERWRITE_UNCHANGED) {
            if (sourceChecksum.equals(stampedChecksum)) {
                detail.setStatus(TenantSyncStatus.UNCHANGED);
                detail.setMessage("目标已是当前模板版本（checksum 一致），跳过");
                detail.setTargetRuleChainId(existing.getId().getId().toString());
                result.setUnchanged(result.getUnchanged() + 1);
                return detail;
            }
            String targetCurrentChecksum = computeChecksum(existing,
                    ruleChainService.loadRuleChainMetaData(tenantId, existing.getId()));
            boolean untouched = stampedChecksum != null && stampedChecksum.equals(targetCurrentChecksum);
            if (!untouched) {
                detail.setStatus(TenantSyncStatus.DRIFTED);
                detail.setMessage(stampedChecksum == null
                        ? "目标规则链无同步版本戳（可能为本地创建/修改），按 OVERWRITE_UNCHANGED 跳过；确认后可改用 OVERWRITE_ALWAYS 强推"
                        : "目标规则链已被本地修改（checksum 与上次同步不一致），跳过；确认后可改用 OVERWRITE_ALWAYS 强推");
                detail.setTargetRuleChainId(existing.getId().getId().toString());
                result.setDrifted(result.getDrifted() + 1);
                return detail;
            }
        }

        return importInto(detail, tenantId, original, sourceChecksum, operator, request, result,
                TenantSyncStatus.UPDATED, "覆盖更新");
    }

    /**
     * 实际导入（dryRun 时只返回将要发生的状态，不写库）。
     */
    private TenantSyncDetail importInto(TenantSyncDetail detail, TenantId tenantId,
                                        RuleChainExportData original, String sourceChecksum,
                                        User operator, SyncRuleChainRequest request,
                                        SyncRuleChainResult result,
                                        TenantSyncStatus plannedStatus, String plannedMessage) {
        if (request.isDryRun()) {
            detail.setStatus(plannedStatus);
            detail.setMessage("dryRun 预览：" + plannedMessage + "（未实际写入）");
            return detail;
        }

        try {
            RuleChainExportData data = JacksonUtil.clone(original);
            if (plannedStatus == TenantSyncStatus.UPDATED) {
                // 保留目标租户原有 root 标记
                Collection<RuleChain> chains = ruleChainService.findTenantRuleChainsByTypeAndName(
                        tenantId, original.getEntity().getType(), original.getEntity().getName());
                if (chains != null && !chains.isEmpty()) {
                    data.getEntity().setRoot(chains.iterator().next().isRoot());
                }
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
            RuleChain saved = importResult.getSavedEntity();
            if (saved != null && saved.getId() != null) {
                detail.setTargetRuleChainId(saved.getId().getId().toString());
                writeStamp(saved, sourceChecksum, operator, tenantId);
            }
            boolean created = importResult.isCreated();
            detail.setStatus(created ? TenantSyncStatus.CREATED : TenantSyncStatus.UPDATED);
            detail.setMessage(plannedMessage);
            if (created) {
                result.setCreated(result.getCreated() + 1);
            } else {
                result.setUpdated(result.getUpdated() + 1);
            }
        } catch (Exception e) {
            log.warn("[{}] 同步规则链到租户失败: chainName={}", tenantId, original.getEntity().getName(), e);
            detail.setStatus(TenantSyncStatus.FAILED);
            detail.setMessage(e.getMessage());
            result.setFailed(result.getFailed() + 1);
        }
        return detail;
    }

    /**
     * 把版本戳写入目标链 additionalInfo（随实体一起持久化）。
     */
    private void writeStamp(RuleChain chain, String sourceChecksum, User operator, TenantId tenantId) {
        try {
            ObjectNode info = chain.getAdditionalInfo() instanceof ObjectNode
                    ? (ObjectNode) chain.getAdditionalInfo()
                    : JsonNodeFactory.instance.objectNode();
            info.put(STAMP_CHECKSUM, sourceChecksum);
            info.put(STAMP_APPLIED_AT, System.currentTimeMillis());
            info.put(STAMP_SOURCE_TENANT, String.valueOf(templateSourceTenantId));
            info.put(STAMP_APPLIED_BY, operator.getEmail() == null ? "unknown" : operator.getEmail());
            chain.setAdditionalInfo(info);
            ruleChainService.saveRuleChain(chain);
        } catch (Exception e) {
            // 版本戳写失败不影响数据面，只影响下次"是否已是最新版本"的判断
            log.warn("[{}] 写入规则链同步版本戳失败: chain={}", tenantId, chain.getName(), e);
        }
    }

    private String readStamp(RuleChain chain) {
        JsonNode info = chain.getAdditionalInfo();
        if (info == null || !info.has(STAMP_CHECKSUM)) {
            return null;
        }
        return info.get(STAMP_CHECKSUM).asText(null);
    }

    /**
     * 规则链内容 checksum：节点与连线按名称/类型稳定排序后哈希。
     *
     * <p>不含节点 id（每次导入都会变），用于判断"目标链相对上次同步是否被改动"。
     */
    static String computeChecksum(RuleChain chain, RuleChainMetaData meta) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(chain.getName()).append('|').append(chain.getType()).append('|').append(chain.isRoot()).append('\n');

        List<RuleNode> rawNodes = meta.getNodes() == null ? List.of() : meta.getNodes();
        List<RuleNode> nodes = new ArrayList<>(rawNodes);
        nodes.sort(Comparator.comparing((RuleNode n) -> String.valueOf(n.getName()))
                .thenComparing(n -> String.valueOf(n.getType())));
        for (RuleNode node : nodes) {
            sb.append("N:").append(node.getName()).append('|').append(node.getType())
                    .append('|').append(node.isDebugMode()).append('|').append(node.isSingletonMode())
                    .append('|').append(node.getQueueName() == null ? "" : node.getQueueName())
                    .append('|').append(node.getConfiguration() == null ? "" : node.getConfiguration())
                    .append('\n');
        }

        Map<Integer, String> indexToName = new LinkedHashMap<>();
        for (int i = 0; i < rawNodes.size(); i++) {
            indexToName.put(i, String.valueOf(rawNodes.get(i).getName()));
        }
        List<String> connectionKeys = new ArrayList<>();
        List<RuleChainConnectionInfo> connections = meta.getRuleChainConnections() == null
                ? List.of() : meta.getRuleChainConnections();
        for (RuleChainConnectionInfo c : connections) {
            connectionKeys.add("C:" + indexToName.get(c.getFromIndex()) + ">" + c.getTargetRuleChainId()
                    + '|' + c.getType());
        }
        connectionKeys.sort(Comparator.naturalOrder());
        connectionKeys.forEach(k -> sb.append(k).append('\n'));

        return sha256(sb.toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // 极不可能发生；退化为 hashCode 保证流程不中断
            return Integer.toHexString(input.hashCode());
        }
    }

    private RuleChainSyncSource resolveSourceWithValidation(TenantId srcTenant, SyncRuleChainRequest request)
            throws ThingsboardException {
        RuleChain srcChain = resolveSource(srcTenant, request);
        RuleChainMetaData srcMeta = ruleChainService.loadRuleChainMetaData(srcTenant, srcChain.getId());
        List<String> violations = selfContainedChecker.check(srcChain, srcMeta);
        if (!violations.isEmpty()) {
            throw new ThingsboardException("源规则链非自包含，同步终止: " + String.join("; ", violations),
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        return new RuleChainSyncSource(srcTenant, srcChain, srcMeta);
    }

    private RuleChain resolveSource(TenantId srcTenant, SyncRuleChainRequest request) throws ThingsboardException {
        if (request.getSourceRuleChainId() != null) {
            RuleChain chain = ruleChainService.findRuleChainById(srcTenant,
                    new RuleChainId(UUID.fromString(request.getSourceRuleChainId())));
            if (chain == null) {
                throw new ThingsboardException("源规则链不存在: " + request.getSourceRuleChainId(),
                        ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
            return chain;
        }
        RuleChainType type = request.getRuleChainType() == null
                ? RuleChainType.CORE
                : RuleChainType.valueOf(request.getRuleChainType());
        Collection<RuleChain> chains = ruleChainService.findTenantRuleChainsByTypeAndName(
                srcTenant, type, request.getSourceRuleChainName());
        if (chains == null || chains.isEmpty()) {
            throw new ThingsboardException("源规则链不存在: name=" + request.getSourceRuleChainName(),
                    ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return chains.iterator().next();
    }

    private TenantId parseTenantId(String id) throws ThingsboardException {
        if (isBlank(id)) {
            throw new ThingsboardException("sourceTenantId 不能为空", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        try {
            return new TenantId(UUID.fromString(id.trim()));
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException("租户 ID 格式非法: " + id, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 源链解析结果（实体 + 元数据 + 源租户） */
    private record RuleChainSyncSource(TenantId sourceTenant, RuleChain chain, RuleChainMetaData meta) {
    }
}
