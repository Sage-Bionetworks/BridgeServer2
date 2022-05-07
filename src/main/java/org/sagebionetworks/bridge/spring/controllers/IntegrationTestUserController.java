package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.IntegrationTestUserService;

/**
 * These endpoints are intended for account creation during integration tests.
 * These endpoints should not be used to create users in the system. Given our
 * security design, we must allow ADMINs to create accounts through this API so
 * we can run integration tests without the SUPERADMIN role, but again, non-Sage
 * admins should not use these APIs to create accounts.
 */
@CrossOrigin
@RestController
public class IntegrationTestUserController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("User deleted.");
    static final StatusMessage CREATED_MSG = new StatusMessage("User created.");
    private static final String CONSENT_FIELD = "consent";

    private IntegrationTestUserService testUserService;
    
    @Autowired
    final void setIntegrationTestUserService(IntegrationTestUserService testUserService) {
        this.testUserService = testUserService;
    }
    
    @PostMapping("/v3/users")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode createUser() {
        UserSession session = getAuthenticatedSession(ADMIN);
        App app = appService.getApp(session.getAppId());

        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = parseJson(node, StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        UserSession userSession = testUserService.createUser(app, participant, null, false, consent);

        return UserSessionInfo.toJSON(userSession);
    }

    @DeleteMapping("/v3/users/{userId}")
    public StatusMessage deleteUser(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        accountService.deleteAccount(AccountId.forId(session.getAppId(), userId));
        return DELETED_MSG;
    }
}
