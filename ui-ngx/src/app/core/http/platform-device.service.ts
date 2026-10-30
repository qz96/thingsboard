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
import { Device } from '@shared/models/device.models';
import {
  RegionDeviceInfo,
  RegionDeviceStatus,
  RegionLoginAsResult,
  RegionOverview,
  RegionSummary
} from '@shared/models/platform.models';

@Injectable({
  providedIn: 'root'
})
export class PlatformDeviceService {

  constructor(private http: HttpClient) {
  }

  public getRegions(config?: RequestConfig): Observable<RegionOverview[]> {
    return this.http.get<RegionOverview[]>('/api/platform/regions', defaultHttpOptionsFromConfig(config));
  }

  public getRegionSummary(tenantId: string, config?: RequestConfig): Observable<RegionSummary> {
    return this.http.get<RegionSummary>(`/api/platform/regions/${tenantId}/summary`, defaultHttpOptionsFromConfig(config));
  }

  public getRegionDevices(tenantId: string, pageLink: PageLink, status: RegionDeviceStatus,
                          config?: RequestConfig): Observable<PageData<RegionDeviceInfo>> {
    let url = `/api/platform/regions/${tenantId}/devices${pageLink.toQuery()}`;
    if (status) {
      url += `&status=${status}`;
    }
    return this.http.get<PageData<RegionDeviceInfo>>(url, defaultHttpOptionsFromConfig(config));
  }

  public saveRegionDevice(tenantId: string, device: Device, accessToken?: string, config?: RequestConfig): Observable<Device> {
    let url = `/api/platform/regions/${tenantId}/devices`;
    if (accessToken) {
      url += `?accessToken=${encodeURIComponent(accessToken)}`;
    }
    return this.http.post<Device>(url, device, defaultHttpOptionsFromConfig(config));
  }

  public updateRegionDevice(tenantId: string, deviceId: string, device: Device, config?: RequestConfig): Observable<Device> {
    return this.http.put<Device>(`/api/platform/regions/${tenantId}/devices/${deviceId}`, device,
      defaultHttpOptionsFromConfig(config));
  }

  public loginAsRegionAdmin(tenantId: string, config?: RequestConfig): Observable<RegionLoginAsResult> {
    return this.http.post<RegionLoginAsResult>(`/api/platform/regions/${tenantId}/login-as`, null,
      defaultHttpOptionsFromConfig(config));
  }

  public deleteRegionDevice(tenantId: string, deviceId: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/platform/regions/${tenantId}/devices/${deviceId}`,
      defaultHttpOptionsFromConfig(config));
  }

}
