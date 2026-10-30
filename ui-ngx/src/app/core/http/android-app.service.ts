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

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { Observable } from 'rxjs';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { AndroidApp, AndroidAppDisplay, AndroidAppUpdateInfo, AndroidAppVersion } from '@shared/models/android-app.models';

@Injectable({
  providedIn: 'root'
})
export class AndroidAppService {

  constructor(
    private http: HttpClient
  ) {
  }

  public saveAndroidApp(androidApp: AndroidApp, config?: RequestConfig): Observable<AndroidApp> {
    return this.http.post<AndroidApp>(`/api/android/app`, androidApp, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidApps(pageLink: PageLink, config?: RequestConfig): Observable<PageData<AndroidApp>> {
    return this.http.get<PageData<AndroidApp>>(`/api/android/app${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppById(id: string, config?: RequestConfig): Observable<AndroidApp> {
    return this.http.get<AndroidApp>(`/api/android/app/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteAndroidApp(id: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/android/app/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public saveAndroidAppVersion(appId: string, version: AndroidAppVersion, config?: RequestConfig): Observable<AndroidAppVersion> {
    return this.http.post<AndroidAppVersion>(`/api/android/app/${appId}/version`, version, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppVersions(appId: string, pageLink: PageLink, config?: RequestConfig): Observable<PageData<AndroidAppVersion>> {
    return this.http.get<PageData<AndroidAppVersion>>(`/api/android/app/${appId}/version${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppVersionById(id: string, config?: RequestConfig): Observable<AndroidAppVersion> {
    return this.http.get<AndroidAppVersion>(`/api/android/app/version/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public publishAndroidAppVersion(id: string, config?: RequestConfig): Observable<AndroidAppVersion> {
    return this.http.put<AndroidAppVersion>(`/api/android/app/version/${id}/publish`, null, defaultHttpOptionsFromConfig(config));
  }

  public retractAndroidAppVersion(id: string, config?: RequestConfig): Observable<AndroidAppVersion> {
    return this.http.put<AndroidAppVersion>(`/api/android/app/version/${id}/retract`, null, defaultHttpOptionsFromConfig(config));
  }

  public deleteAndroidAppVersion(id: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/android/app/version/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public checkAppUpdate(pkgName: string, versionCode: number, config?: RequestConfig): Observable<AndroidAppUpdateInfo> {
    return this.http.get<AndroidAppUpdateInfo>(`/api/noauth/android/app/check?pkgName=${pkgName}&versionCode=${versionCode}`,
      defaultHttpOptionsFromConfig(config));
  }

  public saveAndroidAppDisplay(androidAppDisplay: AndroidAppDisplay, config?: RequestConfig): Observable<AndroidAppDisplay> {
    return this.http.post<AndroidAppDisplay>(`/api/android/app/display`, androidAppDisplay, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppDisplays(pageLink: PageLink, config?: RequestConfig): Observable<PageData<AndroidAppDisplay>> {
    return this.http.get<PageData<AndroidAppDisplay>>(`/api/android/app/display${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppDisplayById(id: string, config?: RequestConfig): Observable<AndroidAppDisplay> {
    return this.http.get<AndroidAppDisplay>(`/api/android/app/display/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public setAndroidAppDisplayShowOnHome(id: string, showOnHome: boolean, config?: RequestConfig): Observable<AndroidAppDisplay> {
    return this.http.put<AndroidAppDisplay>(`/api/android/app/display/${id}/showOnHome/${showOnHome}`,
      null, defaultHttpOptionsFromConfig(config));
  }

  public deleteAndroidAppDisplay(id: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/android/app/display/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public getAndroidAppDisplaysForHome(config?: RequestConfig): Observable<AndroidAppDisplay[]> {
    return this.http.get<AndroidAppDisplay[]>(`/api/noauth/android/app/display/home`, defaultHttpOptionsFromConfig(config));
  }

}
