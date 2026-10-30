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
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.common.data.security.model.JwtPair;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.platform.RegionDeviceInfo;
import org.thingsboard.server.common.data.platform.RegionOverview;
import org.thingsboard.server.common.data.platform.RegionSummary;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.platform.PlatformRegionStatsService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.device.TbDeviceService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;
import org.thingsboard.server.service.security.permission.Operation;

import java.util.List;
import java.util.UUID;

import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_AUTHORITY_PARAGRAPH;

/**
 * Platform level (cross-tenant) device overview and device management.
 * A region is represented by a tenant, so every endpoint requires an explicit tenant id
 * instead of the current user tenant (SYS_ADMIN has no tenant context).
 */
@RestController
@TbCoreComponent
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Slf4j
public class PlatformDeviceController extends BaseController {

    private final PlatformRegionStatsService platformRegionStatsService;
    private final TbDeviceService tbDeviceService;
    private final JwtTokenFactory tokenFactory;

    @Value("${security.user_token_access_enabled:true}")
    private boolean userTokenAccessEnabled;

    @ApiOperation(value = "Get regions overview (getRegionOverviews)",
            notes = "Returns the list of regions (tenants) with device, active and not cleared alarm counters." +
                    SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/regions")
    public List<RegionOverview> getRegionOverviews() {
        return platformRegionStatsService.findRegionOverviews();
    }

    @ApiOperation(value = "Get region summary (getRegionSummary)",
            notes = "Returns device statistics of the given region (tenant), including not cleared alarms by severity." + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/regions/{tenantId}/summary")
    public RegionSummary getRegionSummary(@Parameter(description = "Region (tenant) id", required = true)
                                          @PathVariable UUID tenantId) throws ThingsboardException {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.READ);
        return platformRegionStatsService.findRegionSummary(regionId);
    }

    @ApiOperation(value = "Get region devices (getRegionDevices)",
            notes = "Returns a page of devices of the given region (tenant). " +
                    "Status filter values: ALL, ACTIVE, OFFLINE, ALARM. " +
                    "Supported sort properties: name (default), type, label, active, alarmCount, createdTime. Default order is device name ascending." +
                    SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/regions/{tenantId}/devices")
    public PageData<RegionDeviceInfo> getRegionDevices(@Parameter(description = "Region (tenant) id", required = true)
                                                       @PathVariable UUID tenantId,
                                                       @Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                       @RequestParam int pageSize,
                                                       @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                       @RequestParam int page,
                                                       @Parameter(description = "Case-insensitive 'substring' filter based on device name or label")
                                                       @RequestParam(required = false) String textSearch,
                                                       @Parameter(description = "Status filter: ALL, ACTIVE, OFFLINE, ALARM",
                                                               schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"ALL", "ACTIVE", "OFFLINE", "ALARM"}))
                                                       @RequestParam(required = false, defaultValue = "ALL") String status,
                                                       @Parameter(description = "Sort property", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"name", "type", "label", "active", "alarmCount", "createdTime"}))
                                                       @RequestParam(required = false) String sortProperty,
                                                       @Parameter(description = "Sort order", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"ASC", "DESC"}))
                                                       @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.READ);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return platformRegionStatsService.findRegionDevices(regionId, pageLink, status);
    }

    @ApiOperation(value = "Create device in region (saveRegionDevice)",
            notes = "Creates a device inside the given region (tenant). Device id must not be specified in the request body. " +
                    "Device credentials are created automatically unless the access token is provided." + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/regions/{tenantId}/devices")
    public Device saveRegionDevice(@Parameter(description = "Region (tenant) id", required = true)
                                   @PathVariable UUID tenantId,
                                   @Parameter(description = "A JSON value representing the device.", required = true)
                                   @RequestBody Device device,
                                   @Parameter(description = "Optional device access token")
                                   @RequestParam(required = false) String accessToken) throws Exception {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.WRITE);
        if (device.getId() != null) {
            throw new DataValidationException("Device id must not be specified when creating a device!");
        }
        device.setTenantId(regionId);
        return tbDeviceService.save(device, accessToken, getCurrentUser());
    }

    @ApiOperation(value = "Update device in region (updateRegionDevice)",
            notes = "Updates name, label and type of a device of the given region (tenant). " +
                    "Other device fields (customer assignment, profile, additional info) are preserved. " +
                    "The device must belong to the region." + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PutMapping(value = "/regions/{tenantId}/devices/{deviceId}")
    public Device updateRegionDevice(@Parameter(description = "Region (tenant) id", required = true)
                                     @PathVariable UUID tenantId,
                                     @Parameter(description = "Device id", required = true)
                                     @PathVariable UUID deviceId,
                                     @Parameter(description = "A JSON value representing the device.", required = true)
                                     @RequestBody Device device) throws Exception {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.WRITE);
        Device existing = findRegionDevice(regionId, new DeviceId(deviceId));
        if (device.getId() != null && !device.getId().equals(existing.getId())) {
            throw new DataValidationException("Device id in the request body does not match the device id in the path!");
        }
        // Merge semantics: only name/label/type are editable from the platform page.
        // Other fields (customerId, deviceProfileId, additionalInfo, etc.) are preserved.
        existing.setName(device.getName());
        existing.setLabel(device.getLabel());
        if (StringUtils.hasText(device.getType()) && !device.getType().equals(existing.getType())) {
            existing.setType(device.getType());
            existing.setDeviceProfileId(null);
        }
        return tbDeviceService.save(existing, null, getCurrentUser());
    }

    @ApiOperation(value = "Delete device in region (deleteRegionDevice)",
            notes = "Deletes a device of the given region (tenant). Works exactly like the native delete endpoint: " +
                    "the device row is removed together with its credentials, attributes and relations, and the action is written to the audit log." +
                    SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/regions/{tenantId}/devices/{deviceId}")
    public void deleteRegionDevice(@Parameter(description = "Region (tenant) id", required = true)
                                   @PathVariable UUID tenantId,
                                   @Parameter(description = "Device id", required = true)
                                   @PathVariable UUID deviceId) throws Exception {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.WRITE);
        Device device = findRegionDevice(regionId, new DeviceId(deviceId));
        tbDeviceService.delete(device, getCurrentUser());
    }

    @ApiOperation(value = "Login as region administrator (loginAsRegionAdmin)",
            notes = "Issues an access token of the tenant administrator of the given region (tenant), " +
                    "so that the platform administrator can continue working inside that region with the native UI. " +
                    "Requires security.user_token_access_enabled = true." + SYSTEM_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/regions/{tenantId}/login-as")
    public JwtPair loginAsRegionAdmin(@Parameter(description = "Region (tenant) id", required = true)
                                      @PathVariable UUID tenantId) throws ThingsboardException {
        TenantId regionId = new TenantId(tenantId);
        checkTenantId(regionId, Operation.READ);
        if (!userTokenAccessEnabled) {
            throw new ThingsboardException("Login as user is disabled on this platform",
                    ThingsboardErrorCode.PERMISSION_DENIED);
        }
        PageData<User> admins = userService.findTenantAdmins(regionId, new PageLink(1, 0));
        if (admins.getData() == null || admins.getData().isEmpty()) {
            throw new ThingsboardException("Region [" + tenantId + "] has no tenant administrator",
                    ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        User admin = admins.getData().get(0);
        UserCredentials credentials = userService.findUserCredentialsByUserId(regionId, admin.getId());
        if (credentials == null || !credentials.isEnabled()) {
            throw new ThingsboardException("Tenant administrator of region [" + tenantId + "] is disabled",
                    ThingsboardErrorCode.PERMISSION_DENIED);
        }
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, admin.getEmail());
        SecurityUser securityUser = new SecurityUser(admin, credentials.isEnabled(), principal);
        return tokenFactory.createTokenPair(securityUser);
    }

    private Device findRegionDevice(TenantId regionId, DeviceId deviceId) throws ThingsboardException {
        Device device = deviceService.findDeviceById(regionId, deviceId);
        if (device == null) {
            throw new ThingsboardException(
                    "Device with id [" + deviceId.getId() + "] is not found in region [" + regionId.getId() + "]",
                    ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return device;
    }

}
