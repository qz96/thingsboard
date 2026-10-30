///
/// Copyright © 2016-2025 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { BaseData, ExportableEntity } from '@shared/models/base-data';
import { TenantId } from '@shared/models/id/tenant-id';
import { RuleChainId } from '@shared/models/id/rule-chain-id';
import { RuleNodeId } from '@shared/models/id/rule-node-id';
import { RuleNode, RuleNodeComponentDescriptor, RuleNodeType } from '@shared/models/rule-node.models';
import { ComponentClusteringMode, ComponentType } from '@shared/models/component-descriptor.models';
import { HasTenantId, HasVersion } from '@shared/models/entity.models';

export interface RuleChain extends BaseData<RuleChainId>, HasTenantId, HasVersion, ExportableEntity<RuleChainId> {
  tenantId: TenantId;
  name: string;
  firstRuleNodeId: RuleNodeId;
  root: boolean;
  debugMode: boolean;
  type: string;
  configuration?: any;
  additionalInfo?: any;
  isDefault?: boolean;
}

export interface RuleChainMetaData extends HasVersion {
  ruleChainId: RuleChainId;
  firstNodeIndex?: number;
  nodes: Array<RuleNode>;
  connections: Array<NodeConnectionInfo>;
}

export interface RuleChainImport {
  ruleChain: RuleChain;
  metadata: RuleChainMetaData;
}

export interface NodeConnectionInfo {
  fromIndex: number;
  toIndex: number;
  type: string;
}

export const ruleNodeTypeComponentTypes: ComponentType[] =
  [
    ComponentType.FILTER,
    ComponentType.ENRICHMENT,
    ComponentType.TRANSFORMATION,
    ComponentType.ACTION,
    ComponentType.EXTERNAL,
    ComponentType.FLOW
  ];

export const unknownNodeComponent: RuleNodeComponentDescriptor = {
  type: RuleNodeType.UNKNOWN,
  name: 'unknown',
  clusteringMode: ComponentClusteringMode.ENABLED,
  configurationVersion: 0,
  clazz: 'tb.internal.Unknown',
  configurationDescriptor: {
    nodeDefinition: {
      description: '',
      details: '',
      inEnabled: true,
      outEnabled: true,
      relationTypes: [],
      customRelations: false,
      defaultConfiguration: {}
    }
  }
};

export const inputNodeComponent: RuleNodeComponentDescriptor = {
  type: RuleNodeType.INPUT,
  configurationVersion: 0,
  clusteringMode: ComponentClusteringMode.ENABLED,
  name: 'Input',
  clazz: 'tb.internal.Input'
};

export enum RuleChainType {
  CORE = 'CORE',
  EDGE = 'EDGE'
}

export enum TenantSyncStatus {
  CREATED = 'CREATED',
  UPDATED = 'UPDATED',
  SKIPPED = 'SKIPPED',
  FAILED = 'FAILED',
  /** 目标已是当前模板版本，无需重复导入 */
  UNCHANGED = 'UNCHANGED',
  /** 目标被本地修改，按 OVERWRITE_UNCHANGED 跳过，需人工确认或强推 */
  DRIFTED = 'DRIFTED'
}

export enum ExistingTargetStrategy {
  /** 只覆盖上次同步后未被本地修改的租户（默认） */
  OVERWRITE_UNCHANGED = 'OVERWRITE_UNCHANGED',
  /** 无条件覆盖 */
  OVERWRITE_ALWAYS = 'OVERWRITE_ALWAYS',
  /** 已存在即跳过 */
  SKIP_EXISTING = 'SKIP_EXISTING'
}

export enum MissingTargetStrategy {
  CREATE = 'CREATE',
  SKIP = 'SKIP'
}

export interface SyncRuleChainRequest {
  sourceTenantId: string;
  sourceRuleChainId?: string;
  sourceRuleChainName?: string;
  ruleChainType?: string;
  missingTargetStrategy?: MissingTargetStrategy;
  existingTargetStrategy?: ExistingTargetStrategy;
  /** 指定目标租户；为空表示全部租户 */
  tenantIds?: Array<string>;
  dryRun?: boolean;
}

export interface TenantSyncDetail {
  tenantId: string;
  tenantName?: string;
  status: TenantSyncStatus;
  targetRuleChainId?: string;
  message?: string;
}

export interface SyncRuleChainResult {
  sourceSelfContained?: boolean;
  message?: string;
  totalTenants?: number;
  created?: number;
  updated?: number;
  skipped?: number;
  failed?: number;
  /** 源链内容 checksum（本次模板版本标识） */
  sourceChecksum?: string;
  strategy?: ExistingTargetStrategy;
  /** 已是当前模板版本、跳过的租户数 */
  unchanged?: number;
  /** 被本地修改、跳过等待人工确认的租户数 */
  drifted?: number;
  tenants?: Array<TenantSyncDetail>;
}

/** 异步批量同步任务（几十个租户下发时轮询用） */
export interface RuleChainSyncTask {
  id: string;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  total: number;
  processed: number;
  currentTenant?: string;
  startedAt?: number;
  finishedAt?: number;
  message?: string;
  result?: SyncRuleChainResult;
}

