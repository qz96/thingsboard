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

import { ChangeDetectorRef, Component, Inject, Optional } from '@angular/core';
import { EntityComponent } from '@home/components/entity/entity.component';
import { AppState } from '@core/core.state';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { EntityType } from '@shared/models/entity-type.models';
import {
  AndroidAppVersion,
  AndroidAppVersionStatus,
  androidAppVersionStatusTranslations
} from '@shared/models/android-app.models';

@Component({
  selector: 'tb-android-app-version',
  templateUrl: './android-app-version.component.html',
  styleUrls: ['./android-app-version.component.scss']
})
export class AndroidAppVersionComponent extends EntityComponent<AndroidAppVersion> {

  entityType = EntityType;

  androidAppVersionStatusTranslations = androidAppVersionStatusTranslations;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Optional() @Inject('entity') protected entityValue: AndroidAppVersion,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<AndroidAppVersion>,
              protected cd: ChangeDetectorRef,
              public fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: AndroidAppVersion): FormGroup {
    const form = this.fb.group({
      versionName: [entity?.versionName ?? '', [Validators.required, Validators.maxLength(255)]],
      versionCode: [entity?.versionCode ?? null, [Validators.required, Validators.min(1)]],
      releaseNotes: [entity?.releaseNotes ?? '', [Validators.maxLength(100000)]],
      apkUrl: [entity?.apkUrl ?? '', [Validators.maxLength(2048)]],
      apkFileName: [entity?.apkFileName ?? '', [Validators.maxLength(255)]],
      apkSize: [entity?.apkSize ?? null, [Validators.min(1)]],
      apkMd5: [entity?.apkMd5 ?? '', [Validators.maxLength(64)]],
      previewUrl: [entity?.previewUrl ?? '', [Validators.maxLength(2048)]],
      forceUpdate: [entity?.forceUpdate ?? false],
    });
    return form;
  }

  updateForm(entity: AndroidAppVersion) {
    this.entityForm.patchValue(entity, {emitEvent: false});
  }

  get isPublished(): boolean {
    return this.entity?.status === AndroidAppVersionStatus.PUBLISHED;
  }

}
