package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

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
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.ParticipantService;

@CrossOrigin
@RestController
public class OrganizationController extends BaseController {

    private OrganizationService service;
    
    private ParticipantService participantService;
    
    @Autowired
    final void setOrganizationService(OrganizationService service) {
        this.service = service;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @GetMapping("/v1/organizations")
    public PagedResourceList<Organization> getOrganizations(
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(ADMIN, RESEARCHER, DEVELOPER);
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return service.getOrganizations(session.getAppId(), offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v1/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Organization createOrganization() {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        Organization organization = parseJson(Organization.class);
        organization.setAppId(session.getAppId());
        
        return service.createOrganization(organization);
    }
    
    @PostMapping("/v1/organizations/{orgId}")
    public Organization updateOrganization(@PathVariable String orgId) {
        // A study admin caller will also be able to edit some fields of their own organization.
        // The association of accounts to organizations has to be completed first.
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        Organization organization = parseJson(Organization.class);
        organization.setAppId(session.getAppId());
        organization.setIdentifier(orgId);
        
        return service.updateOrganization(organization);
    }
    
    @GetMapping("/v1/organizations/{orgId}")
    public Organization getOrganization(@PathVariable String orgId) {
        // A study admin caller will be able to retrieve their own organization.
        // The association of accounts to organizations has to be completed first.
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        return service.getOrganization(session.getAppId(), orgId);
    }
    
    @DeleteMapping("/v1/organizations/{orgId}")
    public StatusMessage deleteOrganization(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        service.deleteOrganization(session.getAppId(), orgId);
        return new StatusMessage("Organization deleted.");
    }
    
    @PostMapping("/v1/organizations/{orgId}/members")
    public PagedResourceList<AccountSummary> getMembers(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(ADMIN, DEVELOPER, RESEARCHER);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return service.getMembers(session.getAppId(), orgId, search);
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}")
    public StatusMessage addMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        service.addMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User added as a member.");
    }

    @DeleteMapping("/v1/organizations/{orgId}/members/{userId}")
    public StatusMessage removeMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        service.removeMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User removed as a member.");
    }
    
    /**
     * This search allows non-researchers to see the unassigned admin roles in the system.
     * They should also be able to see everyone in their own organization, given the membership
     * APIs.
     */
    @PostMapping("/v1/organizations/nonmembers")
    public PagedResourceList<AccountSummary> getUnassignedAdmins() {
        UserSession session = getAuthenticatedSession(ADMIN, DEVELOPER, RESEARCHER);
        
        AccountSummarySearch initial = parseJson(AccountSummarySearch.class);
        AccountSummarySearch search = new AccountSummarySearch.Builder()
            .copyOf(initial)
            .withAdminOnly(true)
            .withOrgMembership("<none>").build();
        
        App app = appService.getApp(session.getAppId());
        return participantService.getPagedAccountSummaries(app, search);
    }

}
