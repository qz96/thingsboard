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

export interface RegionOverview {
  tenantId: string;
  title: string;
  deviceCount: number;
  activeCount: number;
  offlineCount: number;
  alarmCount: number;
}

export interface RegionSummary extends RegionOverview {
  alarmBySeverity: { [severity: string]: number };
}

export interface RegionDeviceInfo {
  id: string;
  name: string;
  type: string;
  label: string;
  active: boolean;
  alarmCount: number;
}

export interface RegionLoginAsResult {
  token: string;
  refreshToken: string;
  scope?: string;
}

export type RegionDeviceStatus = 'ALL' | 'ACTIVE' | 'OFFLINE' | 'ALARM';

export const regionDeviceStatuses: RegionDeviceStatus[] = ['ALL', 'ACTIVE', 'OFFLINE', 'ALARM'];
