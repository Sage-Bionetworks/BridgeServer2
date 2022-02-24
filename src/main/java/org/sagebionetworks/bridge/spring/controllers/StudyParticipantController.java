package org.sagebionetworks.bridge.spring.controllers;

import static java.lang.Boolean.TRUE;
import static org.apache.http.HttpHeaders.IF_MODIFIED_SINCE;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EXPORT_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANT_REPORTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.participantEligibleForDeletion;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.cache.CacheKey.scheduleModificationTimestamp;
import static org.sagebionetworks.bridge.models.RequestInfo.REQUEST_INFO_WRITER;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.reports.ReportType.PARTICIPANT;
import static org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler.INSTANCE;
import static org.sagebionetworks.bridge.models.sms.SmsType.PROMOTIONAL;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.sagebionetworks.bridge.models.ParticipantRosterRequest;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule.ParticipantSchedule;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.Schedule2Service;
import org.sagebionetworks.bridge.services.StudyActivityEventService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.spring.util.EtagSupport;
import org.sagebionetworks.bridge.spring.util.EtagCacheKey;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.EnrollmentService;

/**
 * APIs for a study coordinator to access participants in a study (that they are 
 * associated to through their organization).
 */
@CrossOrigin
@RestController
public class StudyParticipantController extends BaseController {
    static final StatusMessage REPORT_RECORD_DELETED_MSG = new StatusMessage("Report record deleted.");
    static final StatusMessage UPDATE_MSG = new StatusMessage("Participant updated.");
    static final StatusMessage SIGN_OUT_MSG = new StatusMessage("User signed out.");
    static final StatusMessage RESET_PWD_MSG = new StatusMessage("Request to reset password sent to user.");
    static final StatusMessage EMAIL_VERIFY_MSG = new StatusMessage("Email verification request has been resent to user.");
    static final StatusMessage PHONE_VERIFY_MSG = new StatusMessage("Phone verification request has been resent to user.");
    static final StatusMessage CONSENT_RESENT_MSG = new StatusMessage("Consent agreement resent to user.");
    static final StatusMessage DELETE_MSG = new StatusMessage("User deleted.");
    static final StatusMessage NOTIFY_SUCCESS_MSG = new StatusMessage("Message has been sent to external notification service.");
    static final StatusMessage EVENT_RECORDED_MSG = new StatusMessage("Event recorded.");
    static final StatusMessage EVENT_DELETED_MSG = new StatusMessage("Event deleted.");
    static final StatusMessage PREPARING_ROSTER_MSG = new StatusMessage("Preparing participant roster.");
    static final StatusMessage INSTALL_LINK_SEND_MSG = new StatusMessage("Install instructions sent to participant.");
    static final StatusMessage REPORT_DELETED_MSG = new StatusMessage("Report deleted.");
    static final StatusMessage REPORT_SAVED_MSG = new StatusMessage("Participant report saved.");
    static final StatusMessage REPORT_INDEX_DELETED_MSG = new StatusMessage("Participant report index deleted.");

    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    private EnrollmentService enrollmentService;
    
    private StudyActivityEventService studyActivityEventService;
    
    private StudyService studyService;
    
    private Schedule2Service scheduleService;

    private ReportService reportService;
    
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
    
    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSchedule2Service(Schedule2Service scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    @Autowired
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    DateTime getDateTime() {
        return DateTime.now();
    }
    
    @EtagSupport({
        // Most recent modification to the schedule
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"})
    })
    @GetMapping("/v5/studies/{studyId}/participants/self/timeline")
    public ResponseEntity<Timeline> getTimelineForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new UnauthorizedException("Caller is not enrolled in study '" + studyId + "'");
        }

        DateTime timelineRequestedOn = getDateTime();        
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withTimelineAccessedOn(timelineRequestedOn).build();
        requestInfoService.updateRequestInfo(requestInfo);
        
        Study study = studyService.getStudy(session.getAppId(), studyId, true);
        DateTime modifiedSince = modifiedSinceHeader();
        DateTime modifiedOn = modifiedOn(session.getAppId(), studyId);
        
        if (isUpToDate(modifiedSince, modifiedOn)) {
            return new ResponseEntity<>(NOT_MODIFIED);
        }
        Schedule2 schedule = scheduleService.getScheduleForStudy(session.getAppId(), study)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        cacheProvider.setObject(scheduleModificationTimestamp(session.getAppId(), studyId), schedule.getModifiedOn().toString());
        
