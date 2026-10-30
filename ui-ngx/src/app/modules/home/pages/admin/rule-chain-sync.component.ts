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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { EntityType } from '@shared/models/entity-type.models';
import {
  ExistingTargetStrategy,
  MissingTargetStrategy,
  RuleChain,
  RuleChainSyncTask,
  RuleChainType,
  SyncRuleChainRequest,
  SyncRuleChainResult,
  TenantSyncDetail,
  TenantSyncStatus
} from '@shared/models/rule-chain.models';
import { AdminService } from '@core/http/admin.service';
import { RuleChainService } from '@core/http/rule-chain.service';
import { Subject, interval } from 'rxjs';
import { switchMap, takeUntil, takeWhile } from 'rxjs/operators';

@Component({
  selector: 'tb-rule-chain-sync',
  templateUrl: './rule-chain-sync.component.html',
  styleUrls: ['./rule-chain-sync.component.scss']
})
export class RuleChainSyncComponent implements OnInit, OnDestroy {

  readonly EntityType = EntityType;
  readonly RuleChainType = RuleChainType;
  readonly MissingTargetStrategy = MissingTargetStrategy;
  readonly ExistingTargetStrategy = ExistingTargetStrategy;
  readonly TenantSyncStatus = TenantSyncStatus;

  syncFormGroup: UntypedFormGroup;

  ruleChains: Array<RuleChain> = [];
  loadingRuleChains = false;
  syncing = false;
  result: SyncRuleChainResult = null;

  /** 异步批量任务进度（几十个租户下发时展示） */
  taskId: string = null;
  taskTotal = 0;
  taskProcessed = 0;
  taskCurrentTenant: string = null;
  taskStatus: string = null;

  displayedColumns = ['tenantName', 'status', 'targetRuleChainId', 'message'];

  private destroy$ = new Subject<void>();

  constructor(private fb: UntypedFormBuilder,
              private adminService: AdminService,
              private ruleChainService: RuleChainService,
              private snackBar: MatSnackBar) {
    this.syncFormGroup = this.fb.group({
      sourceTenant: [null, Validators.required],
      ruleChainType: [RuleChainType.CORE, Validators.required],
      sourceRuleChain: [null, Validators.required],
      missingTargetStrategy: [MissingTargetStrategy.CREATE, Validators.required],
      existingTargetStrategy: [ExistingTargetStrategy.OVERWRITE_UNCHANGED, Validators.required],
      dryRun: [false]
    });
  }

  ngOnInit(): void {
    this.syncFormGroup.get('sourceTenant').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.syncFormGroup.get('sourceRuleChain').setValue(null, { emitEvent: false });
        this.loadRuleChains();
      });
    this.syncFormGroup.get('ruleChainType').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadRuleChains());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRuleChains(): void {
    const tenant = this.syncFormGroup.get('sourceTenant').value;
    if (!tenant || !tenant.id) {
      this.ruleChains = [];
      return;
    }
    const tenantId = tenant.id;
    const type = this.syncFormGroup.get('ruleChainType').value;
    this.loadingRuleChains = true;
    this.ruleChainService.getTenantRuleChains(tenantId, new PageLink(100), type)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (pageData: PageData<RuleChain>) => {
          this.ruleChains = pageData.data || [];
          this.loadingRuleChains = false;
        },
        error: () => {
          this.ruleChains = [];
          this.loadingRuleChains = false;
        }
      });
  }

  sync(): void {
    if (this.syncFormGroup.invalid) {
      this.syncFormGroup.markAllAsTouched();
      return;
    }
    const formValue = this.syncFormGroup.value;
    const request: SyncRuleChainRequest = {
      sourceTenantId: formValue.sourceTenant.id,
      sourceRuleChainId: formValue.sourceRuleChain.id.id,
      ruleChainType: formValue.ruleChainType,
      missingTargetStrategy: formValue.missingTargetStrategy,
      existingTargetStrategy: formValue.existingTargetStrategy,
      dryRun: formValue.dryRun
    };
    this.syncing = true;
    this.result = null;
    this.resetTaskProgress();

    if (request.dryRun) {
      // 预览不写库，直接同步返回
      this.adminService.syncRuleChain(request)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (res: SyncRuleChainResult) => {
            this.syncing = false;
            this.result = res;
            this.snackBar.open(this.buildSummary(res) + ' (dry run)', 'OK', { duration: 5000 });
          },
          error: (err) => {
            this.syncing = false;
            this.snackBar.open(err?.error?.message || err?.message || 'Rule chain sync failed.', 'OK', { duration: 8000 });
          }
        });
      return;
    }

    // 正式执行：异步任务 + 轮询进度，避免几十个租户把 HTTP 请求拖到超时
    this.adminService.syncRuleChainAsync(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (task: RuleChainSyncTask) => this.pollTask(task),
        error: (err) => {
          this.syncing = false;
          this.snackBar.open(err?.error?.message || err?.message || 'Rule chain sync failed.', 'OK', { duration: 8000 });
        }
      });
  }

  private pollTask(task: RuleChainSyncTask): void {
    this.taskId = task.id;
    this.taskStatus = task.status;
    this.taskTotal = task.total;
    this.taskProcessed = task.processed;
    this.taskCurrentTenant = task.currentTenant;

    interval(1000)
      .pipe(
        switchMap(() => this.adminService.getRuleChainSyncTask(task.id)),
        takeWhile((t: RuleChainSyncTask) => t.status === 'RUNNING', true),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (t: RuleChainSyncTask) => {
          this.taskStatus = t.status;
          this.taskTotal = t.total;
          this.taskProcessed = t.processed;
          this.taskCurrentTenant = t.currentTenant;
          if (t.status !== 'RUNNING') {
            this.syncing = false;
            this.result = t.result || null;
            if (t.status === 'FAILED') {
              this.snackBar.open(t.message || 'Rule chain sync failed.', 'OK', { duration: 8000 });
            } else {
              this.snackBar.open(this.buildSummary(t.result), 'OK', { duration: 6000 });
            }
          }
        },
        error: () => {
          this.syncing = false;
        }
      });
  }

  private buildSummary(res: SyncRuleChainResult): string {
    if (!res) {
      return '';
    }
    return `Created: ${res.created || 0}, Updated: ${res.updated || 0}, ` +
      `Unchanged: ${res.unchanged || 0}, Drifted: ${res.drifted || 0}, ` +
      `Skipped: ${res.skipped || 0}, Failed: ${res.failed || 0}`;
  }

  private resetTaskProgress(): void {
    this.taskId = null;
    this.taskTotal = 0;
    this.taskProcessed = 0;
    this.taskCurrentTenant = null;
    this.taskStatus = null;
  }

  get progressPercent(): number {
    return this.taskTotal > 0 ? Math.round(this.taskProcessed * 100 / this.taskTotal) : 0;
  }

  statusLabel(status: TenantSyncStatus): string {
    switch (status) {
      case TenantSyncStatus.CREATED: return 'Created';
      case TenantSyncStatus.UPDATED: return 'Updated';
      case TenantSyncStatus.SKIPPED: return 'Skipped';
      case TenantSyncStatus.FAILED: return 'Failed';
      case TenantSyncStatus.UNCHANGED: return 'Unchanged';
      case TenantSyncStatus.DRIFTED: return 'Drifted';
      default: return status;
    }
  }

  trackByTenantId(index: number, detail: TenantSyncDetail): string {
    return detail.tenantId;
  }
}
