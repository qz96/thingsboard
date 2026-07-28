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

import { Component, OnInit, Inject } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { UntypedFormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Constants } from '@shared/models/constants';
import { Router } from '@angular/router';
import { OAuth2ClientLoginInfo } from '@shared/models/oauth2.models';
import { environment as env } from '@env/environment';
import { TranslateService } from '@ngx-translate/core';
import { updateUserLang } from '@core/settings/settings.utils';
import { DOCUMENT } from '@angular/common';

@Component({
  selector: 'tb-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent extends PageComponent implements OnInit {

  passwordViolation = false;
  currentLang: string;
  languageList = env.supportedLangs;

  loginFormGroup = this.fb.group({
    username: ['', [Validators.required]],
    password: ['']
  });
  oauth2Clients: Array<OAuth2ClientLoginInfo> = null;

  private langDisplayNames: { [key: string]: string } = {
    'zh_CN': '中文',
    'en_US': 'English',
    'zh_TW': '繁體中文'
  };

  constructor(protected store: Store<AppState>,
              private authService: AuthService,
              public fb: UntypedFormBuilder,
              private router: Router,
              private translate: TranslateService,
              @Inject(DOCUMENT) private document: Document) {
    super(store);
    this.currentLang = this.translate.currentLang || env.defaultLang;
  }

  ngOnInit() {
    this.oauth2Clients = this.authService.oauth2Clients;
  }

  getLangDisplayName(lang: string): string {
    return this.langDisplayNames[lang] || lang;
  }

  changeLanguage(lang: string): void {
    this.currentLang = lang;
    updateUserLang(this.translate, this.document, lang).subscribe();
  }

  login(): void {
    if (this.loginFormGroup.valid) {
      this.authService.login(this.loginFormGroup.value).subscribe(
        () => {},
        (error: HttpErrorResponse) => {
          if (error && error.error && error.error.errorCode) {
            if (error.error.errorCode === Constants.serverErrorCode.credentialsExpired) {
              this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${error.error.resetToken}`);
            } else if (error.error.errorCode === Constants.serverErrorCode.passwordViolation) {
              this.passwordViolation = true;
            }
          }
        }
      );
    } else {
      Object.keys(this.loginFormGroup.controls).forEach(field => {
        const control = this.loginFormGroup.get(field);
        control.markAsTouched({onlySelf: true});
      });
    }
  }

  getOAuth2Uri(oauth2Client: OAuth2ClientLoginInfo): string {
    let result = "";
    if (this.authService.redirectUrl) {
      result += "?prevUri=" + this.authService.redirectUrl;
    }
    return oauth2Client.url + result;
  }
}
