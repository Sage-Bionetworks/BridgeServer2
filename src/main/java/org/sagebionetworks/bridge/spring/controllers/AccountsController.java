package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ACCOUNTS;
import static org.sagebionetworks.bridge.BridgeUtils.parseAccountId;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

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
import org.sagebionetworks.bridge.services.AccountWorkflowService;
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
    private static final StatusMessage UPDATED_MSG = new StatusMessage("Member updated.");
    private static final StatusMessage DELETED_MSG = new StatusMessage("Member account deleted.");
    private static final StatusMessage RESET_PWD_MSG = new StatusMessage("Request to reset password sent to user.");
    private static final StatusMessage EMAIL_VERIFY_MSG = new StatusMessage("Email verification request has been resent to user.");
    private static final StatusMessage PHONE_VERIFY_MSG = new StatusMessage("Phone verification request has been resent to user.");
    private static final StatusMessage SIGN_OUT_MSG = new StatusMessage("User signed out.");
    private static final Set<String> ACCOUNT_FIELDS = ImmutableSet.of("firstName", "lastName", "synapseUserId", 
            "email", "phone", "attributes", "status", "roles", "dataGroups", "clientData", "languages", 
            "orgMembership", "password", "note", "clientTimeZone");
    
    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    private AccountWorkflowService accountWorkflowService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    
    @PostMapping("/v1/accounts")
    @ResponseStatus(code = CREATED)
    public IdentifierHolder createAccount() {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        String orgId = session.getParticipant().getOrgMembership();
        
        // We can deserialize this as a participant record, because StudyParticipant
        // is a superset of Account. That includes password, which we want to expose
        // in the SDK version of Account.
        StudyParticipant participant = parseJson(StudyParticipant.class);
        // Admins can set someone in any organization, but others must create accounts
        // in their own organization (particularly, org administrators).
        if (!session.isInRole(ImmutableSet.of(ADMIN))) {
            participant = new StudyParticipant.Builder().copyOf(participant)
                    .withOrgMembership(orgId).build();
        }
        App app = appService.getApp(session.getAppId());
        return participantService.createParticipant(app, participant, true);
    }
    
    @GetMapping("/v1/accounts/{userId}")
    public Account getAccount(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        return verifyOrgAdminIsActingOnOrgMember(session, userId);
    }
    
    @PostMapping("/v1/accounts/{userId}")
    public StatusMessage updateAccount(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        Account account = verifyOrgAdminIsActingOnOrgMember(session, userId);
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

        return UPDATED_MSG;
    }
    
    @DeleteMapping("/v1/accounts/{userId}")
    public StatusMessage deleteAccount(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);
        
        App app = appService.getApp(session.getAppId());
        userAdminService.deleteUser(app, userId);
        
        return DELETED_MSG;
    }
    
    
    @GetMapping(path = { "/v1/accounts/{userId}/requestInfo" }, 
            produces = { APPLICATION_JSON_VALUE })
    public String getRequestInfo(@PathVariable String userId) 
            throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);
        
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
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);

        App app = appService.getApp(session.getAppId());
        
        AccountId accountId = AccountId.forId(app.getIdentifier(), userId);
        accountWorkflowService.requestResetPassword(app, true, accountId);
        
        return RESET_PWD_MSG;
    }
    
    @PostMapping("/v1/accounts/{userId}/resendEmailVerification")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage resendEmailVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);

        accountWorkflowService.resendVerification(ChannelType.EMAIL, session.getAppId(), userId);
        
        return EMAIL_VERIFY_MSG;
    }

    @PostMapping("/v1/accounts/{userId}/resendPhoneVerification")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage resendPhoneVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);

        accountWorkflowService.resendVerification(ChannelType.PHONE, session.getAppId(), userId);
        
        return PHONE_VERIFY_MSG;
    }
    
    @PostMapping("/v1/accounts/self/identifiers")
    public JsonNode updateIdentifiers() {
        UserSession session = getAuthenticatedSession();
        
        IdentifierUpdate update = parseJson(IdentifierUpdate.class);
        App app = appService.getApp(session.getAppId());

        CriteriaContext context = getCriteriaContext(session);
        
        StudyParticipant participant = authenticationService.updateIdentifiers(app, context, update);
        sessionUpdateService.updateParticipant(session, context, participant);
        
        return UserSessionInfo.toJSON(session);
    }

    @PostMapping("/v1/accounts/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String userId,
            @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAuthenticatedSession(ORG_ADMIN);
        
        verifyOrgAdminIsActingOnOrgMember(session, userId);

        App app = appService.getApp(session.getAppId());
        authenticationService.signUserOut(app, userId, deleteReauthToken);

        return SIGN_OUT_MSG;
    }
    
    public Account verifyOrgAdminIsActingOnOrgMember(UserSession session, String userIdToken) {
        AccountId accountId = parseAccountId(session.getAppId(), userIdToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        // Check that caller has permissions to edit an administrative account.
        CAN_EDIT_ACCOUNTS.checkAndThrow(ORG_ID, account.getOrgMembership(), USER_ID, account.getId());
        return account;
    }
}
