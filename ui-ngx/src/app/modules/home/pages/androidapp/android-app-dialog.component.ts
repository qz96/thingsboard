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

import { AfterViewInit, Component, SkipSelf, ViewChild } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { FormGroupDirective, NgForm, UntypedFormControl } from '@angular/forms';
import { AndroidApp } from '@shared/models/android-app.models';
import type { AndroidAppComponent } from '@home/pages/androidapp/android-app.component';
import { AndroidAppService } from '@core/http/android-app.service';

@Component({
  selector: 'tb-android-app-dialog',
  templateUrl: './android-app-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: AndroidAppDialogComponent}],
  styleUrls: []
})
export class AndroidAppDialogComponent extends DialogComponent<AndroidAppDialogComponent, AndroidApp> implements AfterViewInit, ErrorStateMatcher {

  submitted = false;

  @ViewChild('androidAppComponent', {static: true}) androidAppComponent: AndroidAppComponent;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              protected dialogRef: MatDialogRef<AndroidAppDialogComponent, AndroidApp>,
              private androidAppService: AndroidAppService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher) {
    super(store, router, dialogRef);
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.androidAppComponent.entityForm.markAsDirty();
      this.androidAppComponent.isEdit = true;
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
    if (this.androidAppComponent.entityForm.valid) {
      this.androidAppService.saveAndroidApp(this.androidAppComponent.entityFormValue()).subscribe(
        app => this.dialogRef.close(app)
      );
    }
  }
}
