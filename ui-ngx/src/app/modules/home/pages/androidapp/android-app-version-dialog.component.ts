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

import { AfterViewInit, Component, Inject, SkipSelf, ViewChild } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormGroupDirective, NgForm, UntypedFormControl } from '@angular/forms';
import { AndroidAppVersion } from '@shared/models/android-app.models';
import type { AndroidAppVersionComponent } from '@home/pages/androidapp/android-app-version.component';
import { AndroidAppService } from '@core/http/android-app.service';

export interface AndroidAppVersionDialogData {
  appId: string;
}

@Component({
  selector: 'tb-android-app-version-dialog',
  templateUrl: './android-app-version-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: AndroidAppVersionDialogComponent}],
  styleUrls: []
})
export class AndroidAppVersionDialogComponent extends DialogComponent<AndroidAppVersionDialogComponent, AndroidAppVersion> implements AfterViewInit, ErrorStateMatcher {

  submitted = false;

  @ViewChild('androidAppVersionComponent', {static: true}) androidAppVersionComponent: AndroidAppVersionComponent;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              protected dialogRef: MatDialogRef<AndroidAppVersionDialogComponent, AndroidAppVersion>,
              private androidAppService: AndroidAppService,
              @Inject(MAT_DIALOG_DATA) public data: AndroidAppVersionDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher) {
    super(store, router, dialogRef);
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.androidAppVersionComponent.entityForm.markAsDirty();
      this.androidAppVersionComponent.isEdit = true;
    }, 0);
  }

  isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save() {
    this.submitted = true;
    if (this.androidAppVersionComponent.entityForm.valid) {
      this.androidAppService.saveAndroidAppVersion(this.data.appId,
        this.androidAppVersionComponent.entityFormValue()).subscribe(
        version => this.dialogRef.close(version)
      );
    }
  }
}
