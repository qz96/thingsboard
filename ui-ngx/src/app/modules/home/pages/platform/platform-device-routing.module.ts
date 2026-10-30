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
import { PlatformDevicesComponent } from '@home/pages/platform/platform-devices.component';

export const platformDeviceRoutes: Routes = [
  {
    path: 'platform-devices',
    component: PlatformDevicesComponent,
    data: {
      breadcrumb: {
        menuId: MenuId.platform_devices
      },
      auth: [Authority.SYS_ADMIN],
      title: 'platform.platform-devices'
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(platformDeviceRoutes)],
  exports: [RouterModule]
})
export class PlatformDeviceRoutingModule { }
