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
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '@core/auth/auth.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, takeUntil } from 'rxjs/operators';
import { DialogService } from '@core/services/dialog.service';
import { PlatformDeviceService } from '@core/http/platform-device.service';
import { PageLink } from '@shared/models/page/page-link';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import {
  RegionDeviceInfo,
  RegionDeviceStatus,
  RegionOverview,
  RegionSummary,
  regionDeviceStatuses
} from '@shared/models/platform.models';
import {
  PlatformDeviceDialogComponent,
  PlatformDeviceDialogData,
  PlatformDeviceDialogResult
} from '@home/pages/platform/platform-device-dialog.component';

@Component({
  selector: 'tb-platform-devices',
  templateUrl: './platform-devices.component.html',
  styleUrls: ['./platform-devices.component.scss']
})
export class PlatformDevicesComponent implements OnInit, OnDestroy {

  regions: RegionOverview[] = [];
  filteredRegions: RegionOverview[] = [];
  selectedRegion: RegionOverview = null;
  summary: RegionSummary = null;

  devices: RegionDeviceInfo[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  textSearch = '';
  status: RegionDeviceStatus = 'ALL';
  sortProperty = 'name';
  sortOrder: Direction = Direction.ASC;
  regionSearch = '';

  loadingRegions = false;
  loadingDevices = false;
  switchingRegion = false;

  displayedColumns = ['name', 'type', 'label', 'status', 'alarmCount', 'actions'];
  statuses = regionDeviceStatuses;

  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  constructor(private platformDeviceService: PlatformDeviceService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private translate: TranslateService,
              private authService: AuthService,
              private snackBar: MatSnackBar) {
  }

  ngOnInit(): void {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe((value: string) => {
      this.textSearch = value;
      this.pageIndex = 0;
      this.loadDevices();
    });
    this.loadRegions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRegions(keepSelection = false): void {
    const selectedId = this.selectedRegion?.tenantId;
    this.loadingRegions = true;
    this.platformDeviceService.getRegions().subscribe({
      next: (regions) => {
        this.regions = regions;
        this.applyRegionFilter();
        this.loadingRegions = false;
        let target: RegionOverview = null;
        if (keepSelection && selectedId) {
          target = this.regions.find(r => r.tenantId === selectedId) || null;
        }
        if (!target) {
          target = this.regions.length ? this.regions[0] : null;
        }
        if (target) {
          this.selectRegion(target, true);
        } else {
          this.selectedRegion = null;
          this.summary = null;
          this.devices = [];
          this.totalElements = 0;
        }
      },
      error: () => {
        this.loadingRegions = false;
      }
    });
  }

  applyRegionFilter(): void {
    const search = (this.regionSearch || '').trim().toLowerCase();
    this.filteredRegions = search
      ? this.regions.filter(r => (r.title || '').toLowerCase().includes(search))
      : this.regions;
  }

  onRegionSearch(value: string): void {
    this.regionSearch = value;
    this.applyRegionFilter();
  }

  selectRegion(region: RegionOverview, silent = false): void {
    if (!silent && this.selectedRegion?.tenantId === region.tenantId) {
      return;
    }
    this.selectedRegion = region;
    this.pageIndex = 0;
    this.loadSummary();
    this.loadDevices();
  }

  loadSummary(): void {
    if (!this.selectedRegion) {
      return;
    }
    this.platformDeviceService.getRegionSummary(this.selectedRegion.tenantId).subscribe({
      next: (summary) => {
        this.summary = summary;
      }
    });
  }

  loadDevices(): void {
    if (!this.selectedRegion) {
      return;
    }
    this.loadingDevices = true;
    const sortOrder: SortOrder = { property: this.sortProperty, direction: this.sortOrder };
    const pageLink = new PageLink(this.pageSize, this.pageIndex, this.textSearch || null, sortOrder);
    this.platformDeviceService.getRegionDevices(this.selectedRegion.tenantId, pageLink, this.status).subscribe({
      next: (pageData) => {
        this.devices = pageData.data;
        this.totalElements = pageData.totalElements;
        this.loadingDevices = false;
      },
      error: () => {
        this.loadingDevices = false;
      }
    });
  }

  onSearch(value: string): void {
    this.searchSubject.next(value || '');
  }

  onStatusChange(): void {
    this.pageIndex = 0;
    this.loadDevices();
  }

  onSortChange(event: Sort): void {
    if (!event.direction) {
      this.sortProperty = 'name';
      this.sortOrder = Direction.ASC;
    } else {
      this.sortProperty = event.active;
      this.sortOrder = event.direction === 'desc' ? Direction.DESC : Direction.ASC;
    }
    this.pageIndex = 0;
    this.loadDevices();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadDevices();
  }

  switchToRegion(): void {
    if (!this.selectedRegion || this.switchingRegion) {
      return;
    }
    this.switchingRegion = true;
    this.platformDeviceService.loginAsRegionAdmin(this.selectedRegion.tenantId).subscribe({
      next: (result) => {
        this.authService.setUserFromJwtToken(result.token, result.refreshToken, true).subscribe({
          next: () => {
            window.location.href = '/home';
          },
          error: () => {
            this.switchingRegion = false;
            this.notifyError('platform.switch-to-region-failed');
          }
        });
      },
      error: (error) => {
        this.switchingRegion = false;
        this.notifyError(error?.error?.message || 'platform.switch-to-region-failed');
      }
    });
  }

  private notifyError(messageKey: string): void {
    const isKey = messageKey.indexOf('platform.') === 0;
    this.snackBar.open(isKey ? this.translate.instant(messageKey) : messageKey, null,
      {duration: 4000, panelClass: ['tb-error-snackbar']});
  }

  editDevice(device: RegionDeviceInfo): void {
    if (!this.selectedRegion) {
      return;
    }
    this.openDeviceDialog({
      tenantId: this.selectedRegion.tenantId,
      regionTitle: this.selectedRegion.title,
      device
    });
  }

  deleteDevice(device: RegionDeviceInfo): void {
    if (!this.selectedRegion) {
      return;
    }
    this.dialogService.confirm(
      this.translate.instant('platform.delete-device-title', {name: device.name}),
      this.translate.instant('platform.delete-device-text'),
      this.translate.instant('action.cancel'),
      this.translate.instant('action.delete')
    ).subscribe((confirmed: boolean) => {
      if (confirmed) {
        this.platformDeviceService.deleteRegionDevice(this.selectedRegion.tenantId, device.id)
          .subscribe(() => this.refresh());
      }
    });
  }

  refresh(): void {
    this.loadSummary();
    this.loadDevices();
    this.loadRegions(true);
  }

  trackByRegion(_index: number, region: RegionOverview): string {
    return region.tenantId;
  }

  trackByDevice(_index: number, device: RegionDeviceInfo): string {
    return device.id;
  }

  private openDeviceDialog(data: PlatformDeviceDialogData): void {
    this.dialog.open<PlatformDeviceDialogComponent, PlatformDeviceDialogData, PlatformDeviceDialogResult>(
      PlatformDeviceDialogComponent,
      {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data
      }
    ).afterClosed().subscribe((result) => {
      if (result) {
        this.refresh();
      }
    });
  }

}
