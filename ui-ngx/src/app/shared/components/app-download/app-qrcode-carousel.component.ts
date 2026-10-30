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
  Input,
  OnDestroy,
  OnInit
} from '@angular/core';
import { AndroidAppDisplay } from '@shared/models/android-app.models';
import { unwrapModule } from '@core/utils';

export interface AppQrcodeCard {
  display: AndroidAppDisplay;
  qrCodeSVG: string;
}

/**
 * App 下载二维码轮播：根据传入的展示配置逐条生成二维码，支持左右切换与圆点切换。
 * 若配置了多条（sortOrder 升序），可循环切换。
 */
@Component({
  selector: 'tb-app-qrcode-carousel',
  templateUrl: './app-qrcode-carousel.component.html',
  styleUrls: ['./app-qrcode-carousel.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppQrcodeCarouselComponent implements OnInit, OnDestroy {

  @Input() displays: AndroidAppDisplay[] = [];

  @Input() qrSize = 132;

  @Input() showDownloadLink = false;

  cards: AppQrcodeCard[] = [];

  index = 0;

  constructor(private cd: ChangeDetectorRef) {
  }

  ngOnInit(): void {
    this.cards = (this.displays ?? []).map(display => ({display, qrCodeSVG: null}));
    this.index = 0;
    this.generateQrCodes();
  }

  ngOnDestroy(): void {
    this.cards = [];
  }

  get current(): AppQrcodeCard {
    return this.cards[this.index];
  }

  prev($event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.cards.length > 1) {
      this.index = (this.index - 1 + this.cards.length) % this.cards.length;
      this.cd.markForCheck();
    }
  }

  next($event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.cards.length > 1) {
      this.index = (this.index + 1) % this.cards.length;
      this.cd.markForCheck();
    }
  }

  select(index: number, $event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.index = index;
    this.cd.markForCheck();
  }

  private generateQrCodes(): void {
    if (!this.cards.length) {
      return;
    }
    import('qrcode').then((QRCode) => {
      const qrCodeLib = unwrapModule(QRCode);
      this.cards.forEach(card => {
        qrCodeLib.toString(card.display.downloadUrl, {type: 'svg', margin: 1, width: this.qrSize},
          (_err, svgElement) => {
            card.qrCodeSVG = svgElement;
            this.cd.markForCheck();
          });
      });
    });
  }

}
