package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ORG;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_ORG;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.services.OrganizationService;

@CrossOrigin
@RestController
public class OrganizationController extends BaseController {

    private OrganizationService service;
    
    @Autowired
    final void setOrganizationService(OrganizationService service) {
        this.service = service;
    }
    
    @GetMapping("/v1/organizations")
    public PagedResourceList<Organization> getOrganizations(
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAdministrativeSession();
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return service.getOrganizations(session.getAppId(), offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v1/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Organization createOrganization() {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        Organization organization = parseJson(Organization.class);
        organization.setAppId(session.getAppId());
        
        return service.createOrganization(organization);
    }
    
    @PostMapping("/v1/organizations/{orgId}")
    public Organization updateOrganization(@PathVariable String orgId) {
        // A study admin caller will also be able to edit some fields of their own organization.
        // The association of accounts to organizations has to be completed first.
        UserSession session = getAuthenticatedSession(ORG_ADMIN);

        CAN_EDIT_ORG.checkAndThrow(ORG_ID, orgId);

        Organization organization = parseJson(Organization.class);
        organization.setAppId(session.getAppId());
        organization.setIdentifier(orgId);

        return service.updateOrganization(organization);
    }
    
    @GetMapping("/v1/organizations/{orgId}")
    public Organization getOrganization(@PathVariable String orgId) {
        // A study admin caller will be able to retrieve their own organization.
        // The association of accounts to organizations has to be completed first.
        UserSession session = getAdministrativeSession();
        
        CAN_READ_ORG.checkAndThrow(ORG_ID, orgId);
        
        return service.getOrganization(session.getAppId(), orgId);
    }
    
    @DeleteMapping("/v1/organizations/{orgId}")
    public StatusMessage deleteOrganization(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        service.deleteOrganization(session.getAppId(), orgId);
        return new StatusMessage("Organization deleted.");
    }
}
