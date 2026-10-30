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
import { AndroidApp, AndroidAppStatus, androidAppStatusTranslations } from '@shared/models/android-app.models';

@Component({
  selector: 'tb-android-app',
  templateUrl: './android-app.component.html',
  styleUrls: ['./android-app.component.scss']
})
export class AndroidAppComponent extends EntityComponent<AndroidApp> {

  entityType = EntityType;

  androidAppStatuses = Object.keys(AndroidAppStatus) as AndroidAppStatus[];

  androidAppStatusTranslations = androidAppStatusTranslations;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Optional() @Inject('entity') protected entityValue: AndroidApp,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<AndroidApp>,
              protected cd: ChangeDetectorRef,
              public fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: AndroidApp): FormGroup {
    const form = this.fb.group({
      title: [entity?.title ?? '', [Validators.required, Validators.maxLength(255)]],
      pkgName: [entity?.pkgName ?? '', [Validators.required, Validators.maxLength(255),
        Validators.pattern(/^[a-zA-Z][a-zA-Z\d_]*(?:\.[a-zA-Z][a-zA-Z\d_]*)+$/)]],
      description: [entity?.description ?? '', [Validators.maxLength(1024)]],
      iconUrl: [entity?.iconUrl ?? '', [Validators.maxLength(2048)]],
      iconFileName: [entity?.iconFileName ?? '', [Validators.maxLength(255)]],
      status: [entity?.status ? entity.status : AndroidAppStatus.ACTIVE],
    });
    return form;
  }

  updateForm(entity: AndroidApp) {
    this.entityForm.patchValue(entity, {emitEvent: false});
  }

}
