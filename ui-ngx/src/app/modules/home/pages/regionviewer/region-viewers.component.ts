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
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { selectAuthUser } from '@core/auth/auth.selectors';
import { DialogService } from '@core/services/dialog.service';
import { UserService } from '@core/http/user.service';
import { PageLink } from '@shared/models/page/page-link';
import { User } from '@shared/models/user.model';
import { Authority } from '@shared/models/authority.enum';
import { NULL_UUID } from '@shared/models/id/has-uuid';
import { AddUserDialogComponent, AddUserDialogData } from '@home/pages/user/add-user-dialog.component';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, take, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-region-viewers',
  templateUrl: './region-viewers.component.html',
  styleUrls: ['./region-viewers.component.scss']
})
export class RegionViewersComponent implements OnInit, OnDestroy {

  viewers: User[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;
  textSearch = '';
  loading = false;

  displayedColumns = ['email', 'firstName', 'lastName', 'createdTime', 'actions'];

  private tenantId: string;
  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  constructor(private store: Store<AppState>,
              private userService: UserService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private translate: TranslateService,
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
      this.loadViewers();
    });
    this.store.pipe(select(selectAuthUser), take(1)).subscribe((user) => {
      this.tenantId = user?.tenantId;
      this.loadViewers();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadViewers(): void {
    if (!this.tenantId) {
      return;
    }
    this.loading = true;
    const pageLink = new PageLink(this.pageSize, this.pageIndex, this.textSearch || null);
    this.userService.getTenantViewers(this.tenantId, pageLink).subscribe({
      next: (pageData) => {
        this.viewers = pageData.data;
        this.totalElements = pageData.totalElements;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onSearch(value: string): void {
    this.searchSubject.next(value || '');
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadViewers();
  }

  addViewer(): void {
    this.dialog.open<AddUserDialogComponent, AddUserDialogData, User>(AddUserDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        tenantId: this.tenantId,
        customerId: NULL_UUID,
        authority: Authority.TENANT_VIEWER
      }
    }).afterClosed().subscribe((user) => {
      if (user) {
        this.loadViewers();
      }
    });
  }

  deleteViewer(viewer: User): void {
    this.dialogService.confirm(
      this.translate.instant('region-viewer.delete-title', {email: viewer.email}),
      this.translate.instant('region-viewer.delete-text'),
      this.translate.instant('action.cancel'),
      this.translate.instant('action.delete')
    ).subscribe((confirmed: boolean) => {
      if (confirmed) {
        this.userService.deleteUser(viewer.id.id).subscribe({
          next: () => this.loadViewers(),
          error: (error) => {
            this.snackBar.open(error?.error?.message || 'Failed to delete the user', null, {duration: 4000});
          }
        });
      }
    });
  }

  trackById(_index: number, viewer: User): string {
    return viewer.id.id;
  }

}
