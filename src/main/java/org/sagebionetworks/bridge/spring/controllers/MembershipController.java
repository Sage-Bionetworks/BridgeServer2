package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_MEMBERS;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.springframework.http.HttpStatus.CREATED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.OrganizationService;

@CrossOrigin
@RestController
public class MembershipController extends BaseController {

    private OrganizationService organizationService;
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    @PostMapping("/v1/organizations/{orgId}/members")
    public PagedResourceList<AccountSummary> getMembers(@PathVariable String orgId) {
        UserSession session = getAdministrativeSession();
        
        CAN_READ_MEMBERS.checkAndThrow(ORG_ID, orgId);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return organizationService.getMembers(session.getAppId(), orgId, search);
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}")
    @ResponseStatus(code = CREATED)
    public StatusMessage addMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        // organization membership checked in service
        
        organizationService.addMember(session.getAppId(), orgId, userId);
        
        return new StatusMessage("User added as a member.");
    }

    @DeleteMapping("/v1/organizations/{orgId}/members/{userId}")
    public StatusMessage removeMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        // organization membership checked in service
        
        organizationService.removeMember(session.getAppId(), orgId, userId);
        
        return new StatusMessage("User removed as a member.");
    }
    
    /**
     * This search allows administrators to see the unassigned admin roles in the system,
     * in order to assign them to their own organization.
     */
    @PostMapping("/v1/organizations/nonmembers")
    public PagedResourceList<AccountSummary> getUnassignedAdmins() {
        UserSession session = getAdministrativeSession();
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        
        return organizationService.getUnassignedAdmins(session.getAppId(), search);
    }
}
