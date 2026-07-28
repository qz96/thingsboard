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

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityId } from '@shared/models/id/entity-id';
import { AttributeService } from '@core/http/attribute.service';
import { EntityService } from '@core/http/entity.service';
import { DataKeyType, TimeseriesData } from '@shared/models/telemetry/telemetry.models';
import { MatTableDataSource } from '@angular/material/table';
import { FormBuilder } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { AggregationType } from '@shared/models/time/time.models';

export interface HistoricalTelemetryRow {
  ts: number;
  tsStr: string;
  key: string;
  value: any;
}

@Component({
  selector: 'tb-device-historical-telemetry-table',
  templateUrl: './device-historical-telemetry-table.component.html',
  styleUrls: ['./device-historical-telemetry-table.component.scss'],
  providers: [DatePipe]
})
export class DeviceHistoricalTelemetryTableComponent extends PageComponent implements OnInit, OnDestroy {

  @Input()
  entityId: EntityId;

  @Input()
  entityName: string;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.entityId && !this.initialized) {
        this.initialized = true;
        this.loadTelemetryKeys();
      }
    }
  }

  private activeValue = false;
  private initialized = false;
  private destroy$ = new Subject<void>();

  telemetryKeys: string[] = [];
  selectedKeys: string[] = [];
  displayedColumns = ['ts', 'key', 'value'];
  dataSource = new MatTableDataSource<HistoricalTelemetryRow>([]);
  loading = false;
  hasData = false;

  filterForm = this.fb.group({
    startDate: [null as Date | null],
    endDate: [null as Date | null],
    searchText: ['']
  });

  constructor(
    protected store: Store<AppState>,
    private attributeService: AttributeService,
    private entityService: EntityService,
    private fb: FormBuilder,
    private translate: TranslateService,
    private datePipe: DatePipe
  ) {
    super(store);
  }

  ngOnInit() {
    this.filterForm.get('searchText').valueChanges.pipe(
      debounceTime(300),
      takeUntil(this.destroy$)
    ).subscribe((text) => {
      const filterValue = (text || '').trim().toLowerCase();
      this.dataSource.filter = filterValue;
    });

    this.dataSource.filterPredicate = (data: HistoricalTelemetryRow, filter: string) => {
      return data.key.toLowerCase().includes(filter) ||
        String(data.value).toLowerCase().includes(filter) ||
        data.tsStr.includes(filter);
    };
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  loadTelemetryKeys() {
    if (!this.entityId) {
      return;
    }
    this.entityService.getEntityKeys(this.entityId, '', DataKeyType.timeseries).subscribe(
      (keys) => {
        this.telemetryKeys = keys || [];
        if (this.telemetryKeys.length > 0) {
          this.selectedKeys = [...this.telemetryKeys];
          this.setDefaultDateRange();
          this.queryHistoricalData();
        }
      }
    );
  }

  setDefaultDateRange() {
    const end = new Date();
    const start = new Date();
    start.setHours(start.getHours() - 24);
    this.filterForm.patchValue({
      startDate: start,
      endDate: end
    }, { emitEvent: false });
  }

  queryHistoricalData() {
    if (!this.entityId || this.selectedKeys.length === 0) {
      return;
    }

    const startDate = this.filterForm.get('startDate').value as Date;
    const endDate = this.filterForm.get('endDate').value as Date;

    if (!startDate || !endDate) {
      return;
    }

    this.loading = true;
    const startTs = startDate.getTime();
    const endTs = endDate.getTime();

    this.attributeService.getEntityTimeseries(
      this.entityId,
      this.selectedKeys,
      startTs,
      endTs,
      1000,
      AggregationType.NONE,
      undefined,
      undefined,
      false
    ).subscribe({
      next: (data: TimeseriesData) => {
        this.processTimeseriesData(data);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  private processTimeseriesData(data: TimeseriesData) {
    const rows: HistoricalTelemetryRow[] = [];
    for (const key of Object.keys(data)) {
      const values = data[key];
      if (values && values.length) {
        for (const tsValue of values) {
          rows.push({
            ts: tsValue.ts,
            tsStr: this.datePipe.transform(tsValue.ts, 'yyyy-MM-dd HH:mm:ss.SSS') || '',
            key,
            value: tsValue.value
          });
        }
      }
    }
    rows.sort((a, b) => b.ts - a.ts);
    this.dataSource.data = rows;
    this.hasData = rows.length > 0;
  }

  onKeySelectionChange(keys: string[]) {
    this.selectedKeys = keys;
  }

  refresh() {
    this.queryHistoricalData();
  }

  get selectedKeysText(): string {
    if (this.selectedKeys.length === 0) {
      return this.translate.instant('attribute.no-keys-selected');
    }
    if (this.selectedKeys.length === this.telemetryKeys.length) {
      return this.translate.instant('attribute.all-keys-selected');
    }
    return this.selectedKeys.join(', ');
  }
}
