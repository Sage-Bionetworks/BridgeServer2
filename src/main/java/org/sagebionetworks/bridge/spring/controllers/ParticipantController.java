package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;

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

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

@CrossOrigin
@RestController
public class ParticipantController extends BaseController {
    
    private static final String NOTIFY_SUCCESS_MESSAGE = "Message has been sent to external notification service.";

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

    /** Researcher API to allow backfill of SMS notification registrations. */
    @PostMapping("/v3/participants/{userId}/notifications/sms")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createSmsRegistration(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.createSmsRegistration(study, userId);
        return new StatusMessage("SMS notification registration created");
    }

    @GetMapping(path="/v3/participants/self", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getSelfParticipant(@RequestParam(defaultValue = "true") boolean consents) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        CriteriaContext context = getCriteriaContext(session);
        StudyParticipant participant = participantService.getSelfParticipant(study, context, consents);
        
        return StudyParticipant.API_NO_HEALTH_CODE_WRITER.writeValueAsString(participant);
    }
    
    @PostMapping("/v3/participants/self")
    public JsonNode updateSelfParticipant() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        // By copying only values that were included in the JSON onto the existing StudyParticipant,
        // we allow clients to only send back partial JSON to update the user. This has been the 
        // usage pattern in prior APIs and it will make refactoring to use this API easier.
        JsonNode node = parseJson(JsonNode.class);
        Set<String> fieldNames = Sets.newHashSet(node.fieldNames());

        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);
        StudyParticipant existing = participantService.getParticipant(study, session.getId(), true);
        StudyParticipant updated = new StudyParticipant.Builder()
                .copyOf(existing)
                .copyFieldsOf(participant, fieldNames)
                // Cannot change sharing if the user is not consented. This method should probably require consent
                // but since historically it did not, we will not change it now.
                .withSharingScope(session.doesConsent() ? participant.getSharingScope() : existing.getSharingScope())
                .withId(session.getId()).build();
        participantService.updateParticipant(study, updated);
        
        RequestContext reqContext = BridgeUtils.getRequestContext();
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(reqContext.getCallerClientInfo())
                .withUserId(session.getId())
                .withUserDataGroups(updated.getDataGroups())
                .withUserSubstudyIds(updated.getSubstudyIds())
                .withStudyIdentifier(session.getStudyIdentifier())
                .build();
        
        sessionUpdateService.updateParticipant(session, context, updated);
        
        return UserSessionInfo.toJSON(session);
    }
    
    @DeleteMapping("/v3/participants/{userId}")
    public StatusMessage deleteTestParticipant(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        if (!participant.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP)) {
            throw new UnauthorizedException("Account is not a test account.");
        }
        userAdminService.deleteUser(study, userId);
        
        return new StatusMessage("User deleted.");
    }

    @GetMapping("/v3/studies/{studyId}/participants/{userId}/activityEvents")
    public ResourceList<ActivityEvent> getActivityEventsForWorker(@PathVariable String studyId,
            @PathVariable String userId) {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);

        return new ResourceList<>(participantService.getActivityEvents(study, userId));
    }
    
    @GetMapping(path = "/v3/studies/{studyId}/participants/{userId}/activities/{activityType}/{referentGuid}", produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getActivityHistoryForWorkerV3(@PathVariable String studyId, @PathVariable String userId,
            @PathVariable String activityType, @PathVariable String referentGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);
        
        return getActivityHistoryV3Internal(study, userId, activityType, referentGuid, scheduledOnStart, scheduledOnEnd,
                offsetKey, pageSize);
    }

    @GetMapping("/v3/studies/{studyId}/participants/{userId}/activities/{activityGuid}")
    public JsonNode getActivityHistoryForWorkerV2(@PathVariable String studyId, @PathVariable String userId,
            @PathVariable String activityGuid, @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize)
            throws Exception {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);
        
        return getActivityHistoryInternalV2(study, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy,
                offsetKey, pageSize);
    }

    @PostMapping("/v3/participants/self/identifiers")
    public JsonNode updateIdentifiers() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        IdentifierUpdate update = parseJson(IdentifierUpdate.class);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        CriteriaContext context = getCriteriaContext(session);
        
        StudyParticipant participant = participantService.updateIdentifiers(study, context, update);
        sessionUpdateService.updateParticipant(session, context, participant);
        
        return UserSessionInfo.toJSON(session);
    }
    
    @Deprecated
    @GetMapping("/v3/participants")
    public JsonNode getParticipants(@RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String emailFilter,
            @RequestParam(required = false) String phoneFilter, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return getParticipantsInternal(study, offsetBy, pageSize, emailFilter, phoneFilter, startDate,
                endDate, startTime, endTime);
    }

    @PostMapping("/v3/participants/search")
    public PagedResourceList<AccountSummary> searchForAccountSummaries() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return participantService.getPagedAccountSummaries(study, search);
    }
    
    @Deprecated
    @GetMapping("/v3/studies/{studyId}/participants")
    public JsonNode getParticipantsForWorker(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String emailFilter, @RequestParam(required = false) String phoneFilter,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime) {
        getAuthenticatedSession(WORKER);
        
        Study study = studyService.getStudy(studyId);
        return getParticipantsInternal(study, offsetBy, pageSize, emailFilter, phoneFilter, startDate, endDate,
                startTime, endTime);
    }

    @PostMapping("/v3/studies/{studyId}/participants/search")
    public PagedResourceList<AccountSummary> searchForAccountSummariesForWorker(@PathVariable String studyId) throws Exception {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return participantService.getPagedAccountSummaries(study, search);
    }

    @PostMapping("/v3/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentifierHolder createParticipant() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
        return participantService.createParticipant(study, participant, true);
    }

    @GetMapping(path="/v3/participants/{userId}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipant(@PathVariable String userId, @RequestParam(defaultValue = "true") boolean consents)
            throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Do not allow lookup by health code if health code access is disabled. Allow it however
        // if the user is an administrator.
        if (!session.isInRole(ADMIN) && !study.isHealthCodeExportEnabled()
                && userId.toLowerCase().startsWith("healthcode:")) {
            throw new EntityNotFoundException(Account.class);
        }
        
        StudyParticipant participant = participantService.getParticipant(study, userId, consents);

        ObjectWriter writer = (study.isHealthCodeExportEnabled()) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }
    
    @GetMapping(path="/v3/studies/{studyId}/participants/{userId}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipantForWorker(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(defaultValue = "true") boolean consents) throws Exception {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);

        StudyParticipant participant = participantService.getParticipant(study, userId, consents);
        
        ObjectWriter writer = StudyParticipant.API_WITH_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }

    @GetMapping("/v3/participants/{userId}/requestInfo")
    public RequestInfo getRequestInfo(@PathVariable String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Verify it's in the same study as the researcher.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        } else if (!study.getStudyIdentifier().equals(requestInfo.getStudyIdentifier())) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }
        return requestInfo;
    }

    @PostMapping("/v3/participants/{userId}")
    public StatusMessage updateParticipant(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = parseJson(StudyParticipant.class);
 
        // Force userId of the URL
        participant = new StudyParticipant.Builder().copyOf(participant).withId(userId).build();
        
        participantService.updateParticipant(study, participant);

        return new StatusMessage("Participant updated.");
    }
    
    @PostMapping("/v3/participants/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String userId, @RequestParam(required = false) boolean deleteReauthToken) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.signUserOut(study, userId, deleteReauthToken);

        return new StatusMessage("User signed out.");
    }

    @PostMapping("/v3/participants/{userId}/requestResetPassword")
    public StatusMessage requestResetPassword(@PathVariable String userId)
            throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.requestResetPassword(study, userId);
        
        return new StatusMessage("Request to reset password sent to user.");
    }

    @GetMapping("/v3/participants/{userId}/activities/{activityGuid}")
    public JsonNode getActivityHistoryV2(@PathVariable String userId, @PathVariable String activityGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize)
            throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return getActivityHistoryInternalV2(study, userId, activityGuid, scheduledOnStart,
            scheduledOnEnd, offsetBy, offsetKey, pageSize);
    }

    @GetMapping(path="/v3/participants/{userId}/activities/{activityType}/{referentGuid}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getActivityHistoryV3(@PathVariable String userId, @PathVariable String activityType,
            @PathVariable String referentGuid, @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return getActivityHistoryV3Internal(study, userId, activityType, referentGuid, scheduledOnStart,
                scheduledOnEnd, offsetKey, pageSize);
    }

    @DeleteMapping("/v3/participants/{userId}/activities")
    public StatusMessage deleteActivities(@PathVariable String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.deleteActivities(study, userId);
        
        return new StatusMessage("Scheduled activities deleted.");
    }

    @PostMapping("/v3/participants/{userId}/resendEmailVerification")
    public StatusMessage resendEmailVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.resendVerification(study, ChannelType.EMAIL, userId);
        
        return new StatusMessage("Email verification request has been resent to user.");
    }

    @PostMapping("/v3/participants/{userId}/resendPhoneVerification")
    public StatusMessage resendPhoneVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.resendVerification(study, ChannelType.PHONE, userId);
        
        return new StatusMessage("Phone verification request has been resent to user.");
    }
    
    @PostMapping("/v3/participants/{userId}/consents/{guid}/resendConsent")
    public StatusMessage resendConsentAgreement(@PathVariable String userId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        participantService.resendConsentAgreement(study, subpopGuid, userId);
        
        return new StatusMessage("Consent agreement resent to user.");
    }

    @PostMapping("/v3/participants/{userId}/consents/withdraw")
    public StatusMessage withdrawFromStudy(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawFromStudy(study, userId, withdrawal, withdrewOn);
        
        return new StatusMessage("User has been withdrawn from the study.");
    }

    @PostMapping("/v3/participants/{userId}/consents/{guid}/withdraw")
    public StatusMessage withdrawConsent(@PathVariable String userId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        participantService.withdrawConsent(study, userId, subpopGuid, withdrawal, withdrewOn);
        
        return new StatusMessage("User has been withdrawn from subpopulation '"+guid+"'.");
    }

    @GetMapping("/v3/participants/{userId}/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploads(@PathVariable String userId,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);

        return participantService.getUploads(study, userId, startTimeDate, endTimeDate, pageSize, offsetKey);
    }

    @GetMapping("/v3/participants/{userId}/notifications")
    public ResourceList<NotificationRegistration> getNotificationRegistrations(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        List<NotificationRegistration> registrations = participantService.listRegistrations(study, userId);
        
        return new ResourceList<>(registrations);
    }

    @PostMapping("/v3/participants/{userId}/sendNotification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendNotification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        NotificationMessage message = parseJson(NotificationMessage.class);
        
        Set<String> erroredNotifications = participantService.sendNotification(study, userId, message);
        
        if (erroredNotifications.isEmpty()) {
            return new StatusMessage(NOTIFY_SUCCESS_MESSAGE);                    
        }
        return new StatusMessage(NOTIFY_SUCCESS_MESSAGE + " Some registrations returned errors: "
                + BridgeUtils.COMMA_SPACE_JOINER.join(erroredNotifications) + ".");
    }

    @GetMapping("/v3/participants/{userId}/activityEvents")
    public ResourceList<ActivityEvent> getActivityEvents(@PathVariable String userId) {
        UserSession researcherSession = getAuthenticatedSession(Roles.RESEARCHER);
        Study study = studyService.getStudy(researcherSession.getStudyIdentifier());

        return new ResourceList<>(participantService.getActivityEvents(study, userId));
    }

    @PostMapping("/v3/studies/{studyId}/participants/{userId}/sendSmsMessage")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendSmsMessageForWorker(@PathVariable String studyId, @PathVariable String userId) {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);
        SmsTemplate template = parseJson(SmsTemplate.class);
        
        participantService.sendSmsMessage(study, userId, template);
        return new StatusMessage("Message sent.");
    }

    private JsonNode getParticipantsInternal(Study study, String offsetByString, String pageSizeString,
            String emailFilter, String phoneFilter, String startDateString, String endDateString,
            String startTimeString, String endTimeString) {
        
        int offsetBy = getIntOrDefault(offsetByString, 0);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);
        
        // For naming consistency, we are changing from the user of startDate/endDate to startTime/endTime
        // for DateTime parameters. Both are accepted by these participant API endpoints (the only places 
        // where this needed to change).
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        if (startTime == null) {
            startTime = getDateTimeOrDefault(startDateString, null);
        }
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        if (endTime == null) {
            endTime = getDateTimeOrDefault(endDateString, null);
        }
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(offsetBy)
                .withPageSize(pageSize)
                .withEmailFilter(emailFilter)
                .withPhoneFilter(phoneFilter)
                .withStartTime(startTime)
                .withEndTime(endTime).build();
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, search);
        
        // Similarly, we will return startTime/endTime in the top-level request parameter properties as 
        // startDate/endDate while transitioning, to maintain backwards compatibility.
        ObjectNode node = MAPPER.valueToTree(page);
        Map<String,Object> rp = page.getRequestParams();
        if (rp.get(START_TIME) != null) {
            node.put(START_DATE, (String)rp.get(START_TIME));    
        }
        if (rp.get(END_TIME) != null) {
            node.put(END_DATE, (String)rp.get(END_TIME));    
        }
        return node;
    }
    
    private JsonNode getActivityHistoryInternalV2(Study study, String userId, String activityGuid,
            String scheduledOnStartString, String scheduledOnEndString, String offsetBy, String offsetKey,
            String pageSizeString) throws Exception {
        if (offsetKey == null) {
            offsetKey = offsetBy;
        }
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(
                study, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);

        // If offsetBy was supplied, we return it as a top-level property of the list for backwards compatibility.
        String json = ScheduledActivity.RESEARCHER_SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
        ObjectNode node = (ObjectNode)MAPPER.readTree(json);
        if (offsetBy != null) {
            node.put(OFFSET_BY, offsetBy);    
        }
        return node;
    }
    
    private String getActivityHistoryV3Internal(Study study, String userId, String activityTypeString,
            String referentGuid, String scheduledOnStartString, String scheduledOnEndString, String offsetKey,
            String pageSizeString) throws Exception {
        
        ActivityType activityType = ActivityType.fromPlural(activityTypeString);
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(study, userId,
                activityType, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
        
        return ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
    }
}
