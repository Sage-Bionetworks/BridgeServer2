package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.springframework.http.HttpStatus.CREATED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.AuthUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.UserAdminService;

@CrossOrigin
@RestController
public class MembershipController extends BaseController {

    private OrganizationService organizationService;
    
    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/search")
    public PagedResourceList<AccountSummary> getMembers(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return organizationService.getMembers(session.getAppId(), orgId, search);
    }
    
    @PostMapping("/v1/organizations/{orgId}/members")
    public IdentifierHolder createMember(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        App app = appService.getApp(session.getAppId());
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
        participant = new StudyParticipant.Builder().withOrgMembership(orgId).build();
        
        return participantService.createParticipant(app, participant, true);
    }
    
    @GetMapping("/v1/organizations/{orgId}/members/{userId}")
    public StudyParticipant getMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        App app = appService.getApp(session.getAppId());
        
        AuthUtils.checkOrgMember(orgId);
        StudyParticipant participant = participantService.getParticipant(app, userId, false);
        if (!participant.getOrgMembership().equals(orgId)) {
            throw new EntityNotFoundException(Account.class);
        }
        return participant;
    }
    
    @PostMapping("/v1/organizations/members/{userId}")
    public StatusMessage updateMember(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        App app = appService.getApp(session.getAppId());

        StudyParticipant existing = participantService.getParticipant(app, userId, false);
        AuthUtils.checkOrgMember(existing.getOrgMembership());

        // Force userId of the URL
        StudyParticipant participant = parseJson(StudyParticipant.class);

        participant = new StudyParticipant.Builder()
                .copyOf(participant)
                .withId(userId)
                .withOrgMembership(existing.getOrgMembership()).build();
        
        participantService.updateParticipant(app, participant);

        return new StatusMessage("Member updated.");
    }
    
    @DeleteMapping("/v1/organizations/members/{userId}")
    public StatusMessage deleteMember(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        App app = appService.getApp(session.getAppId());
            
        StudyParticipant existing = participantService.getParticipant(app, userId, false);
        AuthUtils.checkOrgMember(existing.getOrgMembership());
        
        userAdminService.deleteUser(app, userId);
        
        return new StatusMessage("Member account deleted.");
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}")
    @ResponseStatus(code = CREATED)
    public StatusMessage addMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        organizationService.addMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User added as a member.");
    }

    @DeleteMapping("/v1/organizations/{orgId}/members/{userId}")
    public StatusMessage removeMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        organizationService.removeMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User removed as a member.");
    }
    
    /**
     * This search allows non-researchers to see the unassigned admin roles in the system.
     * They should also be able to see everyone in their own organization, given the membership
     * APIs.
     */
    @PostMapping("/v1/organizations/nonmembers")
    public PagedResourceList<AccountSummary> getUnassignedAdmins() {
        UserSession session = getAdministrativeSession();
        
        AccountSummarySearch initial = parseJson(AccountSummarySearch.class);
        AccountSummarySearch search = new AccountSummarySearch.Builder()
            .copyOf(initial)
            .withAdminOnly(true)
            .withOrgMembership("<none>").build();
        
        App app = appService.getApp(session.getAppId());
        return participantService.getPagedAccountSummaries(app, search);
    }
}
