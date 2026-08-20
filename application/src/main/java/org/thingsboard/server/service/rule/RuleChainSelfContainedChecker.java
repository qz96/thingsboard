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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 规则链"自包含"检测：判断源规则链是否可以安全地复制到其它租户而不产生悬空引用。
 *
 * <p>判定规则（命中任一即判为非自包含，返回违规清单）：
 * <ol>
 *   <li>连接了其它规则链（{@code ruleChainConnections} 非空）——复制到目标租户会指向源租户的另一条链。</li>
 *   <li>节点配置引用了非链内实体的 UUID 字段——如 deviceId/assetId/customerId/... 以及任何以 Id/ID 结尾、
 *       值形如 UUID 且不在链内节点 id 集合中的字段（覆盖脚本节点硬编码 id 等场景）。</li>
 * </ol>
 *
 * <p>与导入框架 ID 重映射的区别：{@code RuleChainImportService} 仅重映射"链内节点 id"（externalId↔internalId），
 * 不处理跨实体的业务引用，因此此处单独扫描已知/通用的实体 id 字段，保守地拦截会错乱的链。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleChainSelfContainedChecker {

    /** 字段名以 Id/ID 结尾（用于通用兜底扫描）。 */
    private static final Pattern ENTITY_ID_FIELD_SUFFIX = Pattern.compile(".*[iI]d$");

    /** 已知实体 id 字段（小写），用于精确拦截业务引用。 */
    private static final Set<String> KNOWN_ENTITY_ID_FIELDS = Set.of(
            "deviceid", "assetid", "customerid", "dashboardid", "userid", "entityid",
            "assetprofileid", "deviceprofileid", "originatorid", "entityviewid",
            "queueid", "resourceid", "tenantid", "customerprofileid"
    );

    /**
     * 检测源规则链是否自包含。
     *
     * @return 违规说明清单；为空表示自包含。
     */
    public List<String> check(RuleChain srcChain, RuleChainMetaData meta) {
        List<String> violations = new ArrayList<>();

        // 1) 跨规则链连接
        if (meta.getRuleChainConnections() != null) {
            for (RuleChainConnectionInfo conn : meta.getRuleChainConnections()) {
                violations.add("规则链连接指向其他规则链: targetRuleChainId=" + conn.getTargetRuleChainId());
            }
        }

        // 2) 收集链内节点 id（用于排除链内引用）
        Set<UUID> nodeIds = new HashSet<>();
        if (meta.getNodes() != null) {
            for (RuleNode node : meta.getNodes()) {
                if (node.getId() != null) {
                    nodeIds.add(node.getId().getId());
                }
            }
        }

        // 3) 递归扫描每个节点配置
        if (meta.getNodes() != null) {
            for (RuleNode node : meta.getNodes()) {
                JsonNode configuration = node.getConfiguration();
                if (configuration == null) {
                    continue;
                }
                String nodeLabel = node.getName() != null ? node.getName()
                        : (node.getId() != null ? node.getId().toString() : "<unknown>");
                scan(configuration, nodeLabel, nodeIds, violations);
            }
        }
        return violations;
    }

    private void scan(JsonNode node, String nodeLabel, Set<UUID> nodeIds, List<String> violations) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    String text = value.asText();
                    if (isUuid(text) && !nodeIds.contains(UUID.fromString(text))
                            && (ENTITY_ID_FIELD_SUFFIX.matcher(field).matches()
                            || KNOWN_ENTITY_ID_FIELDS.contains(field.toLowerCase()))) {
                        violations.add("节点[" + nodeLabel + "] 字段[" + field + "] 引用实体 id: " + text);
                    }
                } else {
                    scan(value, nodeLabel, nodeIds, violations);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                scan(item, nodeLabel, nodeIds, violations);
            }
        }
    }

    private static boolean isUuid(String s) {
        if (s == null || s.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
