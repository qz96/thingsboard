/**
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.androidapp.AndroidApp;
import org.thingsboard.server.common.data.androidapp.AndroidAppUpdateInfo;
import org.thingsboard.server.common.data.androidapp.AndroidAppVersion;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AndroidAppId;
import org.thingsboard.server.common.data.id.AndroidAppVersionId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.androidapp.AndroidAppService;
import org.thingsboard.server.dao.androidapp.AndroidAppVersionService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;

import java.util.UUID;

import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_PROPERTY_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_AUTHORITY_PARAGRAPH;

@RestController
@TbCoreComponent
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AndroidAppController extends BaseController {

    private final AndroidAppService androidAppService;
    private final AndroidAppVersionService androidAppVersionService;

    @ApiOperation(value = "Save Or update Android app (saveAndroidApp)",
            notes = "Create or update the Android application. The pair of tenant id and package name is unique.\n\n" + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/android/app")
    public AndroidApp saveAndroidApp(@Parameter(description = "A JSON value representing the Android application.", required = true)
                                     @RequestBody @Valid AndroidApp androidApp) throws ThingsboardException {
        androidApp.setTenantId(getTenantId());
        return androidAppService.saveAndroidApp(getTenantId(), androidApp);
    }

    @ApiOperation(value = "Get Android apps (getAndroidApps)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app")
    public PageData<AndroidApp> getAndroidApps(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                               @RequestParam int pageSize,
                                               @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                               @RequestParam int page,
                                               @Parameter(description = "Case-insensitive 'substring' filter based on app title or package name")
                                               @RequestParam(required = false) String textSearch,
                                               @Parameter(description = SORT_PROPERTY_DESCRIPTION)
                                               @RequestParam(required = false) String sortProperty,
                                               @Parameter(description = SORT_ORDER_DESCRIPTION)
                                               @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return androidAppService.findAndroidAppsByTenantId(getTenantId(), pageLink);
    }

    @ApiOperation(value = "Get Android app by id (getAndroidAppById)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app/{id}")
    public AndroidApp getAndroidAppById(@PathVariable UUID id) throws ThingsboardException {
        return checkEntityId(new AndroidAppId(id), androidAppService::findAndroidAppById, Operation.READ);
    }

    @ApiOperation(value = "Delete Android app by id (deleteAndroidApp)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/android/app/{id}")
    public void deleteAndroidApp(@PathVariable UUID id) throws ThingsboardException {
        AndroidAppId androidAppId = new AndroidAppId(id);
        checkEntityId(androidAppId, androidAppService::findAndroidAppById, Operation.DELETE);
        androidAppService.deleteAndroidAppById(getTenantId(), androidAppId);
    }

    @ApiOperation(value = "Save Or update Android app version (saveAndroidAppVersion)",
            notes = "Create or update a version of the Android application. The pair of app id and version code is unique.\n\n" + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/android/app/{appId}/version")
    public AndroidAppVersion saveAndroidAppVersion(@Parameter(description = "Android app id", required = true)
                                                   @PathVariable UUID appId,
                                                   @Parameter(description = "A JSON value representing the Android app version.", required = true)
                                                   @RequestBody @Valid AndroidAppVersion version) throws ThingsboardException {
        version.setTenantId(getTenantId());
        version.setAppId(new AndroidAppId(appId));
        return androidAppVersionService.saveAndroidAppVersion(getTenantId(), version);
    }

    @ApiOperation(value = "Get Android app versions (getAndroidAppVersions)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app/{appId}/version")
    public PageData<AndroidAppVersion> getAndroidAppVersions(@Parameter(description = "Android app id", required = true)
                                                             @PathVariable UUID appId,
                                                             @Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                             @RequestParam int pageSize,
                                                             @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                             @RequestParam int page,
                                                             @Parameter(description = "Case-insensitive 'substring' filter based on version name")
                                                             @RequestParam(required = false) String textSearch,
                                                             @Parameter(description = SORT_PROPERTY_DESCRIPTION)
                                                             @RequestParam(required = false) String sortProperty,
                                                             @Parameter(description = SORT_ORDER_DESCRIPTION)
                                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return androidAppVersionService.findAndroidAppVersionsByAppId(getTenantId(), appId, pageLink);
    }

    @ApiOperation(value = "Get Android app version by id (getAndroidAppVersionById)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app/version/{versionId}")
    public AndroidAppVersion getAndroidAppVersionById(@PathVariable UUID versionId) throws ThingsboardException {
        return checkEntityId(new AndroidAppVersionId(versionId), androidAppVersionService::findAndroidAppVersionById, Operation.READ);
    }

    @ApiOperation(value = "Publish Android app version (publishAndroidAppVersion)",
            notes = "Publishes the version and automatically retracts the currently published version of the same app.\n\n" + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PutMapping(value = "/android/app/version/{versionId}/publish")
    public AndroidAppVersion publishAndroidAppVersion(@PathVariable UUID versionId) throws ThingsboardException {
        AndroidAppVersionId versionPk = new AndroidAppVersionId(versionId);
        checkEntityId(versionPk, androidAppVersionService::findAndroidAppVersionById, Operation.WRITE);
        return androidAppVersionService.publishAndroidAppVersion(getTenantId(), versionPk);
    }

    @ApiOperation(value = "Retract Android app version (retractAndroidAppVersion)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PutMapping(value = "/android/app/version/{versionId}/retract")
    public AndroidAppVersion retractAndroidAppVersion(@PathVariable UUID versionId) throws ThingsboardException {
        AndroidAppVersionId versionPk = new AndroidAppVersionId(versionId);
        checkEntityId(versionPk, androidAppVersionService::findAndroidAppVersionById, Operation.WRITE);
        return androidAppVersionService.retractAndroidAppVersion(getTenantId(), versionPk);
    }

    @ApiOperation(value = "Delete Android app version by id (deleteAndroidAppVersion)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/android/app/version/{versionId}")
    public void deleteAndroidAppVersion(@PathVariable UUID versionId) throws ThingsboardException {
        AndroidAppVersionId versionPk = new AndroidAppVersionId(versionId);
        checkEntityId(versionPk, androidAppVersionService::findAndroidAppVersionById, Operation.DELETE);
        androidAppVersionService.deleteAndroidAppVersionById(getTenantId(), versionPk);
    }

    @ApiOperation(value = "Check Android app update (checkAppUpdate)",
            notes = "Anonymous endpoint for the Android app to check for updates by package name and current version code.")
    @GetMapping(value = "/noauth/android/app/check")
    public AndroidAppUpdateInfo checkAppUpdate(@Parameter(description = "Android application package name", required = true)
                                               @RequestParam String pkgName,
                                               @Parameter(description = "Current version code of the client application", required = true)
                                               @RequestParam int versionCode) {
        return androidAppVersionService.checkAppUpdate(pkgName, versionCode);
    }

}
