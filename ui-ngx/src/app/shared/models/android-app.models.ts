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

import { BaseData } from '@shared/models/base-data';
import { TenantId } from '@shared/models/id/tenant-id';
import { AndroidAppId } from '@shared/models/id/android-app-id';
import { AndroidAppVersionId } from '@shared/models/id/android-app-version-id';

export enum AndroidAppStatus {
  ACTIVE = 'ACTIVE',
  DISABLED = 'DISABLED'
}

export const androidAppStatusTranslations = new Map<AndroidAppStatus, string>(
  [
    [AndroidAppStatus.ACTIVE, 'androidapp.status-type.active'],
    [AndroidAppStatus.DISABLED, 'androidapp.status-type.disabled'],
  ]
);

export enum AndroidAppVersionStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  RETRACTED = 'RETRACTED'
}

export const androidAppVersionStatusTranslations = new Map<AndroidAppVersionStatus, string>(
  [
    [AndroidAppVersionStatus.DRAFT, 'androidapp.version-status-type.draft'],
    [AndroidAppVersionStatus.PUBLISHED, 'androidapp.version-status-type.published'],
    [AndroidAppVersionStatus.RETRACTED, 'androidapp.version-status-type.retracted'],
  ]
);

export interface AndroidApp extends BaseData<AndroidAppId> {
  tenantId?: TenantId;
  title: string;
  pkgName: string;
  description?: string;
  iconUrl?: string;
  iconFileName?: string;
  status: AndroidAppStatus;
  updatedTime?: number;
}

export interface AndroidAppVersion extends BaseData<AndroidAppVersionId> {
  tenantId?: TenantId;
  appId: AndroidAppId;
  versionName: string;
  versionCode: number;
  releaseNotes?: string;
  apkUrl?: string;
  apkFileName?: string;
  apkSize?: number;
  apkMd5?: string;
  previewUrl?: string;
  forceUpdate?: boolean;
  status: AndroidAppVersionStatus;
  updatedTime?: number;
}

export interface AndroidAppUpdateInfo {
  hasUpdate: boolean;
  versionName?: string;
  versionCode?: number;
  releaseNotes?: string;
  apkUrl?: string;
  forceUpdate?: boolean;
}
