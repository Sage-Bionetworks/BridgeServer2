package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthUtils.checkSelfResearcherOrAdminAndThrow;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.SmsTemplate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
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
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        
        App app = appService.getApp(session.getAppId());

        participantService.createSmsRegistration(app, userId);
        return new StatusMessage("SMS notification registration created");
    }

    @PostMapping("/v3/participants/self")
    public JsonNode updateSelfParticipant() {
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
        
        // By copying only values that were included in the JSON onto the existing StudyParticipant,
        // we allow clients to only send back partial JSON to update the user. This has been the 
        // usage pattern in prior APIs and it will make refactoring to use this API easier.
        JsonNode node = parseJson(JsonNode.class);
        Set<String> fieldNames = Sets.newHashSet(node.fieldNames());

        StudyParticipant participant = parseJson(node, StudyParticipant.class);
        StudyParticipant existing = participantService.getParticipant(app, session.getId(), false);
        StudyParticipant.Builder builder = new StudyParticipant.Builder()
                .copyOf(existing)
                .copyFieldsOf(participant, fieldNames)
                .withId(session.getId());
        // Only change sharing scope if the user is submitting a value AND they are consented.
        SharingScope sharingScope = participant.getSharingScope();
        if (sharingScope != null && session.doesConsent()) {
            builder.withSharingScope(sharingScope);
        } else {
            builder.withSharingScope(existing.getSharingScope());
        }
        StudyParticipant updated = builder.build();
        
        participantService.updateParticipant(app, updated);
        
        // Construction of the participant record now includes some fields that aren't set directly by the update,
        // such as study associations. Load a completely updated participant record. Do get history 
        // for consent and session.
        StudyParticipant updatedParticipant = participantService.getParticipant(app, session.getId(), true);
        RequestContext reqContext = RequestContext.get();
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(reqContext.getCallerClientInfo())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(updatedParticipant.getDataGroups())
                .withUserStudyIds(updatedParticipant.getStudyIds())
                .withAppId(session.getAppId())
                .build();
        
        sessionUpdateService.updateParticipant(session, context, updatedParticipant);
        
        return UserSessionInfo.toJSON(session);
    }
    
    @DeleteMapping("/v3/participants/{userId}")
    public StatusMessage deleteTestParticipant(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        StudyParticipant participant = participantService.getParticipant(app, userId, false);
        if (!participant.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP)) {
            throw new UnauthorizedException("Account is not a test account.");
        }
        userAdminService.deleteUser(app, userId);
        
        return new StatusMessage("User deleted.");
    }

    @GetMapping(path = { "/v1/apps/{appId}/participants/{userId}/activityEvents",
            "/v3/studies/{appId}/participants/{userId}/activityEvents" })
    public ResourceList<ActivityEvent> getActivityEventsForWorker(@PathVariable String appId,
            @PathVariable String userId) {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);

        return new ResourceList<>(participantService.getActivityEvents(app, userId));
    }
    
    @GetMapping(path = { "/v1/apps/{appId}/participants/{userId}/activities/{activityType}/{referentGuid}",
            "/v3/studies/{appId}/participants/{userId}/activities/{activityType}/{referentGuid}" }, produces = {
                    APPLICATION_JSON_UTF8_VALUE })
    public String getActivityHistoryForWorkerV3(@PathVariable String appId, @PathVariable String userId,
            @PathVariable String activityType, @PathVariable String referentGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);
        
        return getActivityHistoryV3Internal(app, userId, activityType, referentGuid, scheduledOnStart, scheduledOnEnd,
                offsetKey, pageSize);
    }

    @GetMapping(path = {"/v1/apps/{appId}/participants/{userId}/activities/{activityGuid}",
            "/v3/studies/{appId}/participants/{userId}/activities/{activityGuid}"})
    public JsonNode getActivityHistoryForWorkerV2(@PathVariable String appId, @PathVariable String userId,
            @PathVariable String activityGuid, @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize)
            throws Exception {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);
        
        return getActivityHistoryInternalV2(app, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy,
                offsetKey, pageSize);
    }

    @PostMapping("/v3/participants/self/identifiers")
    public JsonNode updateIdentifiers() {
        UserSession session = getAuthenticatedSession();
        
        IdentifierUpdate update = parseJson(IdentifierUpdate.class);
        App app = appService.getApp(session.getAppId());

        CriteriaContext context = getCriteriaContext(session);
        
        StudyParticipant participant = participantService.updateIdentifiers(app, context, update);
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
        App app = appService.getApp(session.getAppId());
        
        return getParticipantsInternal(app, offsetBy, pageSize, emailFilter, phoneFilter, startDate,
                endDate, startTime, endTime);
    }

    @PostMapping("/v3/participants/search")
    public PagedResourceList<AccountSummary> searchForAccountSummaries() {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        App app = appService.getApp(session.getAppId());
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return participantService.getPagedAccountSummaries(app, search);
    }
    
    @Deprecated
    @GetMapping(path = {"/v1/apps/{appId}/participants", "/v3/studies/{appId}/participants"})
    public JsonNode getParticipantsForWorker(@PathVariable String appId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String emailFilter, @RequestParam(required = false) String phoneFilter,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime) {
        getAuthenticatedSession(WORKER);
        
        App app = appService.getApp(appId);
        return getParticipantsInternal(app, offsetBy, pageSize, emailFilter, phoneFilter, startDate, endDate,
                startTime, endTime);
    }

    @PostMapping(path = {"/v1/apps/{appId}/participants/search", "/v3/studies/{appId}/participants/search"})
    public PagedResourceList<AccountSummary> searchForAccountSummariesForWorker(@PathVariable String appId) {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);
        
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        return participantService.getPagedAccountSummaries(app, search);
    }

    @PostMapping("/v3/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentifierHolder createParticipant() {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        App app = appService.getApp(session.getAppId());
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
        return participantService.createParticipant(app, participant, true);
    }

    @GetMapping(path="/v3/participants/self", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getSelfParticipant(@RequestParam(defaultValue = "true") boolean consents) throws Exception {
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
        
        CriteriaContext context = getCriteriaContext(session);
        StudyParticipant participant = participantService.getSelfParticipant(app, context, consents);
        
        // Return the health code if this is an administrative account. This is because developers 
        // should call this method to retrieve their own account.
        ObjectWriter writer = (session.isInRole(ADMINISTRATIVE_ROLES)) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }

    @GetMapping(path="/v3/participants/{userId}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipant(@PathVariable String userId, @RequestParam(defaultValue = "true") boolean consents)
            throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);

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
    
    @GetMapping(path= {"/v1/apps/{appId}/participants/{userId}",
            "/v3/studies/{appId}/participants/{userId}"}, produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipantForWorker(@PathVariable String appId, @PathVariable String userId,
            @RequestParam(defaultValue = "true") boolean consents) throws Exception {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);

        StudyParticipant participant = participantService.getParticipant(app, userId, consents);
        
        ObjectWriter writer = StudyParticipant.API_WITH_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }
    
    @GetMapping(path = {"/v1/apps/{appId}/participants/{userId}/requestInfo",
            "/v3/studies/{appId}/participants/{userId}/requestInfo"}, produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfoForWorker(@PathVariable String appId, @PathVariable String userId)
            throws JsonProcessingException {
        getAuthenticatedSession(WORKER);

        // Verify it's in the same app as the researcher.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        } else if (!appId.equals(requestInfo.getAppId())) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }
        return REQUEST_INFO_WRITER.writeValueAsString(requestInfo);
    }

    @GetMapping(path = "/v3/participants/{userId}/requestInfo", produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfo(@PathVariable String userId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
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
    
    @PostMapping("/v3/participants/{userId}")
    public StatusMessage updateParticipant(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        StudyParticipant participant = parseJson(StudyParticipant.class);
 
        // Force userId of the URL
        participant = new StudyParticipant.Builder().copyOf(participant).withId(userId).build();
        
        participantService.updateParticipant(app, participant);

        return new StatusMessage("Participant updated.");
    }
    
    @PostMapping("/v3/participants/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String userId, @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        participantService.signUserOut(app, userId, deleteReauthToken);

        return new StatusMessage("User signed out.");
    }

    @PostMapping("/v3/participants/{userId}/requestResetPassword")
    public StatusMessage requestResetPassword(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        participantService.requestResetPassword(app, userId);
        
        return new StatusMessage("Request to reset password sent to user.");
    }

    @GetMapping("/v3/participants/{userId}/activities/{activityGuid}")
    public JsonNode getActivityHistoryV2(@PathVariable String userId, @PathVariable String activityGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize)
            throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        return getActivityHistoryInternalV2(app, userId, activityGuid, scheduledOnStart,
            scheduledOnEnd, offsetBy, offsetKey, pageSize);
    }

    @GetMapping(path="/v3/participants/{userId}/activities/{activityType}/{referentGuid}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getActivityHistoryV3(@PathVariable String userId, @PathVariable String activityType,
            @PathVariable String referentGuid, @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        return getActivityHistoryV3Internal(app, userId, activityType, referentGuid, scheduledOnStart,
                scheduledOnEnd, offsetKey, pageSize);
    }

    @DeleteMapping("/v3/participants/{userId}/activities")
    public StatusMessage deleteActivities(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        participantService.deleteActivities(app, userId);
        
        return new StatusMessage("Scheduled activities deleted.");
    }

    @PostMapping("/v3/participants/{userId}/resendEmailVerification")
    public StatusMessage resendEmailVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        participantService.resendVerification(app, ChannelType.EMAIL, userId);
        
        return new StatusMessage("Email verification request has been resent to user.");
    }

    @PostMapping("/v3/participants/{userId}/resendPhoneVerification")
    public StatusMessage resendPhoneVerification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());

        participantService.resendVerification(app, ChannelType.PHONE, userId);
        
        return new StatusMessage("Phone verification request has been resent to user.");
    }
    
    @PostMapping("/v3/participants/{userId}/consents/{guid}/resendConsent")
    public StatusMessage resendConsentAgreement(@PathVariable String userId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        participantService.resendConsentAgreement(app, subpopGuid, userId);
        
        return new StatusMessage("Consent agreement resent to user.");
    }

    @PostMapping("/v3/participants/{userId}/consents/withdraw")
    public StatusMessage withdrawFromApp(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawFromApp(app, userId, withdrawal, withdrewOn);
        
        return new StatusMessage("User has been withdrawn from one or more studies in the app.");
    }

    @PostMapping("/v3/participants/{userId}/consents/{guid}/withdraw")
    public StatusMessage withdrawConsent(@PathVariable String userId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        participantService.withdrawConsent(app, userId, subpopGuid, withdrawal, withdrewOn);
        
        return new StatusMessage("User has been withdrawn from subpopulation '"+guid+"'.");
    }

    @GetMapping("/v3/participants/{userId}/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploads(@PathVariable String userId,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);

        return participantService.getUploads(app, userId, startTimeDate, endTimeDate, pageSize, offsetKey);
    }

    @GetMapping("/v3/participants/{userId}/notifications")
    public ResourceList<NotificationRegistration> getNotificationRegistrations(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        List<NotificationRegistration> registrations = participantService.listRegistrations(app, userId);
        
        return new ResourceList<>(registrations);
    }

    @PostMapping("/v3/participants/{userId}/sendNotification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendNotification(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(session.getAppId());
        
        NotificationMessage message = parseJson(NotificationMessage.class);
        
        Set<String> erroredNotifications = participantService.sendNotification(app, userId, message);
        
        if (erroredNotifications.isEmpty()) {
            return new StatusMessage(NOTIFY_SUCCESS_MESSAGE);                    
        }
        return new StatusMessage(NOTIFY_SUCCESS_MESSAGE + " Some registrations returned errors: "
                + BridgeUtils.COMMA_SPACE_JOINER.join(erroredNotifications) + ".");
    }

    @GetMapping("/v3/participants/{userId}/activityEvents")
    public ResourceList<ActivityEvent> getActivityEvents(@PathVariable String userId) {
        UserSession researcherSession = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        checkSelfResearcherOrAdminAndThrow(null, userId);
        App app = appService.getApp(researcherSession.getAppId());

        return new ResourceList<>(participantService.getActivityEvents(app, userId));
    }

    @PostMapping(path = {"/v1/apps/{appId}/participants/{userId}/sendSmsMessage",
            "/v3/studies/{appId}/participants/{userId}/sendSmsMessage"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendSmsMessageForWorker(@PathVariable String appId, @PathVariable String userId) {
        getAuthenticatedSession(WORKER);
        App app = appService.getApp(appId);
        SmsTemplate template = parseJson(SmsTemplate.class);
        
        participantService.sendSmsMessage(app, userId, template);
        return new StatusMessage("Message sent.");
    }
    
    private JsonNode getParticipantsInternal(App app, String offsetByString, String pageSizeString,
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
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(app, search);
        
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
    
    private JsonNode getActivityHistoryInternalV2(App app, String userId, String activityGuid,
            String scheduledOnStartString, String scheduledOnEndString, String offsetBy, String offsetKey,
            String pageSizeString) throws Exception {
        if (offsetKey == null) {
            offsetKey = offsetBy;
        }
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(
                app, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);

        // If offsetBy was supplied, we return it as a top-level property of the list for backwards compatibility.
        String json = ScheduledActivity.RESEARCHER_SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
        ObjectNode node = (ObjectNode)MAPPER.readTree(json);
        if (offsetBy != null) {
            node.put(OFFSET_BY, offsetBy);    
        }
        return node;
    }
    
    private String getActivityHistoryV3Internal(App app, String userId, String activityTypeString,
            String referentGuid, String scheduledOnStartString, String scheduledOnEndString, String offsetKey,
            String pageSizeString) throws Exception {
        
        ActivityType activityType = ActivityType.fromPlural(activityTypeString);
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(app, userId,
                activityType, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
        
        return ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
    }
}
