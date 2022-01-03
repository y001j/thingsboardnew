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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeInfo;
import org.thingsboard.server.common.data.edge.EdgeSearchQuery;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api")
public class EdgeController extends BaseController {

    public static final String EDGE_ID = "edgeId";

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edges/enabled", method = RequestMethod.GET)
    @ResponseBody
    public boolean isEdgesSupportEnabled() {
        return edgesEnabled;
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/{edgeId}", method = RequestMethod.GET)
    @ResponseBody
    public Edge getEdgeById(@PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.READ);
            if (Authority.CUSTOMER_USER.equals(getCurrentUser().getAuthority())) {
                cleanUpLicenseKey(edge);
            }
            return edge;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/info/{edgeId}", method = RequestMethod.GET)
    @ResponseBody
    public EdgeInfo getEdgeInfoById(@PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            EdgeInfo edgeInfo = checkEdgeInfoId(edgeId, Operation.READ);
            if (Authority.CUSTOMER_USER.equals(getCurrentUser().getAuthority())) {
                cleanUpLicenseKey(edgeInfo);
            }
            return edgeInfo;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge", method = RequestMethod.POST)
    @ResponseBody
    public Edge saveEdge(@RequestBody Edge edge) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            edge.setTenantId(tenantId);
            boolean created = edge.getId() == null;

            RuleChain edgeTemplateRootRuleChain = null;
            if (created) {
                edgeTemplateRootRuleChain = ruleChainService.getEdgeTemplateRootRuleChain(tenantId);
                if (edgeTemplateRootRuleChain == null) {
                    throw new DataValidationException("Root edge rule chain is not available!");
                }
            }

            Operation operation = created ? Operation.CREATE : Operation.WRITE;

            accessControlService.checkPermission(getCurrentUser(), Resource.EDGE, operation,
                    edge.getId(), edge);

            Edge savedEdge = checkNotNull(edgeService.saveEdge(edge, true));

            if (created) {
                ruleChainService.assignRuleChainToEdge(tenantId, edgeTemplateRootRuleChain.getId(), savedEdge.getId());
                edgeNotificationService.setEdgeRootRuleChain(tenantId, savedEdge, edgeTemplateRootRuleChain.getId());
                edgeService.assignDefaultRuleChainsToEdge(tenantId, savedEdge.getId());
            }

            tbClusterService.broadcastEntityStateChangeEvent(savedEdge.getTenantId(), savedEdge.getId(),
                    created ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);

            logEntityAction(savedEdge.getId(), savedEdge, null, created ? ActionType.ADDED : ActionType.UPDATED, null);
            return savedEdge;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.EDGE), edge,
                    null, edge.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteEdge(@PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.DELETE);
            edgeService.deleteEdge(getTenantId(), edgeId);

            tbClusterService.broadcastEntityStateChangeEvent(getTenantId(), edgeId,
                    ComponentLifecycleEvent.DELETED);

            logEntityAction(edgeId, edge,
                    null,
                    ActionType.DELETED, null, strEdgeId);

        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.EDGE),
                    null,
                    null,
                    ActionType.DELETED, e, strEdgeId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edges", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Edge> getEdges(@RequestParam int pageSize,
                                   @RequestParam int page,
                                   @RequestParam(required = false) String textSearch,
                                   @RequestParam(required = false) String sortProperty,
                                   @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            TenantId tenantId = getCurrentUser().getTenantId();
            return checkNotNull(edgeService.findEdgesByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/edge/{edgeId}", method = RequestMethod.POST)
    @ResponseBody
    public Edge assignEdgeToCustomer(@PathVariable("customerId") String strCustomerId,
                                     @PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(EDGE_ID, strEdgeId);
        try {
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);

            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            checkEdgeId(edgeId, Operation.ASSIGN_TO_CUSTOMER);

            Edge savedEdge = checkNotNull(edgeService.assignEdgeToCustomer(getCurrentUser().getTenantId(), edgeId, customerId));

            tbClusterService.broadcastEntityStateChangeEvent(getTenantId(), edgeId,
                    ComponentLifecycleEvent.UPDATED);

            logEntityAction(edgeId, savedEdge,
                    savedEdge.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strEdgeId, strCustomerId, customer.getName());

            sendEntityAssignToCustomerNotificationMsg(savedEdge.getTenantId(), savedEdge.getId(),
                    customerId, EdgeEventActionType.ASSIGNED_TO_CUSTOMER);

            return savedEdge;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.EDGE), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strEdgeId, strCustomerId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/edge/{edgeId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Edge unassignEdgeFromCustomer(@PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.UNASSIGN_FROM_CUSTOMER);
            if (edge.getCustomerId() == null || edge.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
                throw new IncorrectParameterException("Edge isn't assigned to any customer!");
            }
            Customer customer = checkCustomerId(edge.getCustomerId(), Operation.READ);

            Edge savedEdge = checkNotNull(edgeService.unassignEdgeFromCustomer(getCurrentUser().getTenantId(), edgeId));

            tbClusterService.broadcastEntityStateChangeEvent(getTenantId(), edgeId,
                    ComponentLifecycleEvent.UPDATED);

            logEntityAction(edgeId, edge,
                    edge.getCustomerId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strEdgeId, customer.getId().toString(), customer.getName());

            sendEntityAssignToCustomerNotificationMsg(savedEdge.getTenantId(), savedEdge.getId(),
                    customer.getId(), EdgeEventActionType.UNASSIGNED_FROM_CUSTOMER);

            return savedEdge;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.EDGE), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strEdgeId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/edge/{edgeId}", method = RequestMethod.POST)
    @ResponseBody
    public Edge assignEdgeToPublicCustomer(@PathVariable(EDGE_ID) String strEdgeId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.ASSIGN_TO_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(edge.getTenantId());
            Edge savedEdge = checkNotNull(edgeService.assignEdgeToCustomer(getCurrentUser().getTenantId(), edgeId, publicCustomer.getId()));

            tbClusterService.broadcastEntityStateChangeEvent(getTenantId(), edgeId,
                    ComponentLifecycleEvent.UPDATED);

            logEntityAction(edgeId, savedEdge,
                    savedEdge.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strEdgeId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedEdge;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.EDGE), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strEdgeId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/edges", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Edge> getTenantEdges(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length() > 0) {
                return checkNotNull(edgeService.findEdgesByTenantIdAndType(tenantId, type, pageLink));
            } else {
                return checkNotNull(edgeService.findEdgesByTenantId(tenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/edgeInfos", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EdgeInfo> getTenantEdgeInfos(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length() > 0) {
                return checkNotNull(edgeService.findEdgeInfosByTenantIdAndType(tenantId, type, pageLink));
            } else {
                return checkNotNull(edgeService.findEdgeInfosByTenantId(tenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/edges", params = {"edgeName"}, method = RequestMethod.GET)
    @ResponseBody
    public Edge getTenantEdge(@RequestParam String edgeName) throws ThingsboardException {
        try {
            TenantId tenantId = getCurrentUser().getTenantId();
            return checkNotNull(edgeService.findEdgeByTenantIdAndName(tenantId, edgeName));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/{edgeId}/{ruleChainId}/root", method = RequestMethod.POST)
    @ResponseBody
    public Edge setRootRuleChain(@PathVariable(EDGE_ID) String strEdgeId,
                                 @PathVariable("ruleChainId") String strRuleChainId) throws ThingsboardException {
        checkParameter(EDGE_ID, strEdgeId);
        checkParameter("ruleChainId", strRuleChainId);
        try {
            RuleChainId ruleChainId = new RuleChainId(toUUID(strRuleChainId));
            checkRuleChain(ruleChainId, Operation.WRITE);

            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.WRITE);
            accessControlService.checkPermission(getCurrentUser(), Resource.EDGE, Operation.WRITE,
                    edge.getId(), edge);

            Edge updatedEdge = edgeNotificationService.setEdgeRootRuleChain(getTenantId(), edge, ruleChainId);

            tbClusterService.broadcastEntityStateChangeEvent(updatedEdge.getTenantId(), updatedEdge.getId(), ComponentLifecycleEvent.UPDATED);

            logEntityAction(updatedEdge.getId(), updatedEdge, null, ActionType.UPDATED, null);

            return updatedEdge;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.EDGE),
                    null,
                    null,
                    ActionType.UPDATED, e, strEdgeId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/edges", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Edge> getCustomerEdges(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            PageData<Edge> result;
            if (type != null && type.trim().length() > 0) {
                result = edgeService.findEdgesByTenantIdAndCustomerIdAndType(tenantId, customerId, type, pageLink);
            } else {
                result = edgeService.findEdgesByTenantIdAndCustomerId(tenantId, customerId, pageLink);
            }
            if (Authority.CUSTOMER_USER.equals(user.getAuthority())) {
                for (Edge edge : result.getData()) {
                    cleanUpLicenseKey(edge);
                }
            }
            return checkNotNull(result);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/edgeInfos", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EdgeInfo> getCustomerEdgeInfos(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            PageData<EdgeInfo> result;
            if (type != null && type.trim().length() > 0) {
                result = edgeService.findEdgeInfosByTenantIdAndCustomerIdAndType(tenantId, customerId, type, pageLink);
            } else {
                result = edgeService.findEdgeInfosByTenantIdAndCustomerId(tenantId, customerId, pageLink);
            }
            if (Authority.CUSTOMER_USER.equals(user.getAuthority())) {
                for (Edge edge : result.getData()) {
                    cleanUpLicenseKey(edge);
                }
            }
            return checkNotNull(result);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edges", params = {"edgeIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<Edge> getEdgesByIds(
            @RequestParam("edgeIds") String[] strEdgeIds) throws ThingsboardException {
        checkArrayParameter("edgeIds", strEdgeIds);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            CustomerId customerId = user.getCustomerId();
            List<EdgeId> edgeIds = new ArrayList<>();
            for (String strEdgeId : strEdgeIds) {
                edgeIds.add(new EdgeId(toUUID(strEdgeId)));
            }
            ListenableFuture<List<Edge>> edgesFuture;
            if (customerId == null || customerId.isNullUid()) {
                edgesFuture = edgeService.findEdgesByTenantIdAndIdsAsync(tenantId, edgeIds);
            } else {
                edgesFuture = edgeService.findEdgesByTenantIdCustomerIdAndIdsAsync(tenantId, customerId, edgeIds);
            }
            List<Edge> edges = edgesFuture.get();
            if (Authority.CUSTOMER_USER.equals(user.getAuthority())) {
                for (Edge edge : edges) {
                    cleanUpLicenseKey(edge);
                }
            }
            return checkNotNull(edges);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edges", method = RequestMethod.POST)
    @ResponseBody
    public List<Edge> findByQuery(@RequestBody EdgeSearchQuery query) throws ThingsboardException {
        checkNotNull(query);
        checkNotNull(query.getParameters());
        checkNotNull(query.getEdgeTypes());
        checkEntityId(query.getParameters().getEntityId(), Operation.READ);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            List<Edge> edges = checkNotNull(edgeService.findEdgesByQuery(tenantId, query).get());
            edges = edges.stream().filter(edge -> {
                try {
                    accessControlService.checkPermission(user, Resource.EDGE, Operation.READ, edge.getId(), edge);
                    return true;
                } catch (ThingsboardException e) {
                    return false;
                }
            }).collect(Collectors.toList());
            if (Authority.CUSTOMER_USER.equals(user.getAuthority())) {
                for (Edge edge : edges) {
                    cleanUpLicenseKey(edge);
                }
            }
            return edges;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/types", method = RequestMethod.GET)
    @ResponseBody
    public List<EntitySubtype> getEdgeTypes() throws ThingsboardException {
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            ListenableFuture<List<EntitySubtype>> edgeTypes = edgeService.findEdgeTypesByTenantId(tenantId);
            return checkNotNull(edgeTypes.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/sync/{edgeId}", method = RequestMethod.POST)
    public void syncEdge(@PathVariable("edgeId") String strEdgeId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        try {
            if (isEdgesEnabled()) {
                EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
                edgeId = checkNotNull(edgeId);
                SecurityUser user = getCurrentUser();
                TenantId tenantId = user.getTenantId();
                edgeGrpcService.startSyncProcess(tenantId, edgeId);
            } else {
                throw new ThingsboardException("Edges support disabled", ThingsboardErrorCode.GENERAL);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/edge/missingToRelatedRuleChains/{edgeId}", method = RequestMethod.GET)
    @ResponseBody
    public String findMissingToRelatedRuleChains(@PathVariable("edgeId") String strEdgeId) throws ThingsboardException {
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            edgeId = checkNotNull(edgeId);
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            return edgeService.findMissingToRelatedRuleChains(tenantId, edgeId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private void cleanUpLicenseKey(Edge edge) {
        edge.setEdgeLicenseKey(null);
    }

    @RequestMapping(value = "/license/checkInstance", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> checkInstance(@RequestBody JsonNode request) throws ThingsboardException {
        log.debug("Checking instance [{}]", request);
        try {
            return edgeLicenseService.checkInstance(request);
        } catch (Exception e) {
            log.error("Error occurred: [{}]", e.getMessage(), e);
            throw new ThingsboardException(e, ThingsboardErrorCode.SUBSCRIPTION_VIOLATION);
        }
    }

    @RequestMapping(value = "/license/activateInstance", params = {"licenseSecret", "releaseDate"}, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> activateInstance(@RequestParam String licenseSecret,
                                                     @RequestParam String releaseDate) throws ThingsboardException {
        log.debug("Activating instance [{}], [{}]", licenseSecret, releaseDate);
        try {
            return edgeLicenseService.activateInstance(licenseSecret, releaseDate);
        } catch (Exception e) {
            log.error("Error occurred: [{}]", e.getMessage(), e);
            throw new ThingsboardException(e, ThingsboardErrorCode.SUBSCRIPTION_VIOLATION);
        }
    }
}
