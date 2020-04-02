package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID_STRING;
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
import org.sagebionetworks.bridge.models.studies.Study;
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
    public JsonNode signInForSuperAdmin() throws Exception {
        SignIn originSignIn = parseJson(SignIn.class);
        
        // Adjust the sign in so it is always done against the API study.
        SignIn signIn = new SignIn.Builder().withSignIn(originSignIn)
                .withStudy(API_STUDY_ID_STRING).build();        
        
        Study study = studyService.getStudy(signIn.getStudyId());
        CriteriaContext context = getCriteriaContext(study.getIdentifier());

        // We do not check consent, but do verify this is an administrator
        UserSession session = authenticationService.signIn(study, context, signIn);

        if (!session.isInRole(SUPERADMIN)) {
            authenticationService.signOut(session);
            throw new UnauthorizedException("Not a superadmin account");
        }
        
        // Now act as if the user is in the study that was requested
        sessionUpdateService.updateStudy(session, originSignIn.getStudyId());
        setCookieAndRecordMetrics(session);
        
        return UserSessionInfo.toJSON(session);
    }
    
    /**
     * This turned out to be useful... so useful we're opening it up to all administrative
     * users.
     * 
     * @see org.sagebionetworks.bridge.spring.controllersAuthenticationController#changeStudy 
     * @throws Exception
     */
    @Deprecated
    @PostMapping("/v3/auth/admin/study")
    public JsonNode changeStudyForAdmin() throws Exception {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        // The only part of this payload we care about is the study property
        SignIn signIn = parseJson(SignIn.class);
        String studyId = signIn.getStudyId();

        // Verify it's correct
        Study study = studyService.getStudy(studyId);
        sessionUpdateService.updateStudy(session, study.getIdentifier());
        
        return UserSessionInfo.toJSON(session);
    }
    
    @PostMapping("/v3/users")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode createUser() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        UserSession userSession = userAdminService.createUser(study, participant, null, false, consent);

        return UserSessionInfo.toJSON(userSession);
    }

    /**
     * admin api used to create consent/not-consent user for given study
     * nearly identical to createUser() one
     * @param studyId
     * @return
     * @throws Exception
     */
    @PostMapping("/v3/studies/{studyId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createUserWithStudyId(@PathVariable String studyId) throws Exception {
        getAuthenticatedSession(SUPERADMIN);
        Study study = studyService.getStudy(studyId);
        
        JsonNode node = parseJson(JsonNode.class);
        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        userAdminService.createUser(study, participant, null, false, consent);

        return CREATED_MSG;
    }

    @DeleteMapping("/v3/users/{userId}")
    public StatusMessage deleteUser(@PathVariable String userId) throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userAdminService.deleteUser(study, userId);
        
        return DELETED_MSG;
    }
}
