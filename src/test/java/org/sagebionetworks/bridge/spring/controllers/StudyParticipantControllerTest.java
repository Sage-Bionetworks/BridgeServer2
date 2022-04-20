package org.sagebionetworks.bridge.spring.controllers;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.cache.CacheKey.scheduleModificationTimestamp;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.models.reports.ReportType.PARTICIPANT;
import static org.sagebionetworks.bridge.models.sms.SmsType.PROMOTIONAL;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.EVENT_DELETED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.INSTALL_LINK_SEND_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.NOTIFY_SUCCESS_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.PREPARING_ROSTER_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.REPORT_DELETED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.REPORT_INDEX_DELETED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.REPORT_RECORD_DELETED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyParticipantController.REPORT_SAVED_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.participantschedules.ParticipantSchedule;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AdherenceService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.EnrollmentService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.Schedule2Service;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyActivityEventService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

public class StudyParticipantControllerTest extends Mockito {
    @Mock
    AppService mockAppService;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    EnrollmentService mockEnrollmentService;
    
    @Mock
    StudyActivityEventService mockStudyActivityEventService;

    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    Schedule2Service mockScheduleService;
    
    @Mock
    AdherenceService mockAdherenceService;
    
    @Mock
    AccountWorkflowService mockAccountWorkflowService;
    
    @Mock
    AuthenticationService mockAuthenticationService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    ReportService mockReportService;
    
    @Mock
    SessionUpdateService mockSessionUpdateService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Enrollment> enrollmentCaptor;
    
    @Captor
    ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @Captor
    ArgumentCaptor<ParticipantRosterRequest> requestCaptor;
    
    @Captor
    ArgumentCaptor<ReportDataKey> keyCaptor;
    
    @Captor
    ArgumentCaptor<ReportData> dataCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @InjectMocks
    @Spy
    StudyParticipantController controller;
    
    UserSession session;
    
    Account account;

