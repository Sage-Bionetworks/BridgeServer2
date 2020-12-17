package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.IS_SELF_AND_ORG_MEMBER_OR_ORGADMIN;
import static org.sagebionetworks.bridge.BridgeUtils.parseAccountId;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

/**
 * A set of endpoints for creating administrative users (only). If the 
 * account is created with an organizational association, and that 
 * association is not allowed, the caller receives an error. If it is not,
 * then the account can be associated to an organization through the APIs.
 */
@CrossOrigin
@RestController
public class AccountsController extends BaseController  {
    
    private static final Set<String> ACCOUNT_FIELDS = ImmutableSet.of("firstName", 
            "lastName", "synapseUserId", "email", "phone", "attributes", "status", 
            "roles", "dataGroups", "clientData", "languages", "orgMembership", 
            "password");
    
    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    
    @PostMapping("/v1/accounts")
    @ResponseStatus(code = CREATED)
    public IdentifierHolder createAccount() {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
        String orgId = session.getParticipant().getOrgMembership();
        
        // We can deserialize this as a participant record, because StudyParticipant
        // is a superset of Account. That includes password, which we want to expose
        // in the SDK version of Account.
        StudyParticipant participant = parseJson(StudyParticipant.class);
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withOrgMembership(orgId).build();
        
        App app = appService.getApp(session.getAppId());
        return participantService.createParticipant(app, participant, true);
    }
    
    @GetMapping("/v1/accounts/{userId}")
    public Account getAccount(@PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        return verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);
    }
    
    @PostMapping("/v1/accounts/{userId}")
    public StatusMessage updateAccount(@PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        Account account = verifyOrgAdminIsActingOnOrgMember(
                session.getAppId(), orgId, userId);
        App app = appService.getApp(session.getAppId());
        StudyParticipant existing = participantService.getParticipant(app, account, false);

        // Only copy some fields to the existing object. We do it this way in case the 
        // account is also being used as a participant—this way none of those fields will 
        // be erased by an update through this API.
        StudyParticipant updates = parseJson(StudyParticipant.class);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(existing)
                .copyFieldsOf(updates, ACCOUNT_FIELDS)
                .withOrgMembership(existing.getOrgMembership()).build();
        participantService.updateParticipant(app, participant);

        return new StatusMessage("Member updated.");
    }
    
    @DeleteMapping("/v1/accounts/{userId}")
    public StatusMessage deleteAccount(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN, ADMIN);
            
        App app = appService.getApp(session.getAppId());
        StudyParticipant existing = participantService.getParticipant(app, userId, false);
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), existing.getOrgMembership(), userId);
        
        userAdminService.deleteUser(app, userId);
        
        return new StatusMessage("Member account deleted.");
    }
    
    
    @GetMapping(path = { "/v1/accounts/{userId}/requestInfo" }, 
            produces = { APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfo(@PathVariable String userId) 
            throws JsonProcessingException {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        verifyOrgAdminIsActingOnOrgMember(
                session.getAppId(), orgId, userId);
        
        // RequestInfo is accessible because the user is accessible, no reason to 
        // test again.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        }
        return REQUEST_INFO_WRITER.writeValueAsString(requestInfo);
    }
    
    @PostMapping("/v1/accounts/{userId}/requestResetPassword")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage requestResetPassword(@PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.requestResetPassword(app, userId);
        
        return new StatusMessage("Request to reset password sent to user.");
    }
    
    @PostMapping("/v1/accounts/{userId}/resendEmailVerification")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage resendEmailVerification(@PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.EMAIL, userId);
        
        return new StatusMessage("Email verification request has been resent to user.");
    }

    @PostMapping("/v1/accounts/{userId}/resendPhoneVerification")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage resendPhoneVerification(@PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.PHONE, userId);
        
        return new StatusMessage("Phone verification request has been resent to user.");
    }
    
    @PostMapping("/v1/accounts/self/identifiers")
    public JsonNode updateIdentifiers() {
        UserSession session = getAuthenticatedSession();
        
        IdentifierUpdate update = parseJson(IdentifierUpdate.class);
        App app = appService.getApp(session.getAppId());

        CriteriaContext context = getCriteriaContext(session);
        
        StudyParticipant participant = participantService.updateIdentifiers(app, context, update);
        sessionUpdateService.updateParticipant(session, context, participant);
        
        return UserSessionInfo.toJSON(session);
    }

    @PostMapping("/v1/accounts/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String userId,
            @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAdministrativeSession();
        String orgId = session.getParticipant().getOrgMembership();
        
        verifyOrgAdminIsActingOnOrgMember(session.getAppId(), orgId, userId);

        App app = appService.getApp(session.getAppId());
        participantService.signUserOut(app, userId, deleteReauthToken);

        return new StatusMessage("User signed out.");
    }
    
    public Account verifyOrgAdminIsActingOnOrgMember(String appId, String callerOrgId, String userId) {
        if (callerOrgId == null) {
            throw new EntityNotFoundException(Account.class);
        }
        // The caller needs to be an administrator of this organization
        AccountId accountId = parseAccountId(appId, userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (!callerOrgId.equals(account.getOrgMembership())) {
            throw new EntityNotFoundException(Account.class);
        }
        if (!IS_SELF_AND_ORG_MEMBER_OR_ORGADMIN.check(ORG_ID, callerOrgId, USER_ID, account.getId())) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }
}
