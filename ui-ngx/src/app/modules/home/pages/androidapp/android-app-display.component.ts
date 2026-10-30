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

import { ChangeDetectorRef, Component, Inject, OnDestroy, Optional } from '@angular/core';
import { EntityComponent } from '@home/components/entity/entity.component';
import { AppState } from '@core/core.state';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { EntityType } from '@shared/models/entity-type.models';
import {
  AndroidAppDisplay,
  AndroidAppDisplayPlatform,
  androidAppDisplayPlatformTranslations
} from '@shared/models/android-app.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isNotEmptyStr, unwrapModule } from '@core/utils';

@Component({
  selector: 'tb-android-app-display',
  templateUrl: './android-app-display.component.html',
  styleUrls: ['./android-app-display.component.scss']
})
export class AndroidAppDisplayComponent extends EntityComponent<AndroidAppDisplay> implements OnDestroy {

  entityType = EntityType;

  androidAppDisplayPlatforms = Object.keys(AndroidAppDisplayPlatform) as AndroidAppDisplayPlatform[];

  androidAppDisplayPlatformTranslations = androidAppDisplayPlatformTranslations;

  qrCodeSVG: string = null;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Optional() @Inject('entity') protected entityValue: AndroidAppDisplay,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<AndroidAppDisplay>,
              protected cd: ChangeDetectorRef,
              public fb: FormBuilder) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: AndroidAppDisplay): FormGroup {
    const form = this.fb.group({
      platformType: [entity?.platformType ? entity.platformType : AndroidAppDisplayPlatform.ANDROID, [Validators.required]],
      appName: [entity?.appName ?? '', [Validators.required, Validators.maxLength(255)]],
      downloadUrl: [entity?.downloadUrl ?? '', [Validators.required, Validators.maxLength(2048)]],
      showOnHome: [entity?.showOnHome ?? false],
      sortOrder: [entity?.sortOrder ?? 0, [Validators.required, Validators.min(0)]],
    });
    form.get('downloadUrl').valueChanges.pipe(takeUntil(this.destroy$)).subscribe(value => {
      this.updateQRCode(value);
    });
    this.updateQRCode(entity?.downloadUrl);
    return form;
  }

  updateForm(entity: AndroidAppDisplay) {
    this.entityForm.patchValue(entity, {emitEvent: false});
    this.updateQRCode(entity?.downloadUrl);
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateQRCode(downloadUrl: string) {
    if (!isNotEmptyStr(downloadUrl)) {
      this.qrCodeSVG = null;
      this.cd.markForCheck();
      return;
    }
    import('qrcode').then((QRCode) => {
      unwrapModule(QRCode).toString(downloadUrl, {type: 'svg', margin: 1, width: 200}, (_err, svgElement) => {
        this.qrCodeSVG = svgElement;
        this.cd.markForCheck();
      });
    });
  }

}
