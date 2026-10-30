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

import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Device } from '@shared/models/device.models';
import { RegionDeviceInfo } from '@shared/models/platform.models';
import { PlatformDeviceService } from '@core/http/platform-device.service';

export interface PlatformDeviceDialogData {
  tenantId: string;
  regionTitle: string;
  device?: RegionDeviceInfo;
}

export interface PlatformDeviceDialogResult {
  created: boolean;
}

@Component({
  selector: 'tb-platform-device-dialog',
  templateUrl: './platform-device-dialog.component.html',
  styleUrls: ['./platform-device-dialog.component.scss']
})
export class PlatformDeviceDialogComponent {

  readonly isEdit: boolean;
  readonly form: FormGroup;
  submitted = false;
  saving = false;

  constructor(private fb: FormBuilder,
              private dialogRef: MatDialogRef<PlatformDeviceDialogComponent, PlatformDeviceDialogResult>,
              private platformDeviceService: PlatformDeviceService,
              @Inject(MAT_DIALOG_DATA) public data: PlatformDeviceDialogData) {
    this.isEdit = !!data.device;
    this.form = this.fb.group({
      name: [data.device?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      type: [data.device?.type ?? '', [Validators.maxLength(255)]],
      label: [data.device?.label ?? '', [Validators.maxLength(255)]],
      accessToken: ['', [Validators.maxLength(255)]]
    });
    if (this.isEdit) {
      this.form.get('accessToken').disable();
    }
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    if (this.form.invalid) {
      return;
    }
    this.saving = true;
    const value = this.form.getRawValue();
    const device = {
      name: value.name,
      type: value.type || null,
      label: value.label || null
    } as unknown as Device;

    const request$ = this.isEdit
      ? this.platformDeviceService.updateRegionDevice(this.data.tenantId, this.data.device.id, device)
      : this.platformDeviceService.saveRegionDevice(this.data.tenantId, device, value.accessToken || null);

    request$.subscribe({
      next: () => {
        this.saving = false;
        this.dialogRef.close({created: !this.isEdit});
      },
      error: () => {
        this.saving = false;
      }
    });
  }

}
