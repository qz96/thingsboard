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
import org.thingsboard.server.common.data.androidapp.AndroidAppDisplay;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AndroidAppDisplayId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.androidapp.AndroidAppDisplayService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;

import java.util.List;
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
public class AndroidAppDisplayController extends BaseController {

    private final AndroidAppDisplayService androidAppDisplayService;

    @ApiOperation(value = "Save Or update Android app display config (saveAndroidAppDisplay)",
            notes = "Create or update the Android app display config used to generate a QR code " +
                    "and optionally show the entry on the home page.\n\n" + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/android/app/display")
    public AndroidAppDisplay saveAndroidAppDisplay(@Parameter(description = "A JSON value representing the Android app display config.", required = true)
                                                   @RequestBody @Valid AndroidAppDisplay androidAppDisplay) throws ThingsboardException {
        androidAppDisplay.setTenantId(getTenantId());
        return androidAppDisplayService.saveAndroidAppDisplay(getTenantId(), androidAppDisplay);
    }

    @ApiOperation(value = "Get Android app display configs (getAndroidAppDisplays)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app/display")
    public PageData<AndroidAppDisplay> getAndroidAppDisplays(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                             @RequestParam int pageSize,
                                                             @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                             @RequestParam int page,
                                                             @Parameter(description = "Case-insensitive 'substring' filter based on app name or download URL")
                                                             @RequestParam(required = false) String textSearch,
                                                             @Parameter(description = SORT_PROPERTY_DESCRIPTION)
                                                             @RequestParam(required = false) String sortProperty,
                                                             @Parameter(description = SORT_ORDER_DESCRIPTION)
                                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return androidAppDisplayService.findAndroidAppDisplaysByTenantId(getTenantId(), pageLink);
    }

    @ApiOperation(value = "Get Android app display config by id (getAndroidAppDisplayById)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/android/app/display/{id}")
    public AndroidAppDisplay getAndroidAppDisplayById(@PathVariable UUID id) throws ThingsboardException {
        return checkEntityId(new AndroidAppDisplayId(id), androidAppDisplayService::findAndroidAppDisplayById, Operation.READ);
    }

    @ApiOperation(value = "Show or hide Android app display config on the home page (setAndroidAppDisplayShowOnHome)",
            notes = "Updates the 'show on home page' flag of the display config.\n\n" + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PutMapping(value = "/android/app/display/{id}/showOnHome/{showOnHome}")
    public AndroidAppDisplay setAndroidAppDisplayShowOnHome(@PathVariable UUID id,
                                                            @PathVariable boolean showOnHome) throws ThingsboardException {
        AndroidAppDisplayId androidAppDisplayId = new AndroidAppDisplayId(id);
        checkEntityId(androidAppDisplayId, androidAppDisplayService::findAndroidAppDisplayById, Operation.WRITE);
        return androidAppDisplayService.setAndroidAppDisplayShowOnHome(getTenantId(), androidAppDisplayId, showOnHome);
    }

    @ApiOperation(value = "Delete Android app display config by id (deleteAndroidAppDisplay)", notes = SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/android/app/display/{id}")
    public void deleteAndroidAppDisplay(@PathVariable UUID id) throws ThingsboardException {
        AndroidAppDisplayId androidAppDisplayId = new AndroidAppDisplayId(id);
        checkEntityId(androidAppDisplayId, androidAppDisplayService::findAndroidAppDisplayById, Operation.DELETE);
        androidAppDisplayService.deleteAndroidAppDisplayById(getTenantId(), androidAppDisplayId);
    }

    @ApiOperation(value = "Get Android app display configs shown on the home page (getAndroidAppDisplaysForHome)",
            notes = "Anonymous endpoint for the home page to fetch the app display entries with 'show on home' enabled, " +
                    "ordered by sort order ascending.")
    @GetMapping(value = "/noauth/android/app/display/home")
    public List<AndroidAppDisplay> getAndroidAppDisplaysForHome() {
        return androidAppDisplayService.findAndroidAppDisplaysByShowOnHome(TenantId.SYS_TENANT_ID, true);
    }

}
