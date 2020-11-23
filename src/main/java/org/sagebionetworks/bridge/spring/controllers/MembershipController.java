package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.parseAccountId;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.AuthUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
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
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

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
        // should this just be scoped to any administrative user?
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        AuthUtils.checkOrgMember(orgId);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return organizationService.getMembers(session.getAppId(), orgId, search);
    }
    
    @PostMapping("/v1/organizations/{orgId}/members")
    public IdentifierHolder createMember(@PathVariable String orgId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        AuthUtils.checkOrgMember(orgId);
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withOrgMembership(orgId).build();
        
        App app = appService.getApp(session.getAppId());
        return participantService.createParticipant(app, participant, true);
    }
    
    @GetMapping("/v1/organizations/{orgId}/members/{userId}")
    public StudyParticipant getMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        Account account = verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);
        
        App app = appService.getApp(session.getAppId());
        StudyParticipant participant = participantService.getParticipant(app, account, false);

        return participant;
    }
    
    @PostMapping("/v1/organizations/members/{userId}")
    public StatusMessage updateMember(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        Account account = verifyOrgAdminIsActingOnOrgMember(session.getAppId(),
                session.getParticipant().getOrgMembership(), userId);
        App app = appService.getApp(session.getAppId());
        StudyParticipant existing = participantService.getParticipant(app, account, false);

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
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), existing.getOrgMembership(), userId);
        
        userAdminService.deleteUser(app, userId);
        
        return new StatusMessage("Member account deleted.");
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}")
    @ResponseStatus(code = CREATED)
    public StatusMessage addMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        // organization membership checked in service
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        organizationService.addMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User added as a member.");
    }

    @DeleteMapping("/v1/organizations/{orgId}/members/{userId}")
    public StatusMessage removeMember(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        // organization membership checked in service
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        organizationService.removeMember(session.getAppId(), orgId, accountId);
        
        return new StatusMessage("User removed as a member.");
    }
    
    /**
     * This search allows administrators to see the unassigned admin roles in the system,
     * in order to assign them to their own organization.
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
    
    @GetMapping(path = { "/v1/organizations/{orgId}/members/{userId}/requestInfo" }, 
            produces = { APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfo(@PathVariable String orgId, @PathVariable String userId) 
            throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);
        
        // RequestInfo is accessible because the user is accessible, no reason to 
        // test again.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        }
        return REQUEST_INFO_WRITER.writeValueAsString(requestInfo);
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}/requestResetPassword")
    public StatusMessage requestResetPassword(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.requestResetPassword(app, userId);
        
        return new StatusMessage("Request to reset password sent to user.");
    }
    
    @PostMapping("/v1/organizations/{orgId}/members/{userId}/resendEmailVerification")
    public StatusMessage resendEmailVerification(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.EMAIL, userId);
        
        return new StatusMessage("Email verification request has been resent to user.");
    }

    @PostMapping("/v1/organizations/{orgId}/members/{userId}/resendPhoneVerification")
    public StatusMessage resendPhoneVerification(@PathVariable String orgId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);

        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.PHONE, userId);
        
        return new StatusMessage("Phone verification request has been resent to user.");
    }
      
    @PostMapping("/v1/organizations/{orgId}/members/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String orgId, @PathVariable String userId,
            @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.signUserOut(app, userId, deleteReauthToken);

        return new StatusMessage("User signed out.");
    }

    protected Account verifyOrgAdminIsActingOnOrgMember(String appId, String orgId, String userId) {
        // The caller needs to be an administrator of this organization
        AuthUtils.checkOrgAdmin(orgId);
        
        // The account (if it exists) must be in the organization. Return account for 
        // methods that need to load a StudyParticipant (don't load account twice).
        Account account = accountService.getAccount( parseAccountId(appId, userId) );
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (!orgId.equals(account.getOrgMembership())) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }
}
