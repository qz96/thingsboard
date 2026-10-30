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
import { AndroidAppDisplayComponent } from '@home/pages/androidapp/android-app-display.component';
import {
  AndroidAppDisplay,
  AndroidAppDisplayPlatform,
  androidAppDisplayPlatformTranslations
} from '@shared/models/android-app.models';
import { TruncatePipe } from '@shared/pipe/truncate.pipe';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AndroidAppDisplayDialogComponent } from '@home/pages/androidapp/android-app-display-dialog.component';

@Injectable()
export class AndroidAppDisplayTableConfigResolver {

  private readonly config: EntityTableConfig<AndroidAppDisplay> = new EntityTableConfig<AndroidAppDisplay>();

  constructor(private translate: TranslateService,
              private datePipe: DatePipe,
              private androidAppService: AndroidAppService,
              private truncatePipe: TruncatePipe,
              private dialog: MatDialog
              ) {
    this.config.selectionEnabled = false;
    this.config.entityType = EntityType.ANDROID_APP_DISPLAY;
    this.config.addAsTextButton = true;
    this.config.entitiesDeleteEnabled = false;
    this.config.rowPointer = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.ANDROID_APP_DISPLAY);
    this.config.entityResources = entityTypeResources.get(EntityType.ANDROID_APP_DISPLAY);
    this.config.entityComponent = AndroidAppDisplayComponent;
    this.config.addDialogStyle = {width: '680px', maxHeight: '100vh'};
    this.config.defaultSortOrder = {property: 'sortOrder', direction: Direction.ASC};
    this.config.tableTitle = this.translate.instant('androidapp.displays');
    this.config.deleteEntityTitle = (display) => this.translate.instant('androidapp.delete-display-title', {appName: display.appName});
    this.config.deleteEntityContent = () => this.translate.instant('androidapp.delete-display-text');
  }

  resolve(_route: ActivatedRouteSnapshot): EntityTableConfig<AndroidAppDisplay> {
    this.config.entitiesFetchFunction = pageLink => this.androidAppService.getAndroidAppDisplays(pageLink);
    this.config.loadEntity = id => this.androidAppService.getAndroidAppDisplayById(id.id);
    this.config.saveEntity = display => this.androidAppService.saveAndroidAppDisplay(display);
    this.config.addEntity = () => this.addDisplay();
    this.config.deleteEntity = id => this.androidAppService.deleteAndroidAppDisplay(id.id);
    this.config.columns = [
      new DateEntityTableColumn<AndroidAppDisplay>('createdTime', 'common.created-time', this.datePipe, '170px'),
      new EntityTableColumn<AndroidAppDisplay>('platformType', 'androidapp.platform-type', '10%',
        (entity) => this.translate.instant(androidAppDisplayPlatformTranslations.get(entity.platformType)), () => ({}), false),
      new EntityTableColumn<AndroidAppDisplay>('appName', 'androidapp.app-name', '15%'),
      new EntityTableColumn<AndroidAppDisplay>('downloadUrl', 'androidapp.download-url', '30%',
        (entity) => this.truncatePipe.transform(entity.downloadUrl, true, 40, '…'), () => ({}), true, () => ({}),
        () => undefined, false,
        {
          name: this.translate.instant('androidapp.copy-download-url'),
          icon: 'content_copy',
          style: {
            padding: '4px',
            'font-size': '16px',
            color: 'rgba(0,0,0,.54)'
          },
          isEnabled: (entity) => !!entity.downloadUrl,
          onAction: (_$event, entity) => entity.downloadUrl,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<AndroidAppDisplay>('sortOrder', 'androidapp.sort-order', '10%',
        (entity) => entity.sortOrder?.toString() ?? '', () => ({}), false),
      new EntityTableColumn<AndroidAppDisplay>('showOnHome', 'androidapp.show-on-home', '10%',
        (entity) => `<span style="display: flex;">${this.showOnHomeBadge(entity.showOnHome)}</span>`,
        (entity) => this.showOnHomeStyle(entity.showOnHome)
      ),
    ];
    this.config.cellActionDescriptors = this.configureCellActions();
    return this.config;
  }

  private addDisplay(): Observable<AndroidAppDisplay> {
    return this.dialog.open<AndroidAppDisplayDialogComponent, any,
      AndroidAppDisplay>(AndroidAppDisplayDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed()
      .pipe(tap((res) => {
        if (res) {
          this.config.updateData();
        }
      }));
  }

  private configureCellActions(): Array<CellActionDescriptor<AndroidAppDisplay>> {
    return [
      {
        name: this.translate.instant('androidapp.show-on-home'),
        icon: 'visibility',
        isEnabled: (entity) => !entity.showOnHome,
        onAction: ($event, entity) => this.setShowOnHome($event, entity, true)
      },
      {
        name: this.translate.instant('androidapp.hide-on-home'),
        icon: 'visibility_off',
        isEnabled: (entity) => entity.showOnHome,
        onAction: ($event, entity) => this.setShowOnHome($event, entity, false)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.config.getTable().deleteEntity($event, entity)
      }
    ];
  }

  private setShowOnHome($event: Event, entity: AndroidAppDisplay, showOnHome: boolean) {
    if ($event) {
      $event.stopPropagation();
    }
    this.androidAppService.setAndroidAppDisplayShowOnHome(entity.id.id, showOnHome).subscribe(() => {
      this.config.updateData();
    });
  }

  private showOnHomeBadge(showOnHome: boolean): string {
    const translateKey = showOnHome ? 'androidapp.show-on-home-shown' : 'androidapp.show-on-home-hidden';
    const backgroundColor = showOnHome ? 'rgba(25, 128, 56, 0.06)' : 'rgba(0, 0, 0, 0.06)';
    return `<div style="border-radius: 14px; height: 28px; line-height: 20px; padding: 4px 10px;
                        width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private showOnHomeStyle(showOnHome: boolean): object {
    return {
      fontSize: '14px',
      color: showOnHome ? '#198038' : 'rgba(0,0,0,0.54)'
    };
  }

}
