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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { MenuId } from '@core/services/menu.models';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { AndroidAppTableConfigResolver } from '@home/pages/androidapp/android-app-table-config.resolver';
import { AndroidAppVersionTableConfigResolver } from '@home/pages/androidapp/android-app-version-table-config.resolver';
import { AndroidAppDisplayTableConfigResolver } from '@home/pages/androidapp/android-app-display-table-config.resolver';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';

export const androidAppRoutes: Routes = [
  {
    path: 'android-apps',
    data: {
      breadcrumb: {
        menuId: MenuId.android_apps
      }
    },
    children: [
      {
        path: '',
        component: EntitiesTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'androidapp.apps',
        },
        resolve: {
          entitiesTableConfig: AndroidAppTableConfigResolver
        }
      },
      {
        path: ':appId/versions',
        component: EntitiesTableComponent,
        data: {
          breadcrumb: {
            label: 'androidapp.versions',
            icon: 'history'
          } as BreadCrumbConfig<EntitiesTableComponent>,
          auth: [Authority.SYS_ADMIN],
          title: 'androidapp.versions',
        },
        resolve: {
          entitiesTableConfig: AndroidAppVersionTableConfigResolver
        }
      },
      {
        path: 'displays',
        component: EntitiesTableComponent,
        data: {
          breadcrumb: {
            label: 'androidapp.displays',
            icon: 'qr_code'
          } as BreadCrumbConfig<EntitiesTableComponent>,
          auth: [Authority.SYS_ADMIN],
          title: 'androidapp.displays',
        },
        resolve: {
          entitiesTableConfig: AndroidAppDisplayTableConfigResolver
        }
      }
    ]
  }
];

@NgModule({
  providers: [
    AndroidAppTableConfigResolver,
    AndroidAppVersionTableConfigResolver,
    AndroidAppDisplayTableConfigResolver
  ],
  imports: [RouterModule.forChild(androidAppRoutes)],
  exports: [RouterModule]
})
export class AndroidAppRoutingModule { }
