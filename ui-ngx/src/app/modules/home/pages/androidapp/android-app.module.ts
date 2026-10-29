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
import { AndroidAppComponent } from '@home/pages/androidapp/android-app.component';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { AndroidAppRoutingModule } from '@home/pages/androidapp/android-app-routing.module';
import { AndroidAppDialogComponent } from '@home/pages/androidapp/android-app-dialog.component';
import { AndroidAppVersionComponent } from '@home/pages/androidapp/android-app-version.component';
import { AndroidAppVersionDialogComponent } from '@home/pages/androidapp/android-app-version-dialog.component';
import { AndroidAppDisplayComponent } from '@home/pages/androidapp/android-app-display.component';
import { AndroidAppDisplayDialogComponent } from '@home/pages/androidapp/android-app-display-dialog.component';

@NgModule({
  declarations: [
    AndroidAppComponent,
    AndroidAppDialogComponent,
    AndroidAppVersionComponent,
    AndroidAppVersionDialogComponent,
    AndroidAppDisplayComponent,
    AndroidAppDisplayDialogComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    AndroidAppRoutingModule,
  ],
  exports: [
    AndroidAppDialogComponent,
    AndroidAppVersionDialogComponent,
    AndroidAppDisplayDialogComponent,
  ]
})
export class AndroidAppModule { }