    App app;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockResponse).when(controller).response();
        doReturn(mockRequest).when(controller).request();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .withHealthCode(HEALTH_CODE).build());
        
        account = Account.create();
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        // These are pretty much the same for all calls
        doReturn(session).when(controller).getAdministrativeSession();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(StudyParticipantController.class);
        assertGet(StudyParticipantController.class, "getEnrollmentsForUser");
        assertPost(StudyParticipantController.class, "searchForAccountSummaries");
        assertCreate(StudyParticipantController.class, "createParticipant");
        assertGet(StudyParticipantController.class, "getParticipant");
        assertPost(StudyParticipantController.class, "requestParticipantRoster");
        assertGet(StudyParticipantController.class, "getRequestInfo");
        assertPost(StudyParticipantController.class, "updateParticipant");
        assertPost(StudyParticipantController.class, "signOut");
        assertPost(StudyParticipantController.class, "requestResetPassword");
        assertPost(StudyParticipantController.class, "resendEmailVerification");
        assertPost(StudyParticipantController.class, "resendPhoneVerification");
        assertPost(StudyParticipantController.class, "resendConsentAgreement");
        assertGet(StudyParticipantController.class, "getUploads");
        assertGet(StudyParticipantController.class, "getNotificationRegistrations");
        assertPost(StudyParticipantController.class, "sendNotification");
        assertDelete(StudyParticipantController.class, "deleteTestOrUnusedParticipant");
        assertGet(StudyParticipantController.class, "getRecentActivityEvents");
        assertGet(StudyParticipantController.class, "getActivityEventHistory");
        assertPost(StudyParticipantController.class, "publishActivityEvent");
        assertDelete(StudyParticipantController.class, "deleteActivityEvent");
        assertGet(StudyParticipantController.class, "listParticipantReportIndices");
        assertGet(StudyParticipantController.class, "getParticipantReportIndex");
        assertDelete(StudyParticipantController.class, "deleteParticipantReportIndex");
        assertGet(StudyParticipantController.class, "getParticipantReport");
        assertPost(StudyParticipantController.class, "saveParticipantReport");
        assertDelete(StudyParticipantController.class, "deleteParticipantReportRecord");
        assertDelete(StudyParticipantController.class, "deleteParticipantReport");
        assertGet(StudyParticipantController.class, "getParticipantScheduleForSelf");
        assertGet(StudyParticipantController.class, "getParticipantScheduleForUser");
        assertGet(StudyParticipantController.class, "getParticipantReportForSelf");
        assertGet(StudyParticipantController.class, "getTimelineForUser");
        assertPost(StudyParticipantController.class, "saveParticipantReportForSelf");
        assertPost(StudyParticipantController.class, "sendInstallLink");
    }
    
    @Test
    public void requestParticipantRoster() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withEmail(EMAIL).withEmailVerified(true).build());
        
        doReturn(session).when(controller).getAdministrativeSession();
        
        mockRequestBody(mockRequest, 
                createJson("{'studyId':'the-wrong-study', 'password': '"+PASSWORD+"'}"));
        
        StatusMessage retValue = controller.requestParticipantRoster(TEST_STUDY_ID);
        assertEquals(retValue, PREPARING_ROSTER_MSG);
        
        verify(mockParticipantService).requestParticipantRoster(
                eq(app), eq(TEST_USER_ID), requestCaptor.capture());
        assertEquals(requestCaptor.getValue().getStudyId(), TEST_STUDY_ID);
        assertEquals(requestCaptor.getValue().getPassword(), PASSWORD);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void requestParticipantRoster_notAssociatedToStudy() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withEmail(EMAIL).withEmailVerified(true).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR);
        
        mockRequestBody(mockRequest, 
                createJson("{'studyId':'"+TEST_STUDY_ID+"', 'password': '"+PASSWORD+"'}"));
        
        controller.requestParticipantRoster(TEST_STUDY_ID);
    }
    
    @Test
    public void getActivityEvents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        List<StudyActivityEvent> list = ImmutableList.of(new StudyActivityEvent.Builder().build());
        ResourceList<StudyActivityEvent> page = new ResourceList<>(list);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(page);
        
        mockAccountInStudy();
        
        ResourceList<StudyActivityEvent> retList = controller.getRecentActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retList.getItems().size(), 1);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getActivityEventsAccountNotFound() throws Exception { 
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        // No enrollment record, so it's going to appear as not found
        List<EnrollmentDetail> list = ImmutableList.of();
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(list);
        
        controller.getRecentActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void publishActivityEvent() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", IMMUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        doReturn(session).when(controller).getAdministrativeSession();

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventId':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        StatusMessage retValue = controller.publishActivityEvent(TEST_STUDY_ID, TEST_USER_ID, null, null);
        assertEquals(retValue, StudyParticipantController.EVENT_RECORDED_MSG);
        
        verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:eventKey");
        assertEquals(event.getTimestamp(), CREATED_ON);
    }
    
    @Test
    public void publishActivityEvent_showError() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", IMMUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        doReturn(session).when(controller).getAdministrativeSession();

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventId':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        controller.publishActivityEvent(TEST_STUDY_ID, TEST_USER_ID, "true", null);
        
        verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(true), eq(true));
    }
    
    @Test
    public void publishActivityEvent_updateBursts() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", IMMUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        doReturn(session).when(controller).getAdministrativeSession();

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventId':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        controller.publishActivityEvent(TEST_STUDY_ID, TEST_USER_ID, "true", "true");
        
        verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(true), eq(true));
    }
    
    @Test
    public void publishActivityEvent_doNotUpdateBursts() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", IMMUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        doReturn(session).when(controller).getAdministrativeSession();

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventId':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        controller.publishActivityEvent(TEST_STUDY_ID, TEST_USER_ID, "true", "false");
        
        verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(true), eq(false));
    }
    
    @Test
    public void deleteActivityEvent() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());

        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doReturn(session).when(controller).getAdministrativeSession();

        StudyActivityEventIdsMap map = new StudyActivityEventIdsMap();
        map.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", MUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID))
            .thenReturn(map);

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventkey':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        StatusMessage retValue = controller.deleteActivityEvent(
                TEST_STUDY_ID, TEST_USER_ID, "eventKey", null);
        assertEquals(retValue, EVENT_DELETED_MSG);
        
        verify(mockStudyActivityEventService).deleteEvent(eventCaptor.capture(), eq(false));
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:eventKey");
    }
    
    @Test
    public void deleteActivityEvent_showError() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());

        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doReturn(session).when(controller).getAdministrativeSession();

        StudyActivityEventIdsMap map = new StudyActivityEventIdsMap();
        map.addCustomEvents(ImmutableList.of(new StudyCustomEvent("eventKey", MUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID))
            .thenReturn(map);

        TestUtils.mockRequestBody(mockRequest, createJson(
                "{'eventkey':'eventKey','timestamp':'"+CREATED_ON+"'}"));
        
        mockAccountInStudy();
        
        controller.deleteActivityEvent(TEST_STUDY_ID, TEST_USER_ID, "eventKey", "true");
        
        verify(mockStudyActivityEventService).deleteEvent(eventCaptor.capture(), eq(true));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getActivityEventsForParticipantNotInStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        List<EnrollmentDetail> enrollments = ImmutableList.of(new EnrollmentDetail(
                Enrollment.create(TEST_APP_ID, "other-study", TEST_USER_ID), null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
            .thenReturn(enrollments);
        
        controller.getRecentActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void getEnrollmentsForUser() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        mockAccountInStudy();
        
        EnrollmentDetail detail = new EnrollmentDetail(null, null, null, null);
        List<EnrollmentDetail> list = ImmutableList.of(detail);
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(list);
        
        PagedResourceList<EnrollmentDetail> page = controller.getEnrollmentsForUser(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(page.getItems().size(), 1);
        
        verify(mockEnrollmentService).getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getEnrollmentsForUserWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        // this mocks both the main call, and the way we check to verify the user is in 
        // the target study.
        mockAccountNotInStudy();
        
        controller.getEnrollmentsForUser(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void searchForAccountSummaries() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockParticipantService.getPagedAccountSummaries(eq(app), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAdminOnly(TRUE)
                .withEmailFilter("emailFilter").build();
        mockRequestBody(mockRequest, search);
        
        PagedResourceList<AccountSummary> retValue = controller.searchForAccountSummaries(TEST_STUDY_ID);
        assertSame(retValue, page);
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());
        
        AccountSummarySearch captured = searchCaptor.getValue();
        assertEquals(captured.getEnrolledInStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getEmailFilter(), "emailFilter");
    }
    
    @Test
    public void createParticipant() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockRequestBody(mockRequest, new StudyParticipant.Builder().withLastName("lastName").build());
        
        IdentifierHolder id = new IdentifierHolder("id");
        when(mockParticipantService.createParticipant(eq(app), any(), eq(true))).thenReturn(id);
        
        IdentifierHolder retValue = controller.createParticipant(TEST_STUDY_ID);
        assertSame(retValue, id);
        
        verify(mockParticipantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getLastName(), "lastName");
        
        verify(mockEnrollmentService).enroll(enrollmentCaptor.capture());
        Enrollment en = enrollmentCaptor.getValue();
        assertEquals(en.getAppId(), TEST_APP_ID);
        assertEquals(en.getStudyId(), TEST_STUDY_ID);
        assertEquals(en.getAccountId(), "id");
        assertTrue(en.isConsentRequired());
    }

    @Test
    public void getParticipantIncludeConsents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLastName("lastName").build();
        when(mockParticipantService.getParticipant(app, account, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getLastName(), participant.getLastName());
        
        verify(mockParticipantService).getParticipant(app, account, true);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantIncludeConsentsWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLastName("lastName").build();
        when(mockParticipantService.getParticipant(app, TEST_USER_ID, true)).thenReturn(participant);
        
        mockAccountNotInStudy();

        controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, true);
    }
    
    @Test
    public void getParticipantExcludeConsents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, false);
        
        verify(mockParticipantService).getParticipant(app, account, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantExcludeConsentsWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, false);
    }
    
    @Test
    public void getParticipantIncludesHealthCodeForSuperadmin() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, account, true)).thenReturn(participant);
        
        mockAccountInStudy("healthcode:"+TEST_USER_ID);

        String retValue = controller.getParticipant(TEST_STUDY_ID, "healthcode:"+TEST_USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getHealthCode(), "healthCode");
    }

    @Test
    public void getParticipantIncludesHealthCodeIfConfigured() throws Exception {
        app.setHealthCodeExportEnabled(true);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, account, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getHealthCode(), "healthCode");
    }
    
    @Test
    public void getParticipantGetsByHealthCodeIfConfigured() throws Exception {
        app.setHealthCodeExportEnabled(true);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        session.setAppId(TEST_APP_ID);

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, "healthCode:"+TEST_USER_ID, true)).thenReturn(participant);
        
        mockAccountInStudy("healthCode:"+TEST_USER_ID);

        String retValue = controller.getParticipant(TEST_STUDY_ID, "healthCode:"+TEST_USER_ID, true);
        assertNotNull(retValue);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantPreventsUnauthorizedHealthCodeRequests() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en1));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, "healthcode:"+TEST_USER_ID, true)).thenReturn(participant);
        
        Enrollment en2 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "healthcode:"+TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en2, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, "healthcode:"+TEST_USER_ID))
            .thenReturn(list);

        controller.getParticipant(TEST_STUDY_ID, "healthcode:"+TEST_USER_ID, true);
    }
    
    @Test
    public void getParticipantPreventsNoHealthCodeExportOverrriddenBySuperadmin() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, account, true)).thenReturn(participant);
        
        mockAccountInStudy("healthcode:"+TEST_USER_ID);

        String retValue = controller.getParticipant(TEST_STUDY_ID, "healthcode:"+TEST_USER_ID, true);
        assertNotNull(retValue);
    }
    
    @Test
    public void getParticipantRemovesHealthCodeIfDisabled() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, account, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, TEST_USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertNull(deser.getHealthCode());
    }
    
    @Test
    public void getRequestInfo() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(requestInfo);
        
        mockAccountInStudy();

        String retValue = controller.getRequestInfo(TEST_STUDY_ID, TEST_USER_ID);
        RequestInfo deser = BridgeObjectMapper.get().readValue(retValue, RequestInfo.class);
        assertEquals(deser.getLanguages(), LANGUAGES);
        
        verify(mockRequestInfoService).getRequestInfo(TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRequestInfoWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(requestInfo);
        
        mockAccountNotInStudy();

        controller.getRequestInfo(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRequestInfoWrongApp() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        // this works simply because the account is not in the right app, 
        // so the account lookup fails.
        
        controller.getRequestInfo(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void getRequestInfoNoObject() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();

        String retValue = controller.getRequestInfo(TEST_STUDY_ID, TEST_USER_ID);
        assertNotNull(retValue);
        
        verify(mockRequestInfoService).getRequestInfo(TEST_USER_ID);        
    }
    
    @Test
    public void updateParticipant() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("wrong-id")
                .withLastName("lastName").build();
        mockRequestBody(mockRequest, participant);
        
        mockAccountInStudy();

        StatusMessage retValue = controller.updateParticipant(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Participant updated.");
        
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), TEST_USER_ID);
        assertEquals(captured.getLastName(), "lastName");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateParticipantWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("wrong-id")
                .withLastName("lastName").build();
        mockRequestBody(mockRequest, participant);
        
        mockAccountNotInStudy();

        controller.updateParticipant(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void signOutDeleteToken() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();

        StatusMessage retValue = controller.signOut(TEST_STUDY_ID, TEST_USER_ID, true);
        assertEquals(retValue.getMessage(), "User signed out.");
        
        verify(mockAuthenticationService).signUserOut(app, TEST_USER_ID, true);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signOutDeleteTokenWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();

        controller.signOut(TEST_STUDY_ID, TEST_USER_ID, true);
    }
    
    @Test
    public void signOutDoNotDeleteToken() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();

        StatusMessage retValue = controller.signOut(TEST_STUDY_ID, TEST_USER_ID, false);
        assertEquals(retValue.getMessage(), "User signed out.");
        
        verify(mockAuthenticationService).signUserOut(app, TEST_USER_ID, false);
    }
    
    @Test
    public void requestResetPassword() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        StatusMessage retValue = controller.requestResetPassword(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Request to reset password sent to user.");
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
        verify(mockAccountWorkflowService).requestResetPassword(app, true, accountId);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void requestResetPasswordWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.requestResetPassword(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void resendEmailVerification() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        controller.resendEmailVerification(TEST_STUDY_ID, TEST_USER_ID);
        
        verify(mockAccountWorkflowService).resendVerification(ChannelType.EMAIL, TEST_APP_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendEmailVerificationWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendEmailVerification(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void  resendPhoneVerification() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        controller.resendPhoneVerification(TEST_STUDY_ID, TEST_USER_ID);
        
        verify(mockAccountWorkflowService).resendVerification(ChannelType.PHONE, TEST_APP_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void  resendPhoneVerificationWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendPhoneVerification(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void resendConsentAgreement() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        StatusMessage retValue = controller.resendConsentAgreement(TEST_STUDY_ID, TEST_USER_ID, "guid");
        assertEquals(retValue.getMessage(), "Consent agreement resent to user.");
        
        verify(mockParticipantService).resendConsentAgreement(app, SubpopulationGuid.create("guid"), TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendConsentAgreementWrongSTudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendConsentAgreement(TEST_STUDY_ID, TEST_USER_ID, "guid");
    }
    
    @Test
    public void getUploads() {
        List<UploadView> list = ImmutableList.of();
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(list, "key");
        
        DateTime start = TestConstants.CREATED_ON;
        DateTime end = TestConstants.MODIFIED_ON;
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService
                .getUploads(app, TEST_USER_ID, start, end, 50, "offsetKey")).thenReturn(page);

        mockAccountInStudy();
        
        ForwardCursorPagedResourceList<UploadView> retValue = controller.getUploads(TEST_STUDY_ID, TEST_USER_ID, start.toString(), end.toString(), 50, "offsetKey");
        assertSame(retValue, page);
        
        verify(mockParticipantService).getUploads(app, TEST_USER_ID, start, end, 50, "offsetKey");
    }

    @Test
    public void getUploadsDefaultParams() {
        List<UploadView> list = ImmutableList.of();
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(list, "key");
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService.getUploads(app, TEST_USER_ID, null, null, null, null)).thenReturn(page);

        mockAccountInStudy();
        
        ForwardCursorPagedResourceList<UploadView> retValue = controller.getUploads(TEST_STUDY_ID, TEST_USER_ID, null, null, null, null);
        assertSame(retValue, page);
        
        verify(mockParticipantService).getUploads(app, TEST_USER_ID, null, null, null, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadsWrongStudy() {
        DateTime start = TestConstants.CREATED_ON;
        DateTime end = TestConstants.MODIFIED_ON;
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getUploads(TEST_STUDY_ID, TEST_USER_ID, start.toString(), end.toString(), 50, "offsetKey");
    }
    
    @Test
    public void getNotificationRegistrations() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        List<NotificationRegistration> list = ImmutableList.of(NotificationRegistration.create());
        when(mockParticipantService.listRegistrations(app, TEST_USER_ID)).thenReturn(list);
        
        ResourceList<NotificationRegistration> retValue = controller.getNotificationRegistrations(TEST_STUDY_ID, TEST_USER_ID);
        assertSame(retValue.getItems(), list);
        
        verify(mockParticipantService).listRegistrations(app, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getNotificationRegistrationsWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getNotificationRegistrations(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void sendNotification() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService.sendNotification(any(), any(), any()))
            .thenReturn(ImmutableSet.of("This is an error"));
        
        mockAccountInStudy();
        
        NotificationMessage msg = new NotificationMessage.Builder().withSubject("subject")
                .withMessage("message").build();
        mockRequestBody(mockRequest, msg);
        
        StatusMessage retValue = controller.sendNotification(TEST_STUDY_ID, TEST_USER_ID);
        assertTrue(retValue.getMessage().contains("This is an error"));
        
        verify(mockParticipantService).sendNotification(eq(app), eq(TEST_USER_ID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(captured.getSubject(), "subject");
        assertEquals(captured.getMessage(), "message");
    }

    @Test
    public void sendNotificationNoMessages() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService.sendNotification(any(), any(), any()))
            .thenReturn(ImmutableSet.of());
        
        mockAccountInStudy();
        
        NotificationMessage msg = new NotificationMessage.Builder().withSubject("subject")
                .withMessage("message").build();
        mockRequestBody(mockRequest, msg);
        
        StatusMessage retValue = controller.sendNotification(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(NOTIFY_SUCCESS_MSG, retValue);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void sendNotificationWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.sendNotification(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void deleteTestParticipant() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        StatusMessage retValue = controller.deleteTestOrUnusedParticipant(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getMessage(), "User deleted.");
        
        verify(mockUserAdminService).deleteUser(app, TEST_USER_ID);
    }    
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTestParticipantWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, "wrong-study", TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        controller.deleteTestOrUnusedParticipant(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTestParticipantNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.empty());
        
        controller.deleteTestOrUnusedParticipant(TEST_STUDY_ID, TEST_USER_ID);
    }    

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestParticipantNotTestUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        
        RequestInfo requestInfo = new RequestInfo.Builder().withSignedInOn(CREATED_ON).build();
        when(mockRequestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(requestInfo);
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        controller.deleteTestOrUnusedParticipant(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getActivityEventsWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getRecentActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderNotProvided() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
            
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        when(mockCacheProvider.getObject(
                scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class)).thenReturn(MODIFIED_ON.toString());
        
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));
        
        when(controller.getDateTime()).thenReturn(CREATED_ON);
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockCacheProvider).setObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), MODIFIED_ON.toString());
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
        
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        assertEquals(requestInfoCaptor.getValue().getTimelineAccessedOn(), CREATED_ON); 
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getTimelineForSelf_notAuthenticated() {
        doThrow(new NotAuthenticatedException()).when(controller).getAuthenticatedAndConsentedSession();
            
        controller.getTimelineForSelf(TEST_STUDY_ID);
    }

    @Test(expectedExceptions = ConsentRequiredException.class)
    public void getTimelineForSelf_notConsented() {
        doThrow(new ConsentRequiredException(session)).when(controller).getAuthenticatedAndConsentedSession();
            
        controller.getTimelineForSelf(TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = "Caller is not enrolled in study 'test-study'")
    public void getTimelineForSelf_notEnrolledInStudy() {
        session.setParticipant(new StudyParticipant.Builder().build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
            
        controller.getTimelineForSelf(TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void getTimelineForSelf_studyNotFound() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
            
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenThrow(new EntityNotFoundException(Study.class));
        
        controller.getTimelineForSelf(TEST_STUDY_ID);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderProvidedModifiedOnNotCached() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        // It has been modified
        doReturn(MODIFIED_ON.minusMinutes(1).toString()).when(mockRequest).getHeader("If-Modified-Since");
            
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        // But no value from cache, it'll have to load the schedule
        
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
        verify(mockCacheProvider).setObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), MODIFIED_ON.toString());
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderProvidedModifiedOnIsCached() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        // It has been modified
        doReturn(MODIFIED_ON.minusMinutes(1).toString()).when(mockRequest).getHeader("If-Modified-Since");
            
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        when(mockCacheProvider.getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), 
                String.class)).thenReturn(MODIFIED_ON.toString());
        
        Schedule2 schedule = new Schedule2();
        // just make this different so we can verify this is set
        schedule.setModifiedOn(CREATED_ON); 
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
        verify(mockCacheProvider).setObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), CREATED_ON.toString());
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderSameAsCachedModifiedOnTimestamp() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(MODIFIED_ON.toString()).when(mockRequest).getHeader("If-Modified-Since");
            
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        when(mockCacheProvider.getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), 
                String.class)).thenReturn(MODIFIED_ON.toString());
        
        Schedule2 schedule = new Schedule2();
        // just make this different so we can verify this is set
        schedule.setModifiedOn(CREATED_ON); 
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));

        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
        verify(mockCacheProvider).setObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), CREATED_ON.toString());
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderSameAsModifiedOnTimestamp() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(MODIFIED_ON.toString()).when(mockRequest).getHeader("If-Modified-Since");

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderAfterModifiedOnTimestamp() {
        session.setParticipant(new StudyParticipant.Builder()
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(MODIFIED_ON.plusMinutes(1).toString()).when(mockRequest).getHeader("If-Modified-Since");

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        when(mockCacheProvider.getObject(
                scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class)).thenReturn(MODIFIED_ON.toString());
        
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockScheduleService.getScheduleForStudy(TEST_STUDY_ID, study)).thenReturn(Optional.of(schedule));
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 304);
        assertNull(retValue.getBody());
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockScheduleService, never()).getScheduleForStudy(any(), any());
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
    }
    
    @Test
    public void getTimelineForSelf_cacheHeaderBeforeModifiedOnTimestamp() {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        doReturn(MODIFIED_ON.minusMinutes(1).toString()).when(mockRequest).getHeader("If-Modified-Since");
        doReturn(CREATED_ON).when(controller).getDateTime();

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(Optional.of(schedule));
        
        ResponseEntity<Timeline> retValue = controller.getTimelineForSelf(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
        assertTrue(retValue.getBody() instanceof Timeline);
        
        verify(mockStudyService).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockScheduleService).getScheduleForStudy(TEST_APP_ID, study);
        verify(mockCacheProvider).getObject(scheduleModificationTimestamp(TEST_APP_ID, TEST_STUDY_ID), String.class);
        verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "timeline_retrieved");
        assertEquals(event.getTimestamp(), CREATED_ON);
    }
    
    @Test
    public void getTimelineForUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);

        Timeline timeline = new Timeline.Builder().build();
        when(mockScheduleService.getTimelineForSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(timeline);

        Timeline retValue = controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
        assertSame(retValue, timeline);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getTimelineForUser_notAuthorized() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(WORKER)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getTimelineForUser_noStudyAccess() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("studyB"))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
        expectedExceptionsMessageRegExp = "Account not found.")
    public void getTimelineForUser_accountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getTimelineForUser_userNotInStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Schedule not found.")
    public void getTimelineForUser_studyHasNoSchedule() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        Study study = Study.create();
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);

        controller.getTimelineForUser(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void getActivityEventHistory() throws Exception {
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        controller.getActivityEventHistory(TEST_STUDY_ID, TEST_USER_ID, "eventKey", "100", "250");
        
        verify(mockStudyActivityEventService).getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "eventKey",
                Integer.valueOf(100), Integer.valueOf(250));
    }
    
    @Test
    public void sendInstallLink() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setHealthCode(HEALTH_CODE);
        account.setEmail(EMAIL);
        account.setEmailVerified(true);
        account.setPhone(PHONE);
        account.setPhoneVerified(true);
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        StatusMessage retValue = controller.sendInstallLink(TEST_STUDY_ID, TEST_USER_ID, "Android");
        assertSame(retValue, INSTALL_LINK_SEND_MSG);
        
        verify(mockParticipantService).sendInstallLinkMessage(
                app, PROMOTIONAL, HEALTH_CODE, EMAIL, PHONE, "Android");
    }
    
    @Test
    public void sendInstallLinkNoVerifiedChannels() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setHealthCode(HEALTH_CODE);
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        StatusMessage retValue = controller.sendInstallLink(TEST_STUDY_ID, TEST_USER_ID, null);
        assertSame(retValue, INSTALL_LINK_SEND_MSG);
        
        verify(mockParticipantService).sendInstallLinkMessage(
                app, PROMOTIONAL, HEALTH_CODE, null, null, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void listParticipantReportIndices() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        ReportIndex index1 = ReportIndex.create();
        index1.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        ReportIndex index2 = ReportIndex.create();
        index2.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        ReportIndex index3 = ReportIndex.create(); // this one will be filtred out
        index3.setStudyIds(ImmutableSet.of());
        
        ReportTypeResourceList page = new ReportTypeResourceList(
                ImmutableList.of(index1, index2, index3), false);
        when(mockReportService.getReportIndices(TEST_APP_ID, PARTICIPANT)).thenReturn(page);
        
        ReportTypeResourceList<? extends ReportIndex> retValue = controller.listParticipantReportIndices(TEST_STUDY_ID);
        assertEquals(retValue.getItems().size(), 2);
        assertEquals(retValue.getRequestParams().get("studyId"), TEST_STUDY_ID);
        assertEquals(retValue.getRequestParams().get("reportType"), PARTICIPANT);
    }

    @Test
    public void getParticipantReportIndex() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        when(mockReportService.getReportIndex(any())).thenReturn(index);
        
        ReportIndex retValue = controller.getParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
        assertSame(retValue, index);
        
        verify(mockReportService).getReportIndex(keyCaptor.capture());
        assertEquals(keyCaptor.getValue().getIdentifier(), IDENTIFIER);
        assertEquals(keyCaptor.getValue().getReportType(), PARTICIPANT);
        assertEquals(keyCaptor.getValue().getAppId(), TEST_APP_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantReportIndex_indexNull() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockReportService.getReportIndex(any())).thenReturn(null);
        
        controller.getParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantReportIndex_indexWrongStudy() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of("some-other-study"));
        when(mockReportService.getReportIndex(any())).thenReturn(index);
        
        controller.getParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
    }
    
    @Test
    public void deleteParticipantReportIndex() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);

        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        StatusMessage retValue = controller.deleteParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
        assertSame(retValue, REPORT_INDEX_DELETED_MSG);
        
        verify(mockReportService).deleteParticipantReportIndex(TEST_APP_ID, null, IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReportIndex_nullIndex() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);

        when(mockReportService.getReportIndex(any())).thenReturn(null);
        
        controller.deleteParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReportIndex_wrongStudy() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);

        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of("some-other-study"));
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        controller.deleteParticipantReportIndex(TEST_STUDY_ID, IDENTIFIER);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void getParticipantReport() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy(TEST_USER_ID);
        
        ForwardCursorPagedResourceList<ReportData> page = new ForwardCursorPagedResourceList(ImmutableList.of(),
                "offsetKey", true);
        when(mockReportService.getParticipantReportV4(any(), any(), any(),
                any(), any(), any(), any(), anyInt())).thenReturn(page);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        ForwardCursorPagedResourceList<ReportData> retValue = controller.getParticipantReport(TEST_STUDY_ID,
                TEST_USER_ID, IDENTIFIER, CREATED_ON.toString(), MODIFIED_ON.toString(), "offsetKey", "150");
        assertSame(retValue, page);

        verify(mockReportService).getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, IDENTIFIER,
                HEALTH_CODE, CREATED_ON, MODIFIED_ON, "offsetKey", 150);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void getParticipantReport_defaults() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy(TEST_USER_ID);
        
        ForwardCursorPagedResourceList<ReportData> page = new ForwardCursorPagedResourceList(ImmutableList.of(), "offsetKey", true);
        when(mockReportService.getParticipantReportV4(any(), any(), any(),
                any(), any(), any(), any(), anyInt())).thenReturn(page);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        controller.getParticipantReport(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER, null, null, null, null);

        verify(mockReportService).getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, IDENTIFIER,
                HEALTH_CODE, null, null, null, API_DEFAULT_PAGE_SIZE);
    }
    
    @Test
    public void saveParticipantReport() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);        
        mockAccountInStudy();
        
        ReportData data = ReportData.create();
        data.setDate("2018-08-08T00:00:00.000Z");
        data.setData(TestUtils.getClientData());
        mockRequestBody(mockRequest, data);
        
        StatusMessage retValue = controller.saveParticipantReport(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER);
        assertSame(retValue, REPORT_SAVED_MSG);
        
        verify(mockReportService).saveParticipantReport(eq(TEST_APP_ID), eq(TEST_USER_ID), 
                eq(IDENTIFIER), eq(HEALTH_CODE), dataCaptor.capture());
        
        ReportData captured = dataCaptor.getValue();
        assertNull(captured.getKey());
        assertEquals(captured.getStudyIds(), ImmutableSet.of(TEST_STUDY_ID));
        assertEquals(captured.getData().toString(), TestUtils.getClientData().toString());
        assertNull(captured.getLocalDate());
        assertEquals(captured.getDateTime(), DateTime.parse("2018-08-08T00:00:00.000Z"));
    }

    @Test
    public void deleteParticipantReportRecord() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        mockAccountInStudy();
        
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        StatusMessage retValue = controller.deleteParticipantReportRecord(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER, CREATED_ON.toString());
        assertEquals(retValue, REPORT_RECORD_DELETED_MSG);
        
        verify(mockReportService).deleteParticipantReportRecord(TEST_APP_ID, TEST_USER_ID, IDENTIFIER, CREATED_ON.toString(), HEALTH_CODE);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReportRecord_noIndex() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy();
        when(mockReportService.getReportIndex(any())).thenReturn(null);
        
        controller.deleteParticipantReportRecord(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER, CREATED_ON.toString());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReportRecord_wrongStudy() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy();
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of());
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        controller.deleteParticipantReportRecord(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER, CREATED_ON.toString());
    }
    
    @Test
    public void deleteParticipantReport() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        
        mockAccountInStudy();
        
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of(TEST_STUDY_ID));
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        StatusMessage retValue = controller.deleteParticipantReport(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER);
        assertEquals(retValue, REPORT_DELETED_MSG);
        
        verify(mockReportService).deleteParticipantReport(TEST_APP_ID, TEST_USER_ID, IDENTIFIER, HEALTH_CODE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReport_noIndex() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy();
        when(mockReportService.getReportIndex(any())).thenReturn(null);
        
        controller.deleteParticipantReport(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantReport_wrongStudy() {
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR);
        mockAccountInStudy();
        
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(ImmutableSet.of("wrong-study"));
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(IDENTIFIER)
                .withReportType(PARTICIPANT)
                .withAppId(TEST_APP_ID).build();
        when(mockReportService.getReportIndex(key)).thenReturn(index);
        
        controller.deleteParticipantReport(TEST_STUDY_ID, TEST_USER_ID, IDENTIFIER);
    }
    
    @Test
    public void getParticipantScheduleForUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        mockAccountInStudy();
        
        ParticipantSchedule schedule = new ParticipantSchedule();
        when(mockScheduleService.getParticipantSchedule(
                eq(TEST_APP_ID), eq(TEST_STUDY_ID), any())).thenReturn(schedule);
        
        ParticipantSchedule retValue = controller.getParticipantScheduleForUser(TEST_STUDY_ID, TEST_USER_ID);
        assertSame(retValue, schedule);
    }
    
    @Test
    public void getParticipantScheduleForSelf_newTimeZone() {
        // Order of operations matters here
        InOrder ordered = inOrder(mockRequestInfoService, mockScheduleService, mockAccountService, 
                mockSessionUpdateService, mockStudyActivityEventService);        
        
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockAccountInStudy(TEST_USER_ID);
        
        when(controller.getDateTime()).thenReturn(CREATED_ON);
        
        ParticipantSchedule schedule = new ParticipantSchedule();
        when(mockScheduleService.getParticipantSchedule(
                eq(TEST_APP_ID), eq(TEST_STUDY_ID), any())).thenReturn(schedule);
        
        Account mockAccount = mock(Account.class);
        TestUtils.mockEditAccount(mockAccountService, mockAccount);
        
        ParticipantSchedule retValue = controller.getParticipantScheduleForSelf(TEST_STUDY_ID, TEST_CLIENT_TIME_ZONE);
        assertSame(retValue, schedule);

        ordered.verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        ordered.verify(mockSessionUpdateService).updateClientTimeZone(session, TEST_CLIENT_TIME_ZONE);
        assertEquals(account.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        verify(mockAccount).setClientTimeZone(TEST_CLIENT_TIME_ZONE);

        ordered.verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo info = requestInfoCaptor.getValue();
        assertEquals(info.getTimelineAccessedOn(), CREATED_ON);

        ordered.verify(mockStudyActivityEventService).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), TIMELINE_RETRIEVED.name().toLowerCase());
        assertEquals(event.getTimestamp(), CREATED_ON);

        ordered.verify(mockScheduleService).getParticipantSchedule(eq(TEST_APP_ID), eq(TEST_STUDY_ID), any());
    }
    
    @Test
    public void getParticipantScheduleForSelf_changedTimeZone() {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockAccountInStudy(TEST_USER_ID);
        account.setClientTimeZone("America/Chicago");
        
        when(controller.getDateTime()).thenReturn(CREATED_ON);
        
        ParticipantSchedule schedule = new ParticipantSchedule();
        when(mockScheduleService.getParticipantSchedule(
                eq(TEST_APP_ID), eq(TEST_STUDY_ID), any())).thenReturn(schedule);
        
        Account mockAccount = mock(Account.class);
        TestUtils.mockEditAccount(mockAccountService, mockAccount);
        
        controller.getParticipantScheduleForSelf(TEST_STUDY_ID, TEST_CLIENT_TIME_ZONE);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        verify(mockSessionUpdateService).updateClientTimeZone(session, TEST_CLIENT_TIME_ZONE);
        assertEquals(account.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        verify(mockAccount).setClientTimeZone(TEST_CLIENT_TIME_ZONE);
    }
    
    @Test
    public void getParticipantScheduleForSelf_sameTimeZone() {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withStudyIds(ImmutableSet.of(TEST_STUDY_ID))
                .withClientTimeZone(TEST_CLIENT_TIME_ZONE).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockAccountInStudy(TEST_USER_ID);
        
        when(controller.getDateTime()).thenReturn(CREATED_ON);
        
        ParticipantSchedule schedule = new ParticipantSchedule();
        when(mockScheduleService.getParticipantSchedule(
                eq(TEST_APP_ID), eq(TEST_STUDY_ID), any())).thenReturn(schedule);
        
        controller.getParticipantScheduleForSelf(TEST_STUDY_ID, TEST_CLIENT_TIME_ZONE);
        
        verify(mockAccountService, never()).editAccount(any(), any());
        verify(mockSessionUpdateService, never()).updateClientTimeZone(any(), any());
        assertEquals(account.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantScheduleForSelf_notEnrolledInStudy() {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockAccountInStudy(TEST_USER_ID);
        account.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        
        controller.getParticipantScheduleForSelf(TEST_STUDY_ID, TEST_CLIENT_TIME_ZONE);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "clientTimeZone parameter is required")
    public void getParticipantScheduleForSelf_noTimeZone() {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withStudyIds(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockAccountInStudy(TEST_USER_ID);
        
        controller.getParticipantScheduleForSelf(TEST_STUDY_ID, null);
    }
    
    private void mockAccountInStudy() {
        mockAccountInStudy(TEST_USER_ID);
    }
    
    private void mockAccountInStudy(String userIdToken) {
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, userIdToken);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.getEnrollments().add(en);
        account.setHealthCode(HEALTH_CODE);
        account.setId(TEST_USER_ID);
        account.setAppId(TEST_APP_ID);
    }
    
    private void mockAccountNotInStudy() {
        mockAccountInStudy("some other study");
    }
}
