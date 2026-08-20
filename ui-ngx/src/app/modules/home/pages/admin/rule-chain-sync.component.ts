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
  MissingTargetStrategy,
  RuleChain,
  RuleChainType,
  SyncRuleChainRequest,
  SyncRuleChainResult,
  TenantSyncDetail,
  TenantSyncStatus
} from '@shared/models/rule-chain.models';
import { AdminService } from '@core/http/admin.service';
import { RuleChainService } from '@core/http/rule-chain.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-rule-chain-sync',
  templateUrl: './rule-chain-sync.component.html',
  styleUrls: ['./rule-chain-sync.component.scss']
})
export class RuleChainSyncComponent implements OnInit, OnDestroy {

  readonly EntityType = EntityType;
  readonly RuleChainType = RuleChainType;
  readonly MissingTargetStrategy = MissingTargetStrategy;
  readonly TenantSyncStatus = TenantSyncStatus;

  syncFormGroup: UntypedFormGroup;

  ruleChains: Array<RuleChain> = [];
  loadingRuleChains = false;
  syncing = false;
  result: SyncRuleChainResult = null;

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
      dryRun: formValue.dryRun
    };
    this.syncing = true;
    this.result = null;
    this.adminService.syncRuleChain(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res: SyncRuleChainResult) => {
          this.syncing = false;
          this.result = res;
          if (res.sourceSelfContained === false) {
            this.snackBar.open(res.message || 'Source rule chain is not self-contained.', 'OK', { duration: 8000 });
            return;
          }
          const summary = `Created: ${res.created || 0}, Updated: ${res.updated || 0}, ` +
            `Skipped: ${res.skipped || 0}, Failed: ${res.failed || 0}`;
          this.snackBar.open(summary, 'OK', { duration: 5000 });
        },
        error: (err) => {
          this.syncing = false;
          this.snackBar.open(err?.message || 'Rule chain sync failed.', 'OK', { duration: 8000 });
        }
      });
  }

  statusLabel(status: TenantSyncStatus): string {
    switch (status) {
      case TenantSyncStatus.CREATED: return 'Created';
      case TenantSyncStatus.UPDATED: return 'Updated';
      case TenantSyncStatus.SKIPPED: return 'Skipped';
      case TenantSyncStatus.FAILED: return 'Failed';
      default: return status;
    }
  }

  trackByTenantId(index: number, detail: TenantSyncDetail): string {
    return detail.tenantId;
  }
}
