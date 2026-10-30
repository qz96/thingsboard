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

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  Renderer2,
  ViewContainerRef
} from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import { AndroidAppService } from '@core/http/android-app.service';
import { AndroidAppDisplay } from '@shared/models/android-app.models';
import { ShowAppDownloadPopoverComponent } from '@shared/components/app-download/show-app-download-popover.component';

/**
 * 顶部工具栏的 App 下载入口：点击后像通知铃铛一样展开二维码面板，
 * 配置了多条展示时可在面板内切换。未配置任何展示项时整个按钮隐藏。
 */
@Component({
  selector: 'tb-app-download-bell',
  templateUrl: './app-download-bell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppDownloadBellComponent implements OnInit, OnDestroy {

  displays: AndroidAppDisplay[] = [];

  private destroy$ = new Subject<void>();

  constructor(private androidAppService: AndroidAppService,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit(): void {
    this.androidAppService.getAndroidAppDisplaysForHome()
      .pipe(takeUntil(this.destroy$))
      .subscribe((displays: AndroidAppDisplay[]) => {
        this.displays = displays ?? [];
        this.cd.markForCheck();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  showAppDownload($event: Event, appDownloadButton: MatButton): void {
    if ($event) {
      $event.stopPropagation();
    }
    if (!this.displays.length) {
      return;
    }
    const trigger = appDownloadButton._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const showAppDownloadPopover = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, ShowAppDownloadPopoverComponent, 'bottom', true, null,
        {
          onClose: () => {
            showAppDownloadPopover.hide();
          },
          displays: this.displays
        },
        {maxHeight: '90vh', height: '100%', padding: '10px'},
        {width: '320px', minWidth: '100%', maxWidth: '100%'},
        {height: '100%', flexDirection: 'column', boxSizing: 'border-box', display: 'flex', margin: '0 -16px'}, false);
      showAppDownloadPopover.tbComponentRef.instance.popoverComponent = showAppDownloadPopover;
    }
  }

}
