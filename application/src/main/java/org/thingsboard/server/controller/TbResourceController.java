/**
 * Copyright © 2016-2021 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.lwm2m.LwM2mObject;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.Base64;
import java.util.List;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class TbResourceController extends BaseController {

    public static final String RESOURCE_ID = "resourceId";

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}/download", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        try {
            TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
            TbResource tbResource = checkResourceId(resourceId, Operation.READ);

            ByteArrayResource resource = new ByteArrayResource(Base64.getDecoder().decode(tbResource.getData().getBytes()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + tbResource.getFileName())
                    .header("x-filename", tbResource.getFileName())
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/info/{resourceId}", method = RequestMethod.GET)
    @ResponseBody
    public TbResourceInfo getResourceInfoById(@PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        try {
            TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
            return checkResourceInfoId(resourceId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}", method = RequestMethod.GET)
    @ResponseBody
    public TbResource getResourceById(@PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        try {
            TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
            return checkResourceId(resourceId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource", method = RequestMethod.POST)
    @ResponseBody
    public TbResource saveResource(@RequestBody TbResource resource) throws ThingsboardException {
        boolean created = resource.getId() == null;
        try {
            resource.setTenantId(getTenantId());
            checkEntity(resource.getId(), resource, Resource.TB_RESOURCE);
            TbResource savedResource = checkNotNull(resourceService.saveResource(resource));
            tbClusterService.onResourceChange(savedResource, null);
            logEntityAction(savedResource.getId(), savedResource,
                    null, created ? ActionType.ADDED : ActionType.UPDATED, null);
            return savedResource;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.TB_RESOURCE), resource,
                    null, created ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource", method = RequestMethod.GET)
    @ResponseBody
    public PageData<TbResourceInfo> getResources(@RequestParam int pageSize,
                                                 @RequestParam int page,
                                                 @RequestParam(required = false) String textSearch,
                                                 @RequestParam(required = false) String sortProperty,
                                                 @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (Authority.SYS_ADMIN.equals(getCurrentUser().getAuthority())) {
                return checkNotNull(resourceService.findTenantResourcesByTenantId(getTenantId(), pageLink));
            } else {
                return checkNotNull(resourceService.findAllTenantResourcesByTenantId(getTenantId(), pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/resource/lwm2m/page", method = RequestMethod.GET)
    @ResponseBody
    public List<LwM2mObject> getLwm2mListObjectsPage(@RequestParam int pageSize,
                                                     @RequestParam int page,
                                                     @RequestParam(required = false) String textSearch,
                                                     @RequestParam(required = false) String sortProperty,
                                                     @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = new PageLink(pageSize, page, textSearch);
            return checkNotNull(resourceService.findLwM2mObjectPage(getTenantId(), sortProperty, sortOrder, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/resource/lwm2m", method = RequestMethod.GET)
    @ResponseBody
    public List<LwM2mObject> getLwm2mListObjects(@RequestParam String sortOrder,
                                                 @RequestParam String sortProperty,
                                                 @RequestParam(required = false) String[] objectIds) throws ThingsboardException {
        try {
            return checkNotNull(resourceService.findLwM2mObject(getTenantId(), sortOrder, sortProperty, objectIds));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}", method = RequestMethod.DELETE)
    @ResponseBody
    public void deleteResource(@PathVariable("resourceId") String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        try {
            TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
            TbResource tbResource = checkResourceId(resourceId, Operation.DELETE);
            resourceService.deleteResource(getTenantId(), resourceId);
            tbClusterService.onResourceDeleted(tbResource, null);
            logEntityAction(resourceId, tbResource, null, ActionType.DELETED, null, strResourceId);
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.TB_RESOURCE), null, null, ActionType.DELETED, e, strResourceId);
            throw handleException(e);
        }
    }

}