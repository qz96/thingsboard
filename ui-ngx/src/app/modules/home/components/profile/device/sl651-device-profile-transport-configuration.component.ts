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

import { Component, DestroyRef, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  ValidationErrors,
  Validator
} from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { DeviceTransportType, Sl651DeviceProfileTransportConfiguration } from '@shared/models/device.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-sl651-device-profile-transport-configuration',
  templateUrl: './sl651-device-profile-transport-configuration.component.html',
  styleUrls: [],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => Sl651DeviceProfileTransportConfigurationComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => Sl651DeviceProfileTransportConfigurationComponent),
      multi: true
    }
  ]
})
export class Sl651DeviceProfileTransportConfigurationComponent implements ControlValueAccessor, OnInit, Validator {

  sl651DeviceProfileTransportConfigurationFormGroup: UntypedFormGroup;

  @Input()
  disabled: boolean;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private fb: UntypedFormBuilder,
              private destroyRef: DestroyRef) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.sl651DeviceProfileTransportConfigurationFormGroup = this.fb.group({
      telemetryKeyPrefix: [null],
      statusReportAsAttribute: [true],
      reportTypesText: [null]
    });
    this.sl651DeviceProfileTransportConfigurationFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateModel();
    });
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (!this.sl651DeviceProfileTransportConfigurationFormGroup) {
      return;
    }
    if (this.disabled) {
      this.sl651DeviceProfileTransportConfigurationFormGroup.disable({emitEvent: false});
    } else {
      this.sl651DeviceProfileTransportConfigurationFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Sl651DeviceProfileTransportConfiguration | null): void {
        if (!this.sl651DeviceProfileTransportConfigurationFormGroup) {
            return;
        }
        this.sl651DeviceProfileTransportConfigurationFormGroup.patchValue({
            telemetryKeyPrefix: value?.telemetryKeyPrefix ?? 'sl651',
            statusReportAsAttribute: value?.statusReportAsAttribute ?? true,
            reportTypesText: (value?.reportTypes ?? []).join(', ')
        }, {emitEvent: false});
    }

    validate(c: UntypedFormControl): ValidationErrors | null {
        if (!this.sl651DeviceProfileTransportConfigurationFormGroup) {
            return null;
        }
        return (this.sl651DeviceProfileTransportConfigurationFormGroup.valid) ? null : {
            configuration: {
                valid: false
            }
        };
    }

    private updateModel() {
        if (!this.sl651DeviceProfileTransportConfigurationFormGroup) {
            return;
        }
        const raw = this.sl651DeviceProfileTransportConfigurationFormGroup.getRawValue();
        const configuration: Sl651DeviceProfileTransportConfiguration = {
      telemetryKeyPrefix: raw.telemetryKeyPrefix || 'sl651',
      statusReportAsAttribute: raw.statusReportAsAttribute,
      reportTypes: (raw.reportTypesText || '')
        .split(/[,，\s]+/)
        .map((item: string) => item.trim())
        .filter((item: string) => item.length > 0)
    };
    (configuration as any).type = DeviceTransportType.SL651;
    this.propagateChange(configuration);
  }
}