        studyActivityEventService.publishEvent(new StudyActivityEvent.Builder()
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(session.getId())
                .withObjectType(TIMELINE_RETRIEVED)
                .withTimestamp(timelineRequestedOn).build(), false, true);
        
        return new ResponseEntity<>(INSTANCE.calculateTimeline(schedule), OK);
    }
    
    private DateTime modifiedSinceHeader() {
        return getDateTimeOrDefault(request().getHeader(IF_MODIFIED_SINCE), null);
    }

    private DateTime modifiedOn(String appId, String studyId) {
        return getDateTimeOrDefault(cacheProvider.getObject(
                scheduleModificationTimestamp(appId, studyId), String.class), null);
    }
    
    private boolean isUpToDate(DateTime modifiedSince, DateTime modifiedOn) {
        return modifiedSince != null && modifiedOn != null && modifiedSince.isAfter(modifiedOn);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/emailRoster")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage requestParticipantRoster(@PathVariable String studyId) throws JsonProcessingException {
        UserSession session = getAdministrativeSession();

        CAN_EXPORT_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);

        App app = appService.getApp(session.getAppId());
        
        ParticipantRosterRequest request = parseJson(ParticipantRosterRequest.class);
        ParticipantRosterRequest finalRequest = new ParticipantRosterRequest.Builder()
                .withStudyId(studyId)
                .withPassword(request.getPassword()).build();

        participantService.requestParticipantRoster(app, session.getId(), finalRequest);

        return PREPARING_ROSTER_MSG;
    }
    
    @EtagSupport({
        // Most recent modification to the schedule
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"})
    })
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/timeline")
    public Timeline getTimelineForUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        // Until protocols with study arms are implemented, all participants in 
        // a study will get the same schedule.
        Study study = studyService.getStudy(session.getAppId(), studyId, true);
        if (study.getScheduleGuid() == null) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        return scheduleService.getTimelineForSchedule(session.getAppId(), study.getScheduleGuid());
    }
    
    @EtagSupport({
        // Most recent modification to the schedule
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"}),
        // Most recent modification to the participant’s collection of events
        @EtagCacheKey(model=StudyActivityEvent.class, keys={"userId"}),
        // Most recent modification to the participant’s time zone
        @EtagCacheKey(model=DateTimeZone.class, keys={"userId"})
    })
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/schedule")
    public ParticipantSchedule getParticipantScheduleForUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        return scheduleService.getParticipantSchedule(session.getAppId(), studyId, account);
    }
    
    @EtagSupport({
        // Most recent modification to the schedule
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"}),
        // Most recent modification to the participant’s collection of events
        @EtagCacheKey(model=StudyActivityEvent.class, keys={"userId"}),
        // Most recent modification to the participant’s time zone
        @EtagCacheKey(model=DateTimeZone.class, keys={"userId"})
    })
    @GetMapping("/v5/studies/{studyId}/participants/self/schedule")
    public ParticipantSchedule getParticipantScheduleForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        // This produces the desired error (unauthorized rather than 404)
        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new UnauthorizedException("Caller is not enrolled in study '" + studyId + "'");
        }
        AccountId accountId = AccountId.forId(session.getAppId(), session.getId());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        // Even if the call fails, we want to know they tried it
        DateTime timelineRequestedOn = getDateTime();
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withTimelineAccessedOn(timelineRequestedOn).build();
        requestInfoService.updateRequestInfo(requestInfo);
        
        ParticipantSchedule schedule = scheduleService.getParticipantSchedule(session.getAppId(), studyId, account);

        studyActivityEventService.publishEvent(new StudyActivityEvent.Builder()
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(session.getId())
                .withObjectType(TIMELINE_RETRIEVED)
                .withTimestamp(timelineRequestedOn).build(), false, true);

        return schedule;
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/enrollments")
    public PagedResourceList<EnrollmentDetail> getEnrollmentsForUser(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        List<EnrollmentDetail> list = enrollmentService.getEnrollmentsForUser(session.getAppId(), studyId, account.getId()); 
        return new PagedResourceList<>(list, list.size(), true);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/search")
    public PagedResourceList<AccountSummary> searchForAccountSummaries(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        AccountSummarySearch search = parseJson(AccountSummarySearch.class);
        
        search = search.toBuilder().withEnrolledInStudyId(studyId).build();
        
        return participantService.getPagedAccountSummaries(app, search);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentifierHolder createParticipant(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        StudyParticipant participant = parseJson(StudyParticipant.class);

        IdentifierHolder keys = participantService.createParticipant(app, participant, true);
        
        Enrollment en = Enrollment.create(session.getAppId(), studyId, keys.getIdentifier());
        en.setConsentRequired(true); // enrolled, but not consented.
        enrollmentService.enroll(en);
        
        return keys;
    }

    @GetMapping(path="/v5/studies/{studyId}/participants/{userId}", 
            produces={APPLICATION_JSON_UTF8_VALUE})
    public String getParticipant(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(defaultValue = "true") boolean consents) throws Exception {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);

        App app = appService.getApp(session.getAppId());

        // Do not allow lookup by health code if health code access is disabled. Allow it however
        // if the user is an administrator.
        if (!app.isHealthCodeExportEnabled() && !session.isInRole(SUPERADMIN) 
                && userId.toLowerCase().startsWith("healthcode:")) {
            throw new EntityNotFoundException(Account.class);
        }
        
        StudyParticipant participant = participantService.getParticipant(app, account, consents);
        
        ObjectWriter writer = (app.isHealthCodeExportEnabled() || session.isInRole(SUPERADMIN)) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        return writer.writeValueAsString(participant);
    }
    
    @GetMapping(path = "/v5/studies/{studyId}/participants/{userId}/requestInfo", produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getRequestInfo(@PathVariable String studyId, @PathVariable String userId) throws JsonProcessingException {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        // Verify it's in the same app as the researcher.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(account.getId());
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        }
        return REQUEST_INFO_WRITER.writeValueAsString(requestInfo);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}")
    public StatusMessage updateParticipant(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        StudyParticipant participant = parseJson(StudyParticipant.class);
 
        // Force userId of the URL
        participant = new StudyParticipant.Builder().copyOf(participant).withId(account.getId()).build();
        
        App app = appService.getApp(session.getAppId());
        participantService.updateParticipant(app, participant);

        return UPDATE_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/signOut")
    public StatusMessage signOut(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(required = false) boolean deleteReauthToken) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        participantService.signUserOut(app, account.getId(), deleteReauthToken);

        return SIGN_OUT_MSG;
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/requestResetPassword")
    public StatusMessage requestResetPassword(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        participantService.requestResetPassword(app, account.getId());
        
        return RESET_PWD_MSG;
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/resendEmailVerification")
    public StatusMessage resendEmailVerification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.EMAIL, account.getId());
        
        return EMAIL_VERIFY_MSG;
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/resendPhoneVerification")
    public StatusMessage resendPhoneVerification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        participantService.resendVerification(app, ChannelType.PHONE, account.getId());
        
        return PHONE_VERIFY_MSG;
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/consents/{guid}/resendConsent")
    public StatusMessage resendConsentAgreement(@PathVariable String studyId, @PathVariable String userId,
            @PathVariable String guid) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        participantService.resendConsentAgreement(app, subpopGuid, account.getId());
        
        return CONSENT_RESENT_MSG;
    }

    @GetMapping("/v5/studies/{studyId}/participants/{userId}/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploads(@PathVariable String studyId,
            @PathVariable String userId, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String offsetKey) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);

        return participantService.getUploads(app, account.getId(), startTimeDate, endTimeDate, pageSize, offsetKey);
    }

    @GetMapping("/v5/studies/{studyId}/participants/{userId}/notifications")
    public ResourceList<NotificationRegistration> getNotificationRegistrations(@PathVariable String studyId,
            @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        List<NotificationRegistration> registrations = participantService.listRegistrations(app, account.getId());
        return new ResourceList<>(registrations);
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/sendNotification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendNotification(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        NotificationMessage message = parseJson(NotificationMessage.class);
        App app = appService.getApp(session.getAppId());
        Set<String> erroredNotifications = participantService.sendNotification(app, account.getId(), message);
        
        if (erroredNotifications.isEmpty()) {
            return NOTIFY_SUCCESS_MSG;                    
        }
        return new StatusMessage(NOTIFY_SUCCESS_MSG.getMessage() + " Some registrations returned errors: "
                + BridgeUtils.COMMA_SPACE_JOINER.join(erroredNotifications) + ".");
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/sendInstallLink")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage sendInstallLink(@PathVariable String studyId, @PathVariable String userId, 
            @RequestParam(required = false) String osName) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        App app = appService.getApp(session.getAppId());
        String email = TRUE.equals(account.getEmailVerified()) ? account.getEmail() : null;
        Phone phone = TRUE.equals(account.getPhoneVerified()) ? account.getPhone() : null;
        
        participantService.sendInstallLinkMessage(app, PROMOTIONAL, account.getHealthCode(), email, phone, osName);
        
        return INSTALL_LINK_SEND_MSG;
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userId}")
    public StatusMessage deleteTestOrUnusedParticipant(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        if (!participantEligibleForDeletion(requestInfoService, account)) {
            throw new UnauthorizedException("Account is not a test account or it is already in use.");
        }
        App app = appService.getApp(session.getAppId());
        userAdminService.deleteUser(app, account.getId());
        return DELETE_MSG;
    }    
    
    /* STUDY ACTIVITY EVENT FOR STUDY PARTICIPANT */
    
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/activityevents")
    public ResourceList<StudyActivityEvent> getRecentActivityEvents(
            @PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAdministrativeSession();
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        return studyActivityEventService.getRecentStudyActivityEvents(session.getAppId(), studyId, account.getId());
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/activityevents/{eventId}")
    public ResourceList<StudyActivityEvent> getActivityEventHistory(@PathVariable String studyId,
            @PathVariable String userId,
            @PathVariable String eventId,
            @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAdministrativeSession();
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        Integer offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        Integer pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        AccountId accountId = AccountId.forId(account.getAppId(),  account.getId());
        
        return studyActivityEventService.getStudyActivityEventHistory(accountId, 
                studyId, eventId, offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userId}/activityevents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage publishActivityEvent(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(required = false) String showError, @RequestParam(required = false) String updateBursts) {
        UserSession session = getAdministrativeSession();
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);
        
        StudyActivityEventRequest request = parseJson(StudyActivityEventRequest.class);
        StudyActivityEventIdsMap eventMap = studyService.getStudyActivityEventIdsMap(session.getAppId(), studyId);
        boolean showErrorBool = "true".equals(showError);
        boolean updateBurstsBool = !"false".equals(updateBursts);
        
        studyActivityEventService.publishEvent(request.parse(eventMap)
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(account.getId()).build(), showErrorBool, updateBurstsBool);
        
        return EVENT_RECORDED_MSG;
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userId}/activityevents/{eventId}")
    public StatusMessage deleteActivityEvent(@PathVariable String studyId, 
            @PathVariable String userId,
            @PathVariable String eventId,
            @RequestParam(required = false) String showError) {
        UserSession session = getAdministrativeSession();
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userId);

        StudyActivityEventRequest request = new StudyActivityEventRequest(eventId, null, null, null);
        StudyActivityEventIdsMap eventMap = studyService.getStudyActivityEventIdsMap(session.getAppId(), studyId);
        boolean showErrorBool = "true".equals(showError);
        
        studyActivityEventService.deleteEvent(request.parse(eventMap)
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(account.getId()).build(), showErrorBool);
        
        return EVENT_DELETED_MSG;
    }
    
    /* --------------------------------------------------------------- */
    /* STUDY-SCOPED PARTICIPANT REPORTS */
    /* --------------------------------------------------------------- */
    
    // INDICES
    
    @GetMapping("/v5/studies/{studyId}/participants/reports")
    public ReportTypeResourceList<? extends ReportIndex> listParticipantReportIndices(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);

        // this test is done in the service, but it will miss cases where there are no
        // indices. Fail faster. 
        CAN_READ_PARTICIPANT_REPORTS.checkAndThrow(STUDY_ID, studyId);
        
        List<ReportIndex> list = reportService
                .getReportIndices(session.getAppId(), PARTICIPANT)
                .getItems().stream()
                .filter(index -> index.getStudyIds().contains(studyId))
                .collect(Collectors.toList());
        
        return new ReportTypeResourceList<>(list, true)
                .withRequestParam(ResourceList.STUDY_ID, studyId)
                .withRequestParam(ResourceList.REPORT_TYPE, PARTICIPANT);
    }

    @GetMapping("/v5/studies/{studyId}/participants/reports/{identifier}")
    public ReportIndex getParticipantReportIndex(@PathVariable String studyId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        return index;
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/reports/{identifier}")
    public StatusMessage deleteParticipantReportIndex(@PathVariable String studyId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        reportService.deleteParticipantReportIndex(session.getAppId(), null, identifier);
        
        return REPORT_INDEX_DELETED_MSG;
    }

    // REPORTS

    /**
     * I did not port over the date-only API. The date-time API can be made to serve for dates only
     * (just set the time portion to T00:00:00.000Z")
     */
    @GetMapping("/v5/studies/{studyId}/participants/{userIdToken}/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReport(@PathVariable String studyId,
            @PathVariable String userIdToken, @PathVariable String identifier,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userIdToken);
        
        return reportService.getParticipantReportV4(session.getAppId(), account.getId(), identifier,
                account.getHealthCode(), startTimeDate, endTimeDate, offsetKey, pageSizeInt);
    }

    /**
     * I did not port over the date-only API. The date-time API can be made to serve for dates only
     * (just set the time portion to T00:00:00.000Z")
     */
    @GetMapping("/v5/studies/{studyId}/participants/self/reports/{identifier}")
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportForSelf(@PathVariable String studyId,
            @PathVariable String identifier, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        Account account = getValidAccountInStudy(session.getAppId(), studyId, session.getId());

        DateTime startTimeDate = getDateTimeOrDefault(startTime, null);
        DateTime endTimeDate = getDateTimeOrDefault(endTime, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return reportService.getParticipantReportV4(session.getAppId(), account.getId(), identifier,
                account.getHealthCode(), startTimeDate, endTimeDate, offsetKey, pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/{userIdToken}/reports/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReport(@PathVariable String studyId, @PathVariable String userIdToken,
            @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userIdToken);
        
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        if (reportData.getStudyIds() == null) {
            reportData.setStudyIds(ImmutableSet.of());   
        }
        reportData.setStudyIds(addToSet(reportData.getStudyIds(), studyId));
        
        reportService.saveParticipantReport(session.getAppId(), account.getId(), 
                identifier, account.getHealthCode(), reportData);
        
        return REPORT_SAVED_MSG;
    }

    @PostMapping("/v5/studies/{studyId}/participants/self/reports/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantReportForSelf(@PathVariable String studyId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, session.getId());
        
        ReportData reportData = parseJson(ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        if (reportData.getStudyIds() == null) {
            reportData.setStudyIds(ImmutableSet.of());   
        }
        reportData.setStudyIds(addToSet(reportData.getStudyIds(), studyId));
        
        reportService.saveParticipantReport(session.getAppId(), account.getId(), 
                identifier, account.getHealthCode(), reportData);
        
        return REPORT_SAVED_MSG;
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userIdToken}/reports/{identifier}/{date}")
    public StatusMessage deleteParticipantReportRecord(@PathVariable String studyId, @PathVariable String userIdToken,
            @PathVariable String identifier, @PathVariable String date) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userIdToken);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        reportService.deleteParticipantReportRecord(session.getAppId(), account.getId(), identifier, date, account.getHealthCode());
        
        return REPORT_RECORD_DELETED_MSG;
    }

    @DeleteMapping("/v5/studies/{studyId}/participants/{userIdToken}/reports/{identifier}")
    public StatusMessage deleteParticipantReport(@PathVariable String studyId, @PathVariable String userIdToken,
            @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        Account account = getValidAccountInStudy(session.getAppId(), studyId, userIdToken);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(PARTICIPANT)
                .withAppId(session.getAppId()).build();
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        reportService.deleteParticipantReport(session.getAppId(), account.getId(), identifier, account.getHealthCode());
        
        return REPORT_DELETED_MSG;
    }
    
    /**
     * Get the account no matter what identifier is used (in particular, externalId:<externalId> can be
     * useful for the client), and throw an exception if the account does not exists, or it is not 
     * enrolled in the target study.
     */
    private Account getValidAccountInStudy(String appId, String studyId, String idToken) {
        AccountId accountId = BridgeUtils.parseAccountId(appId, idToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));        

        BridgeUtils.getElement(account.getEnrollments(), Enrollment::getStudyId, studyId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        return account;
    }
}