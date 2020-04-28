package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.UserAdminService;

@CrossOrigin
@RestController
public class UserManagementController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("User deleted.");
    static final StatusMessage CREATED_MSG = new StatusMessage("User created.");
    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }
    
    @PostMapping("/v3/auth/admin/signIn")
    public JsonNode signInForSuperAdmin() {
        SignIn originSignIn = parseJson(SignIn.class);
        
        // Adjust the sign in so it is always done against the API app.
        SignIn signIn = new SignIn.Builder().withSignIn(originSignIn)
                .withAppId(API_APP_ID).build();        
        
        App app = appService.getApp(signIn.getAppId());
        CriteriaContext context = getCriteriaContext(app.getIdentifier());

        // We do not check consent, but do verify this is an administrator
        UserSession session = authenticationService.signIn(app, context, signIn);

        if (!session.isInRole(SUPERADMIN)) {
            authenticationService.signOut(session);
            throw new UnauthorizedException("Not a superadmin account");
        }
        
        // Now act as if the user is in the app that was requested
        sessionUpdateService.updateStudy(session, originSignIn.getAppId());
        setCookieAndRecordMetrics(session);
        
        return UserSessionInfo.toJSON(session);
    }
    
    /**
     * This turned out to be useful... so useful we're opening it up to all administrative
     * users.
     * 
     * @see org.sagebionetworks.bridge.spring.controllersAuthenticationController#changeStudy 
     */
    @Deprecated
    @PostMapping("/v3/auth/admin/study")
    public JsonNode changeStudyForAdmin() {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        // The only part of this payload we care about is the app property
        SignIn signIn = parseJson(SignIn.class);
        String appId = signIn.getAppId();

        // Verify it's correct
        App app = appService.getApp(appId);
        sessionUpdateService.updateStudy(session, app.getIdentifier());
        
        return UserSessionInfo.toJSON(session);
    }
    
    @PostMapping("/v3/users")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode createUser() {
        UserSession session = getAuthenticatedSession(ADMIN);
        App app = appService.getApp(session.getAppId());

        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = parseJson(node, StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        UserSession userSession = userAdminService.createUser(app, participant, null, false, consent);

        return UserSessionInfo.toJSON(userSession);
    }

    /**
     * Admin api used to create consent/not-consent user for given app
     * nearly identical to createUser() one
     * @param appId
     * @return
     */
    @PostMapping(path = {"/v1/apps/{appId}/users", "/v3/studies/{appId}/users"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createUserWithStudyId(@PathVariable String appId) {
        getAuthenticatedSession(SUPERADMIN);
        App app = appService.getApp(appId);
        
        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = parseJson(node, StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        userAdminService.createUser(app, participant, null, false, consent);

        return CREATED_MSG;
    }

    @DeleteMapping("/v3/users/{userId}")
    public StatusMessage deleteUser(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        App app = appService.getApp(session.getAppId());
        
        userAdminService.deleteUser(app, userId);
        
        return DELETED_MSG;
    }
}
