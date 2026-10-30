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

import { Component, Input, OnDestroy } from '@angular/core';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { AndroidAppDisplay } from '@shared/models/android-app.models';

@Component({
  selector: 'tb-show-app-download-popover',
  templateUrl: './show-app-download-popover.component.html',
  styleUrls: ['./show-app-download-popover.component.scss']
})
export class ShowAppDownloadPopoverComponent implements OnDestroy {

  @Input()
  displays: AndroidAppDisplay[] = [];

  @Input()
  onClose: () => void;

  @Input()
  popoverComponent: TbPopoverComponent;

  ngOnDestroy(): void {
    if (this.onClose) {
      this.onClose();
    }
  }

}
