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
import { ActivatedRoute, ActivatedRouteSnapshot, Router } from '@angular/router';
import {
  CellActionDescriptor,
  CellActionDescriptorType,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeTranslations, entityTypeResources } from '@shared/models/entity-type.models';
import { Direction } from '@app/shared/models/page/sort-order';
import { AndroidAppService } from '@core/http/android-app.service';
import { AndroidAppComponent } from '@home/pages/androidapp/android-app.component';
import { AndroidApp, AndroidAppStatus, androidAppStatusTranslations } from '@shared/models/android-app.models';
import { TruncatePipe } from '@shared/pipe/truncate.pipe';
import { MatDialog } from '@angular/material/dialog';
import { AndroidAppDialogComponent } from '@home/pages/androidapp/android-app-dialog.component';

@Injectable()
export class AndroidAppTableConfigResolver {

  private readonly config: EntityTableConfig<AndroidApp> = new EntityTableConfig<AndroidApp>();

  constructor(private translate: TranslateService,
              private datePipe: DatePipe,
              private androidAppService: AndroidAppService,
              private truncatePipe: TruncatePipe,
              private dialog: MatDialog,
              private router: Router,
              private route: ActivatedRoute
              ) {
    this.config.selectionEnabled = false;
    this.config.entityType = EntityType.ANDROID_APP;
    this.config.addAsTextButton = true;
    this.config.entitiesDeleteEnabled = false;
    this.config.rowPointer = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.ANDROID_APP);
    this.config.entityResources = entityTypeResources.get(EntityType.ANDROID_APP);
    this.config.entityComponent = AndroidAppComponent;
    this.config.addDialogStyle = {width: '680px', maxHeight: '100vh'};
    this.config.defaultSortOrder = {property: 'createdTime', direction: Direction.DESC};

    this.config.columns.push(
      new DateEntityTableColumn<AndroidApp>('createdTime', 'common.created-time', this.datePipe, '170px'),
      new EntityTableColumn<AndroidApp>('title', 'androidapp.app-title', '20%'),
      new EntityTableColumn<AndroidApp>('pkgName', 'androidapp.app-package', '25%', (entity) => entity.pkgName ?? '', () => ({}),
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('androidapp.copy-app-package'),
          icon: 'content_copy',
          style: {
            padding: '4px',
            'font-size': '16px',
            color: 'rgba(0,0,0,.54)'
          },
          isEnabled: (entity) => !!entity.pkgName,
          onAction: (_$event, entity) => entity.pkgName,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<AndroidApp>('description', 'androidapp.app-description', '20%',
        (entity) => this.truncatePipe.transform(entity.description, true, 30, '…'), () => ({}), false),
      new EntityTableColumn<AndroidApp>('status', 'androidapp.status', '15%',
        (entity) => `<span style="display: flex;">${this.appStatus(entity.status)}</span>`,
        (entity) => this.appStatusStyle(entity.status)
      ),
    );

    this.config.entitiesFetchFunction = pageLink => this.androidAppService.getAndroidApps(pageLink);
    this.config.loadEntity = id => this.androidAppService.getAndroidAppById(id.id);
    this.config.saveEntity = androidApp => this.androidAppService.saveAndroidApp(androidApp);
    this.config.deleteEntity = id => this.androidAppService.deleteAndroidApp(id.id);
    this.config.deleteEntityTitle = (androidApp) => this.translate.instant('androidapp.delete-app-title', {appTitle: androidApp.title});
    this.config.deleteEntityContent = () => this.translate.instant('androidapp.delete-app-text');

    this.config.cellActionDescriptors = this.configureCellActions();
  }

  resolve(_route: ActivatedRouteSnapshot): EntityTableConfig<AndroidApp> {
    return this.config;
  }

  private configureCellActions(): Array<CellActionDescriptor<AndroidApp>> {
    return [
      {
        name: this.translate.instant('androidapp.manage-versions'),
        icon: 'history',
        isEnabled: () => true,
        onAction: ($event, entity) => this.manageVersions($event, entity)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.config.getTable().deleteEntity($event, entity)
      }
    ];
  }

  private manageVersions($event: Event, entity: AndroidApp) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigate(['..', entity.id.id, 'versions'], {relativeTo: this.route});
  }

  private appStatus(status: AndroidAppStatus): string {
    const translateKey = androidAppStatusTranslations.get(status);
    let backgroundColor = 'rgba(25, 128, 56, 0.06)';
    if (status === AndroidAppStatus.DISABLED) {
      backgroundColor = 'rgba(209, 39, 48, 0.06)';
    }
    return `<div style="border-radius: 14px; height: 28px; line-height: 20px; padding: 4px 10px;
                        width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private appStatusStyle(status: AndroidAppStatus): object {
    const styleObj = {
      fontSize: '14px',
      color: '#198038'
    };
    if (status === AndroidAppStatus.DISABLED) {
      styleObj.color = '#D12730';
    }
    return styleObj;
  }

}
