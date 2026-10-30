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

import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot } from '@angular/router';
import {
  CellActionDescriptor,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeTranslations, entityTypeResources } from '@shared/models/entity-type.models';
import { Direction } from '@app/shared/models/page/sort-order';
import { AndroidAppService } from '@core/http/android-app.service';
import { AndroidAppVersionComponent } from '@home/pages/androidapp/android-app-version.component';
import {
  AndroidAppVersion,
  AndroidAppVersionStatus,
  androidAppVersionStatusTranslations
} from '@shared/models/android-app.models';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
  AndroidAppVersionDialogComponent,
  AndroidAppVersionDialogData
} from '@home/pages/androidapp/android-app-version-dialog.component';

@Injectable()
export class AndroidAppVersionTableConfigResolver {

  private readonly config: EntityTableConfig<AndroidAppVersion> = new EntityTableConfig<AndroidAppVersion>();

  constructor(private translate: TranslateService,
              private datePipe: DatePipe,
              private androidAppService: AndroidAppService,
              private dialog: MatDialog
              ) {
    this.config.selectionEnabled = false;
    this.config.entityType = EntityType.ANDROID_APP_VERSION;
    this.config.addAsTextButton = true;
    this.config.entitiesDeleteEnabled = false;
    this.config.rowPointer = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.ANDROID_APP_VERSION);
    this.config.entityResources = entityTypeResources.get(EntityType.ANDROID_APP_VERSION);
    this.config.entityComponent = AndroidAppVersionComponent;
    this.config.addDialogStyle = {width: '720px', maxHeight: '100vh'};
    this.config.defaultSortOrder = {property: 'versionCode', direction: Direction.DESC};
    this.config.tableTitle = this.translate.instant('androidapp.versions');

    this.config.columns.push(
      new DateEntityTableColumn<AndroidAppVersion>('createdTime', 'common.created-time', this.datePipe, '170px'),
      new EntityTableColumn<AndroidAppVersion>('versionCode', 'androidapp.version-code', '10%',
        (entity) => entity.versionCode?.toString() ?? '', () => ({}), false),
      new EntityTableColumn<AndroidAppVersion>('versionName', 'androidapp.version-name', '15%'),
      new EntityTableColumn<AndroidAppVersion>('apkSize', 'androidapp.apk-size', '15%',
        (entity) => this.formatSize(entity.apkSize), () => ({}), false),
      new EntityTableColumn<AndroidAppVersion>('forceUpdate', 'androidapp.force-update', '15%',
        (entity) => entity.forceUpdate
          ? this.translate.instant('androidapp.force-update-yes')
          : this.translate.instant('androidapp.force-update-no'), () => ({}), false),
      new EntityTableColumn<AndroidAppVersion>('status', 'androidapp.status', '15%',
        (entity) => `<span style="display: flex;">${this.versionStatus(entity.status)}</span>`,
        (entity) => this.versionStatusStyle(entity.status)
      ),
    );

    this.config.deleteEntity = id => this.androidAppService.deleteAndroidAppVersion(id.id);
    this.config.deleteEntityTitle = (version) => this.translate.instant('androidapp.delete-version-title', {versionName: version.versionName});
    this.config.deleteEntityContent = () => this.translate.instant('androidapp.delete-version-text');
  }

  resolve(route: ActivatedRouteSnapshot): EntityTableConfig<AndroidAppVersion> {
    const appId = route.parent?.params?.appId ?? route.params?.appId;
    this.config.entitiesFetchFunction = pageLink => this.androidAppService.getAndroidAppVersions(appId, pageLink);
    this.config.addEntity = () => this.addVersion(appId);
    this.config.cellActionDescriptors = this.configureCellActions(appId);
    return this.config;
  }

  private addVersion(appId: string): Observable<AndroidAppVersion> {
    return this.dialog.open<AndroidAppVersionDialogComponent, AndroidAppVersionDialogData,
      AndroidAppVersion>(AndroidAppVersionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        appId
      }
    }).afterClosed()
      .pipe(tap((res) => {
        if (res) {
          this.config.updateData();
        }
      }));
  }

  private configureCellActions(appId: string): Array<CellActionDescriptor<AndroidAppVersion>> {
    return [
      {
        name: this.translate.instant('androidapp.publish'),
        icon: 'publish',
        isEnabled: (entity) => entity.status !== AndroidAppVersionStatus.PUBLISHED,
        onAction: ($event, entity) => this.publish($event, appId, entity)
      },
      {
        name: this.translate.instant('androidapp.retract'),
        icon: 'undo',
        isEnabled: (entity) => entity.status === AndroidAppVersionStatus.PUBLISHED,
        onAction: ($event, entity) => this.retract($event, appId, entity)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.config.getTable().deleteEntity($event, entity)
      }
    ];
  }

  private publish($event: Event, appId: string, entity: AndroidAppVersion) {
    if ($event) {
      $event.stopPropagation();
    }
    this.androidAppService.publishAndroidAppVersion(entity.id.id).subscribe(() => {
      this.config.updateData();
    });
  }

  private retract($event: Event, appId: string, entity: AndroidAppVersion) {
    if ($event) {
      $event.stopPropagation();
    }
    this.androidAppService.retractAndroidAppVersion(entity.id.id).subscribe(() => {
      this.config.updateData();
    });
  }

  private formatSize(size?: number): string {
    if (!size) {
      return '';
    }
    const mb = size / (1024 * 1024);
    if (mb >= 1) {
      return `${mb.toFixed(2)} MB`;
    }
    return `${(size / 1024).toFixed(1)} KB`;
  }

  private versionStatus(status: AndroidAppVersionStatus): string {
    const translateKey = androidAppVersionStatusTranslations.get(status);
    let backgroundColor = 'rgba(0, 148, 255, 0.06)';
    if (status === AndroidAppVersionStatus.PUBLISHED) {
      backgroundColor = 'rgba(25, 128, 56, 0.06)';
    } else if (status === AndroidAppVersionStatus.RETRACTED) {
      backgroundColor = 'rgba(209, 39, 48, 0.06)';
    }
    return `<div style="border-radius: 14px; height: 28px; line-height: 20px; padding: 4px 10px;
                        width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private versionStatusStyle(status: AndroidAppVersionStatus): object {
    const styleObj = {
      fontSize: '14px',
      color: '#0094FF'
    };
    if (status === AndroidAppVersionStatus.PUBLISHED) {
      styleObj.color = '#198038';
    } else if (status === AndroidAppVersionStatus.RETRACTED) {
      styleObj.color = '#D12730';
    }
    return styleObj;
  }

}
