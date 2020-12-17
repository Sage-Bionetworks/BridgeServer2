package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.IS_COORD_OR_RESEARCHER;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.EnrollmentService;

/**
 * APIs for a study coordinator to access participants in a study (that they are 
 * associated to through their organization).
 */
@CrossOrigin
@RestController
public class StudyParticipantController extends BaseController {
    
    private static final String NOTIFY_SUCCESS_MESSAGE = "Message has been sent to external notification service.";

    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    private EnrollmentService enrollmentService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Autowired
    final void setEnrollmentService(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/v5/studies/{studyId}/participants/{userId}/enrollments")
    public PagedResourceList<EnrollmentDetail> getEnrollmentsForUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        List<EnrollmentDetail> list = enrollmentService.getEnrollmentsForUser(session.getAppId(), studyId, userId); 
        return new PagedResourceList<>(list, list.size(), true);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/search")
    public PagedResourceList<AccountSummary> searchForAccountSummaries(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        
        search = new AccountSummarySearch.Builder().copyOf(search)
                .withEnrolledInStudyId(studyId).build();
        
        return participantService.getPagedAccountSummaries(app, search);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentifierHolder createParticipant(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        StudyParticipant participant = parseJson(StudyParticipant.class);

        IdentifierHolder keys = participantService.createParticipant(app, participant, true);
        
        Enrollment en = Enrollment.create(session.getAppId(), studyId, keys.getIdentifier());
        en.setConsentRequired(true); // enrolled, but not consented.
        enrollmentService.enroll(en);
        
        return keys;
    }

    @GetMapping(path="/v5/studies/{studyId}/participants/{userId}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipant(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(defaultValue = "true") boolean consents) throws Exception {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);

        App app = appService.getApp(session.getAppId());
        // Do not allow lookup by health code if health code access is disabled. Allow it however
        // if the user is an administrator.
        
        
        if (!session.isInRole(ADMIN) && !app.isHealthCodeExportEnabled()
                && userId.toLowerCase().startsWith("healthcode:")) {
            throw new EntityNotFoundException(Account.class);
        }
        
        StudyParticipant participant = participantService.getParticipant(app, userId, consents);
        
        ObjectWriter writer = (app.isHealthCodeExportEnabled() || session.isInRole(ADMIN)) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }
    
    @GetMapping(path = "/v5/studies/{studyId}/participants/{userId}/requestInfo", produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfo(@PathVariable String studyId, @PathVariable String userId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        // Verify it's in the same app as the researcher.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        } else if (!app.getIdentifier().equals(requestInfo.getAppId())) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }
        return REQUEST_INFO_WRITER.writeValueAsString(requestInfo);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}")
    public StatusMessage updateParticipant(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
 
        // Force userId of the URL
        participant = new StudyParticipant.Builder().copyOf(participant).withId(userId).build();
        
        App app = appService.getApp(session.getAppId());
        participantService.updateParticipant(app, participant);

        return new StatusMessage("Participant updated.");
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        participantService.signUserOut(app, userId, deleteReauthToken);

        return new StatusMessage("User signed out.");
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/requestResetPassword")
    public StatusMessage requestResetPassword(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        participantService.requestResetPassword(app, userId);
        
        return new StatusMessage("Request to reset password sent to user.");
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/resendEmailVerification")
    public StatusMessage resendEmailVerification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.EMAIL, userId);
        
        return new StatusMessage("Email verification request has been resent to user.");
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/resendPhoneVerification")
    public StatusMessage resendPhoneVerification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.PHONE, userId);
        
        return new StatusMessage("Phone verification request has been resent to user.");
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/consents/{guid}/resendConsent")
    public StatusMessage resendConsentAgreement(@PathVariable String studyId, @PathVariable String userId,
            @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        participantService.resendConsentAgreement(app, subpopGuid, userId);
        
        return new StatusMessage("Consent agreement resent to user.");
    }

    @GetMapping("/v5/studies/{studyId}/participants/{userId}/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploads(@PathVariable String studyId,
            @PathVariable String userId, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);

        return participantService.getUploads(app, userId, startTimeDate, endTimeDate, pageSize, offsetKey);
    }

    @GetMapping("/v5/studies/{studyId}/participants/{userId}/notifications")
    public ResourceList<NotificationRegistration> getNotificationRegistrations(@PathVariable String studyId,
            @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        List<NotificationRegistration> registrations = participantService.listRegistrations(app, userId);
        return new ResourceList<>(registrations);
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/sendNotification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendNotification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        NotificationMessage message = parseJson(NotificationMessage.class);
        App app = appService.getApp(session.getAppId());
        Set<String> erroredNotifications = participantService.sendNotification(app, userId, message);
        
        if (erroredNotifications.isEmpty()) {
            return new StatusMessage(NOTIFY_SUCCESS_MESSAGE);                    
        }
        return new StatusMessage(NOTIFY_SUCCESS_MESSAGE + " Some registrations returned errors: "
                + BridgeUtils.COMMA_SPACE_JOINER.join(erroredNotifications) + ".");
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userId}")
    public StatusMessage deleteTestParticipant(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        boolean inStudy = account.getEnrollments().stream()
                .anyMatch(en -> studyId.equals(en.getStudyId()));
        if (!inStudy) {
            throw new EntityNotFoundException(Account.class);
        }
        if (!account.getDataGroups().contains(TEST_USER_GROUP)) {
            throw new UnauthorizedException("Account is not a test account.");
        }
        App app = appService.getApp(session.getAppId());
        userAdminService.deleteUser(app, userId);
        
        return new StatusMessage("User deleted.");
    }    
    
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/activityEvents")
    public ResourceList<ActivityEvent> getActivityEvents(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);

        IS_COORD_OR_RESEARCHER.checkAndThrow(STUDY_ID, studyId);
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        App app = appService.getApp(session.getAppId());
        return new ResourceList<>(participantService.getActivityEvents(app, userId));
    }
    
    /**
     * Verify that the account referenced is enrolled in the target study.
     * 
     * @throws EntityNotFoundException
     */
    void checkAccountInStudy(String appId, String studyId, String userId) {
        List<EnrollmentDetail> enrollments = enrollmentService.getEnrollmentsForUser(appId, studyId, userId);
        boolean matches = enrollments.stream().anyMatch(en -> studyId.equals(en.getStudyId()));
        if (!matches) {
            throw new EntityNotFoundException(Account.class);
        }
    }
}
