package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACTIVITY_1;
import static org.sagebionetworks.bridge.TestConstants.CONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.ENCRYPTED_HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.IP_ADDRESS;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.NOTIFICATION_MESSAGE;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SUBPOP_GUID;
import static org.sagebionetworks.bridge.TestConstants.SUMMARY1;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.UNENCRYPTED_HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.SPONSORS_AND_PARTNERS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.SmsTemplate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.EnrollmentService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.SponsorService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class ParticipantControllerTest extends Mockito {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();

    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };

    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>() {
    };

    private static final Set<Roles> CALLER_ROLES = ImmutableSet.of(RESEARCHER);

    private static final Set<String> CALLER_STUDIES = ImmutableSet.of("studyA");

    private static final String ACTIVITY_GUID = ACTIVITY_1.getGuid();

    private static final DateTime START_TIME = TIMESTAMP.minusHours(3);

    private static final DateTime END_TIME = TIMESTAMP;

    private static final Set<String> EMPTY_SET = ImmutableSet.of();

    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withAppId(TEST_APP_ID).withPhone(PHONE)
            .withPassword(PASSWORD).build();
    private static final IdentifierUpdate PHONE_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            PHONE, null);
    private static final IdentifierUpdate EMAIL_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST,
            EMAIL, null, null);
    private static final IdentifierUpdate SYNAPSE_ID_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            null, SYNAPSE_USER_ID);
    
    @InjectMocks
    @Spy
    ParticipantController controller;

    @Mock
    ConsentService mockConsentService;

    @Mock
    ParticipantService mockParticipantService;

    @Mock
    AppService mockAppService;

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    CacheProvider mockCacheProvider;

    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    SponsorService mockSponsorSerice;
    
    @Mock
    EnrollmentService mockEnrollmentService;
    
    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<UserSession> sessionCaptor;

    @Captor
    ArgumentCaptor<DateTime> startTimeCaptor;

    @Captor
    ArgumentCaptor<DateTime> endTimeCaptor;

    @Captor
    ArgumentCaptor<NotificationMessage> messageCaptor;

    @Captor
    ArgumentCaptor<DateTime> startsOnCaptor;

    @Captor
    ArgumentCaptor<DateTime> endsOnCaptor;

    @Captor
    ArgumentCaptor<IdentifierUpdate> identifierUpdateCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;

    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;

    @Captor
    ArgumentCaptor<SmsTemplate> templateCaptor;
    
    @Captor
    ArgumentCaptor<CustomActivityEventRequest> eventRequestCaptor;
    
    UserSession session;

    App app;

    StudyParticipant participant;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        app = new DynamoApp();
        app.setUserProfileAttributes(Sets.newHashSet("foo", "baz"));
        app.setIdentifier(TEST_APP_ID);

        participant = new StudyParticipant.Builder().withRoles(CALLER_ROLES).withStudyIds(CALLER_STUDIES)
                .withId(TEST_USER_ID).build();

        session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        List<AccountSummary> summaries = ImmutableList.of(SUMMARY1, SUMMARY1, SUMMARY1);
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(summaries, 30).withRequestParam("offsetBy", 10)
                .withRequestParam("pageSize", 20).withRequestParam("startTime", START_TIME)
                .withRequestParam("endTime", END_TIME).withRequestParam("emailFilter", "foo");

        when(mockParticipantService.getPagedAccountSummaries(eq(app), any())).thenReturn(page);

        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        sessionUpdateService.setConsentService(mockConsentService);
        sessionUpdateService.setNotificationTopicService(mock(NotificationTopicService.class));
        controller.setSessionUpdateService(sessionUpdateService);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @AfterMethod
    public void after() {
        RequestContext.set(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ParticipantController.class);
        assertCreate(ParticipantController.class, "createSmsRegistration");
        assertGet(ParticipantController.class, "getSelfParticipant");
        assertPost(ParticipantController.class, "updateSelfParticipant");
        assertDelete(ParticipantController.class, "deleteTestParticipant");
        assertGet(ParticipantController.class, "getActivityEventsForWorker");
        assertGet(ParticipantController.class, "getActivityHistoryForWorkerV3");
        assertGet(ParticipantController.class, "getActivityHistoryForWorkerV2");
        assertPost(ParticipantController.class, "updateIdentifiers");
        assertGet(ParticipantController.class, "getParticipants");
        assertPost(ParticipantController.class, "searchForAccountSummaries");
        assertGet(ParticipantController.class, "getParticipantsForWorker");
        assertPost(ParticipantController.class, "searchForAccountSummariesForWorker");
        assertCreate(ParticipantController.class, "createParticipant");
        assertGet(ParticipantController.class, "getParticipant");
        assertGet(ParticipantController.class, "getParticipantForWorker");
        assertGet(ParticipantController.class, "getRequestInfoForWorker");
        assertGet(ParticipantController.class, "getRequestInfo");
        assertPost(ParticipantController.class, "updateParticipant");
        assertPost(ParticipantController.class, "signOut");
        assertPost(ParticipantController.class, "requestResetPassword");
        assertGet(ParticipantController.class, "getActivityHistoryV2");
        assertGet(ParticipantController.class, "getActivityHistoryV3");
        assertDelete(ParticipantController.class, "deleteActivities");
        assertPost(ParticipantController.class, "resendEmailVerification");
        assertPost(ParticipantController.class, "resendPhoneVerification");
        assertPost(ParticipantController.class, "resendConsentAgreement");
        assertPost(ParticipantController.class, "withdrawFromApp");
        assertPost(ParticipantController.class, "withdrawConsent");
        assertGet(ParticipantController.class, "getUploads");
        assertGet(ParticipantController.class, "getNotificationRegistrations");
        assertAccept(ParticipantController.class, "sendNotification");
        assertGet(ParticipantController.class, "getActivityEvents");
        assertAccept(ParticipantController.class, "sendSmsMessageForWorker");
        assertPost(ParticipantController.class, "createCustomActivityEvent");
    }

    @Test
    public void createSmsNotificationRegistration() throws Exception {
        // Requires researcher role.
        session.setParticipant(
                new StudyParticipant.Builder().copyOf(session.getParticipant()).withRoles(CALLER_ROLES).build());

        // Execute.
        StatusMessage result = controller.createSmsRegistration(TEST_USER_ID);
        assertEquals(result.getMessage(), "SMS notification registration created");

        // Verify dependent services.
        verify(mockParticipantService).createSmsRegistration(app, TEST_USER_ID);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipants() throws Exception {
        JsonNode result = controller.getParticipants("10", "20", "emailSubstring", "phoneSubstring",
                START_TIME.toString(), END_TIME.toString(), null, null);

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search.getOffsetBy(), 10);
        assertEquals(search.getPageSize(), 20);
        assertEquals(search.getEmailFilter(), "emailSubstring");
        assertEquals(search.getPhoneFilter(), "phoneSubstring");
        assertEquals(search.getAllOfGroups(), EMPTY_SET);
        assertEquals(search.getNoneOfGroups(), EMPTY_SET);
        assertEquals(search.getStartTime().toString(), START_TIME.toString());
        assertEquals(search.getEndTime().toString(), END_TIME.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsWithStartTimeEndTime() throws Exception {
        JsonNode result = controller.getParticipants("10", "20", "emailSubstring", "phoneSubstring", null, null,
                START_TIME.toString(), END_TIME.toString());

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());
        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search.getOffsetBy(), 10);
        assertEquals(search.getPageSize(), 20);
        assertEquals(search.getEmailFilter(), "emailSubstring");
        assertEquals(search.getPhoneFilter(), "phoneSubstring");
        assertEquals(search.getAllOfGroups(), EMPTY_SET);
        assertEquals(search.getNoneOfGroups(), EMPTY_SET);
        assertEquals(search.getStartTime().toString(), START_TIME.toString());
        assertEquals(search.getEndTime().toString(), END_TIME.toString());
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null, null, null, null, null, null);
    }

    @Test
    public void getParticipant() throws Exception {
        app.setHealthCodeExportEnabled(true);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).build();

        when(mockParticipantService.getParticipant(app, "aUser", true)).thenReturn(studyParticipant);

        String json = controller.getParticipant("aUser", true);
        JsonNode node = MAPPER.readTree(json);

        // StudyParticipant will encrypt the healthCode when you ask for it, so validate the
        // JSON itself.
        assertTrue(node.has("firstName"));
        assertTrue(node.has("healthCode"));
        assertFalse(node.has("encryptedHealthCode"));

        verify(mockParticipantService).getParticipant(app, "aUser", true);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantDeveloperIsNotSelf() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(DEVELOPER)).build());

        controller.getParticipant("aUser", true);
    }
    
    @Test
    public void getParticipantWithNoHealthCode() throws Exception {
        app.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(app, "aUser", true)).thenReturn(studyParticipant);

        String json = controller.getParticipant("aUser", true);

        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);

        assertEquals(retrievedParticipant.getFirstName(), "Test");
        assertNull(retrievedParticipant.getHealthCode());
    }

    @Test
    public void getParticipantReturnsNoHealthCodeForDeveloper() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER, RESEARCHER))
                .withStudyIds(CALLER_STUDIES).withId(TEST_USER_ID).build();
        session.setParticipant(participant);
        
        app.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withId("aUser").withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(app, "aUser", true)).thenReturn(studyParticipant);

        String json = controller.getParticipant("aUser", true);

        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);

        // You do not get the health code, because export of the health code is not enabled and
        // the caller is not an admin.
        assertNull(retrievedParticipant.getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void getParticipantReturnsHealthCodeForAdmin() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN, RESEARCHER))
                .withId(TEST_USER_ID).withStudyIds(CALLER_STUDIES).withId(TEST_USER_ID).build();
        session.setParticipant(participant);

        app.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withId("aUser").withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(app, "aUser", true)).thenReturn(studyParticipant);

        String json = controller.getParticipant("aUser", true);

        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);

        // You still get the health code, even though export of the health code is not enabled.
        assertEquals(retrievedParticipant.getHealthCode(), HEALTH_CODE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantWhereHealthCodeIsPrevented() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        controller.getParticipant("healthCode:"+TEST_USER_ID, true);
    }
    
    @Test
    public void getParticipantWithHealthCodeIfAdmin() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of(RESEARCHER, ADMIN)).build();
        session.setParticipant(participant);
        
        app.setHealthCodeExportEnabled(false);
        
        controller.getParticipant("healthCode:"+TEST_USER_ID, true);
    }
    
    @Test
    public void getParticipantForWorkerWithHealthCodeNotPrevented() throws Exception {
        // The caller is a worker
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        // Health codes are disabled
        app.setHealthCodeExportEnabled(false);
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(app, "healthCode:" + TEST_USER_ID, true)).thenReturn(studyParticipant);
        
        // You can still retrieve the user with a health code
        String result = controller.getParticipantForWorker(TEST_APP_ID, "healthCode:"+TEST_USER_ID, true);
        assertNotNull(result);
    }

    @Test
    public void signUserOut() throws Exception {
        StatusMessage result = controller.signOut(TEST_USER_ID, false);
        assertEquals(result.getMessage(), "User signed out.");

        verify(mockParticipantService).signUserOut(app, TEST_USER_ID, false);
    }

    
    @Test
    public void updateParticipant() throws Exception {
        app.getUserProfileAttributes().add("can_be_recontacted");
        String json = createJson("{'firstName':'firstName'," + "'lastName':'lastName'," + "'email':'email@email.com',"
                + "'externalId':'externalId'," + "'password':'newUserPassword',"
                + "'sharingScope':'sponsors_and_partners'," + "'notifyByEmail':true,"
                + "'dataGroups':['group2','group1']," + "'attributes':{'can_be_recontacted':'true'},"
                + "'languages':['en','fr']}");

        mockRequestBody(mockRequest, json);

        StatusMessage result = controller.updateParticipant(TEST_USER_ID);
        assertEquals(result.getMessage(), "Participant updated.");

        // Both the caller roles and the caller's studies are passed to participantService
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());

        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getId(), TEST_USER_ID);
        assertEquals(participant.getFirstName(), "firstName");
        assertEquals(participant.getLastName(), "lastName");
        assertEquals(participant.getEmail(), EMAIL);
        assertEquals(participant.getPassword(), "newUserPassword");
        assertEquals(participant.getExternalId(), "externalId");
        assertEquals(participant.getSharingScope(), SPONSORS_AND_PARTNERS);
        assertTrue(participant.isNotifyByEmail());
        assertEquals(participant.getDataGroups(), ImmutableSet.of("group2", "group1"));
        assertEquals(participant.getAttributes().get("can_be_recontacted"), "true");
        assertEquals(participant.getLanguages(), ImmutableList.of("en", "fr"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null, null, null, null, null, null);

        // paging with defaults
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search.getOffsetBy(), 0);
        assertEquals(search.getPageSize(), API_DEFAULT_PAGE_SIZE);
        assertNull(search.getEmailFilter());
        assertNull(search.getPhoneFilter());
        assertEquals(search.getAllOfGroups(), EMPTY_SET);
        assertEquals(search.getNoneOfGroups(), EMPTY_SET);
        assertNull(search.getStartTime());
        assertNull(search.getEndTime());
    }

    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = setUpCreateParticipant();
        doReturn(holder).when(mockParticipantService).createParticipant(eq(app), any(StudyParticipant.class),
                eq(true));

        IdentifierHolder result = controller.createParticipant();

        assertEquals(result.getIdentifier(), TEST_USER_ID);

        verify(mockParticipantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));

        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getFirstName(), "firstName");
        assertEquals(participant.getLastName(), "lastName");
        assertEquals(participant.getEmail(), EMAIL);
        assertEquals(participant.getPassword(), "newUserPassword");
        assertEquals(participant.getExternalId(), "externalId");
        assertEquals(participant.getSharingScope(), SPONSORS_AND_PARTNERS);
        assertTrue(participant.isNotifyByEmail());
        assertEquals(participant.getDataGroups(), ImmutableSet.of("group2", "group1"));
        assertEquals(participant.getAttributes().get("phone"), "123456789");
        assertEquals(participant.getLanguages(), ImmutableList.of("en", "fr"));
    }
    
    @Test
    public void getParticipantRequestInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7)).withAppId(TEST_APP_ID).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        String resultStr = controller.getRequestInfo("userId");

        // serialization was tested separately... just validate the object is there
        RequestInfo result = BridgeObjectMapper.get().readValue(resultStr, RequestInfo.class);
        assertEquals(result.getClientInfo(), requestInfo.getClientInfo());
        assertNull(result.getAppId());
    }

    @Test
    public void getParticipantRequestInfoIsNullsafe() throws Exception {
        // There is no request info.
        String resultStr = controller.getRequestInfo("userId");

        RequestInfo result = BridgeObjectMapper.get().readValue(resultStr, RequestInfo.class);
        assertNotNull(result); // values are all null, but object is returned
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantRequestInfoOnlyReturnsCurrentStudyInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withAppId("some-other-app").build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        controller.getRequestInfo("userId");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantRequestInfoForWorkerOnly() throws Exception {
        controller.getRequestInfoForWorker(app.getIdentifier(), TEST_USER_ID);
    }
    
    @Test
    public void getParticipantRequestInfoForWorker() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7)).withAppId(TEST_APP_ID).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        String resultStr = controller.getRequestInfoForWorker(app.getIdentifier(), "userId");

        RequestInfo result = BridgeObjectMapper.get().readValue(resultStr, RequestInfo.class);
        assertEquals(result.getClientInfo(), requestInfo.getClientInfo());
        assertNull(result.getAppId());
    }
    
    @Test
    public void getParticipantRequestInfoForWorkerIsNullsafe() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        // There is no request info.
        String resultStr = controller.getRequestInfoForWorker(app.getIdentifier(), "userId");

        RequestInfo result = BridgeObjectMapper.get().readValue(resultStr, RequestInfo.class);
        assertNotNull(result); // values are all null, but object is returned
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantRequestInfoForWorkerOnlyReturnsCurrentStudyInfo() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withAppId("some-other-app").build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        controller.getRequestInfoForWorker(app.getIdentifier(), "userId");
    }
    
    private IdentifierHolder setUpCreateParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder(TEST_USER_ID);

        app.getUserProfileAttributes().add("phone");
        String json = createJson("{'firstName':'firstName'," + "'lastName':'lastName'," + "'email':'email@email.com',"
                + "'externalId':'externalId'," + "'password':'newUserPassword',"
                + "'sharingScope':'sponsors_and_partners'," + "'notifyByEmail':true,"
                + "'dataGroups':['group2','group1']," + "'attributes':{'phone':'123456789'},"
                + "'languages':['en','fr']}");
        mockRequestBody(mockRequest, json);
        return holder;
    }

    @Test
    public void updateParticipantWithMismatchedIdsUsesURL() throws Exception {
        mockRequestBody(mockRequest, createJson("{'id':'id2'}"));

        StatusMessage result = controller.updateParticipant("id1");
        assertEquals(result.getMessage(), "Participant updated.");

        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());

        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals(persisted.getId(), "id1");
    }

    @Test
    public void getSelfParticipantNoConsentHistories() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).withFirstName("Test").build();
        when(mockParticipantService.getSelfParticipant(eq(app), any(), eq(false))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(false);
        
        verify(mockParticipantService).getSelfParticipant(eq(app), contextCaptor.capture(), eq(false));
        assertEquals(contextCaptor.getValue().getUserId(), TEST_USER_ID);

        JsonNode node = MAPPER.readTree(result);
        assertEquals(node.get("firstName").textValue(), "Test");
        assertEquals(node.get("healthCode").textValue(), UNENCRYPTED_HEALTH_CODE);
        assertNull(node.get("encryptedHealthCode"));
    }

    @Test
    public void getSelfParticipantWithConsentHistories() throws Exception {
        DateTime timestamp = DateTime.now(DateTimeZone.UTC);
        UserConsentHistory history = new UserConsentHistory.Builder().withHealthCode(HEALTH_CODE)
                .withSubpopulationGuid(SubpopulationGuid.create("guid")).withConsentCreatedOn(timestamp.getMillis())
                .withName("Test Name").withBirthdate("2000-01-01").withImageData("imageData")
                .withImageMimeType("imageMimeType").withSignedOn(timestamp.getMillis())
                .withWithdrewOn(timestamp.getMillis()).withHasSignedActiveConsent(true).build();

        Map<String, List<UserConsentHistory>> consentHistories = new HashMap<>();
        consentHistories.put("guid", ImmutableList.of(history));

        StudyParticipant studyParticipant = new StudyParticipant.Builder()
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).withFirstName("Test")
                .withConsentHistories(consentHistories).build();

        when(mockParticipantService.getSelfParticipant(eq(app), any(), eq(true))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(true);

        verify(mockParticipantService).getSelfParticipant(eq(app), any(), eq(true));

        JsonNode nodeParticipant = MAPPER.readTree(result);
        JsonNode nodeHistory = nodeParticipant.get("consentHistories").get("guid").get(0);
        // Verify these are formatted correctly. These are just the unusual fields that required an annotation
        assertEquals(nodeHistory.get("consentCreatedOn").textValue(), timestamp.toString());
        assertEquals(nodeHistory.get("signedOn").textValue(), timestamp.toString());
        assertEquals(nodeHistory.get("withdrewOn").textValue(), timestamp.toString());

        StudyParticipant deserParticipant = MAPPER.treeToValue(nodeParticipant, StudyParticipant.class);

        JsonNode node = MAPPER.readTree(result);
        assertEquals(node.get("firstName").textValue(), "Test");
        assertEquals(node.get("healthCode").textValue(), UNENCRYPTED_HEALTH_CODE);
        assertNull(node.get("encryptedHealthCode"));

        List<UserConsentHistory> deserHistories = deserParticipant.getConsentHistories().get("guid");
        assertEquals(deserHistories.size(), 1);

        UserConsentHistory deserHistory = deserHistories.get(0);
        assertEquals(deserHistory.getSubpopulationGuid(), "guid");
        assertEquals(deserHistory.getConsentCreatedOn(), timestamp.getMillis());
        assertEquals(deserHistory.getName(), "Test Name");
        assertEquals(deserHistory.getBirthdate(), "2000-01-01");
        assertEquals(deserHistory.getImageData(), "imageData");
        assertEquals(deserHistory.getImageMimeType(), "imageMimeType");
        assertEquals(deserHistory.getSignedOn(), timestamp.getMillis());
        assertEquals(deserHistory.getWithdrewOn(), new Long(timestamp.getMillis()));
        assertTrue(deserHistory.isHasSignedActiveConsent());
    }
    
    @Test
    public void getSelfParticipantReturnsNoHealthCode() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of()).build());
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).withFirstName("Test").build();
        when(mockParticipantService.getSelfParticipant(eq(app), any(), eq(false))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(false);
        
        JsonNode node = MAPPER.readTree(result);
        assertNull(node.get("healthCode"));
        assertNull(node.get("encryptedHealthCode"));
    }
    
    @Test
    public void getSelfParticipantReturnsHealthCodeForAdmins() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).withFirstName("Test").build();
        when(mockParticipantService.getSelfParticipant(eq(app), any(), eq(false))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(false);
        
        JsonNode node = MAPPER.readTree(result);
        assertEquals(node.get("healthCode").textValue(), UNENCRYPTED_HEALTH_CODE);
    }

    @Test
    public void updateSelfParticipant() throws Exception {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress(IP_ADDRESS)
                .withCallerEnrolledStudies(ImmutableSet.of("studyA", "studyB")).build());

        // All values should be copied over here, also add a healthCode to verify it is not unset.
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(TestUtils.getStudyParticipant(ParticipantControllerTest.class)).withId(TEST_USER_ID)
                .withLanguages(LANGUAGES).withRoles(ImmutableSet.of(DEVELOPER)) // <-- should not be passed along
                .withDataGroups(USER_DATA_GROUPS).withStudyIds(USER_STUDY_IDS)
                .withHealthCode(HEALTH_CODE).build();
        session.setParticipant(participant);
        session.setIpAddress(IP_ADDRESS); // if this is not the same as request, you get an authentication error

        doReturn(participant).when(mockParticipantService).getParticipant(eq(app), eq(TEST_USER_ID), anyBoolean());

        String json = MAPPER.writeValueAsString(participant);
        mockRequestBody(mockRequest, json);

        JsonNode result = controller.updateSelfParticipant();

        assertEquals(result.get("type").asText(), "UserSessionInfo");
        assertNull(result.get("healthCode"));

        // verify the object is passed to service, one field is sufficient
        verify(mockCacheProvider).setUserSession(any());
        
        InOrder inOrder = inOrder(mockParticipantService);
        inOrder.verify(mockParticipantService).getParticipant(app, TEST_USER_ID, false);
        // No roles are passed in this method, and the studies of the user are passed
        inOrder.verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        inOrder.verify(mockParticipantService).getParticipant(app, TEST_USER_ID, true);
        
        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), TEST_USER_ID);
        assertEquals(captured.getFirstName(), "FirstName");
        assertEquals(captured.getSharingScope(), ALL_QUALIFIED_RESEARCHERS);
        assertTrue(captured.isNotifyByEmail());
        assertEquals(captured.getDataGroups(), USER_DATA_GROUPS);
        assertEquals(captured.getStudyIds(), USER_STUDY_IDS);
        assertEquals(captured.getAttributes().get("can_be_recontacted"), "true");

        verify(mockConsentService).getConsentStatuses(contextCaptor.capture());
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getAppId(), TEST_APP_ID);
        assertEquals(context.getHealthCode(), HEALTH_CODE);
        assertEquals(context.getUserId(), TEST_USER_ID);
        assertEquals(context.getClientInfo(), ClientInfo.UNKNOWN_CLIENT);
        assertEquals(context.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(context.getUserStudyIds(), USER_STUDY_IDS);
        assertEquals(context.getLanguages(), LANGUAGES);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields: phone.*")
    public void updateSelfParticipantBadJson() throws Exception {
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.updateSelfParticipant();
    }

    // Some values will be missing in the JSON and should be preserved from this original participant object.
    // This allows client to provide JSON that's less than the entire participant.
    @Test
    public void partialUpdateSelfParticipant() throws Exception {
        // User must be consented to change sharing status
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        
        Map<String, String> attrs = ImmutableMap.of("foo", "bar", "baz", "bap");

        StudyParticipant participant = new StudyParticipant.Builder().withFirstName("firstName")
                .withLastName("lastName").withEmail("email@email.com").withId("id").withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withNotifyByEmail(true)
                .withDataGroups(ImmutableSet.of("group1", "group2")).withAttributes(attrs)
                .withLanguages(ImmutableList.of("en")).withStatus(AccountStatus.DISABLED).withExternalId("POWERS")
                .build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(app), eq(TEST_USER_ID), anyBoolean());

        String json = createJson("{'externalId':'simpleStringChange'," + "'sharingScope':'no_sharing',"
                + "'notifyByEmail':false," + "'attributes':{'baz':'belgium'}," + "'languages':['fr'],"
                + "'status':'enabled'," + "'roles':['admin']}");
        mockRequestBody(mockRequest, json);

        JsonNode result = controller.updateSelfParticipant();
        assertEquals("UserSessionInfo", result.get("type").asText());

        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), TEST_USER_ID);
        assertEquals(captured.getFirstName(), "firstName");
        assertEquals(captured.getLastName(), "lastName");
        assertEquals(captured.getEmail(), "email@email.com");
        assertEquals(captured.getPassword(), "password");
        assertEquals(captured.getSharingScope(), NO_SHARING);
        assertFalse(captured.isNotifyByEmail());
        assertEquals(captured.getDataGroups(), ImmutableSet.of("group1", "group2"));
        assertNull(captured.getAttributes().get("foo"));
        assertEquals(captured.getAttributes().get("baz"), "belgium");
        assertEquals(captured.getStatus(), ENABLED);
        assertEquals(captured.getLanguages(), ImmutableList.of("fr"));
        assertEquals(captured.getExternalId(), "simpleStringChange");
    }
    
    @Test
    public void participantUpdateSelfCannotToggleSharingWhenUnconsented() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withSharingScope(NO_SHARING).build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(app), eq(TEST_USER_ID), anyBoolean());

        String json = createJson("{'sharingScope':'all_qualified_researchers'}");
        mockRequestBody(mockRequest, json);

        controller.updateSelfParticipant();

        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getSharingScope(), NO_SHARING);
    }

    @Test
    public void participantUpdateSelfWithNullSharingDoesNotClearSharing() throws Exception {
        // It's not a matter of consent... the user is consented:
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        
        StudyParticipant participant = new StudyParticipant.Builder().withSharingScope(ALL_QUALIFIED_RESEARCHERS).build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(app), eq(TEST_USER_ID), anyBoolean());

        String json = createJson("{}");
        mockRequestBody(mockRequest, json);

        controller.updateSelfParticipant();

        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getSharingScope(), ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        StatusMessage result = controller.requestResetPassword(TEST_USER_ID);
        assertEquals(result.getMessage(), "Request to reset password sent to user.");

        verify(mockParticipantService).requestResetPassword(app, TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotResetPasswordIfNotResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withId("notUserId").withRoles(ImmutableSet.of(DEVELOPER)).build();
        session.setParticipant(participant);

        controller.requestResetPassword(TEST_USER_ID);
    }

    @Test
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(TEST_USER_ID).build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(app), eq(TEST_USER_ID), anyBoolean());

        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();

        mockRequestBody(mockRequest, participant);

        JsonNode result = controller.updateSelfParticipant();
        assertEquals(result.get("type").asText(), "UserSessionInfo");

        // verify the object is passed to service, one field is sufficient
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), TEST_USER_ID);
    }

    @Test
    public void canGetActivityHistoryV2() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(app),
                eq(TEST_USER_ID), eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));

        JsonNode result = controller.getActivityHistoryV2(TEST_USER_ID, ACTIVITY_GUID, START_TIME.toString(),
                END_TIME.toString(), "200", null, "77");
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(result.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);

        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());

        assertEquals(result.get("offsetBy").asText(), "200");
        assertEquals(page.getItems().size(), 1); // have not mocked out these items, but the list is there.
        assertEquals(page.getRequestParams().get("pageSize"), 77);
        assertEquals(page.getRequestParams().get("offsetKey"), "200");

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityHistoryV2WithOffsetKey() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(app),
                eq(TEST_USER_ID), eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));

        JsonNode result = controller.getActivityHistoryV2(TEST_USER_ID, ACTIVITY_GUID, START_TIME.toString(),
                END_TIME.toString(), null, "200", "77");
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(result.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);

        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals(activity.getSchedulePlanGuid(), "schedulePlanGuid");
        assertNull(activity.getHealthCode());

        assertEquals(page.getItems().size(), 1); // have not mocked out these items, but the list is there.
        assertEquals(page.getRequestParams().get("pageSize"), 77);
        assertEquals(page.getRequestParams().get("offsetKey"), "200");

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityV2WithNullValues() throws Exception {
        doReturn(createActivityResultsV2(null, API_DEFAULT_PAGE_SIZE)).when(mockParticipantService).getActivityHistory(
                eq(app), eq(TEST_USER_ID), eq(ACTIVITY_GUID), any(), any(), eq(null), eq(API_DEFAULT_PAGE_SIZE));

        JsonNode result = controller.getActivityHistoryV2(TEST_USER_ID, ACTIVITY_GUID, null, null, null, null, null);
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(result.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);

        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals(activity.getSchedulePlanGuid(), "schedulePlanGuid");
        assertNull(activity.getHealthCode());
        assertEquals(page.getItems().size(), 1); // have not mocked out these items, but the list is there.
        assertEquals(page.getRequestParams().get("pageSize"), API_DEFAULT_PAGE_SIZE);

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ACTIVITY_GUID), eq(null), eq(null),
                eq(null), eq(API_DEFAULT_PAGE_SIZE));
    }

    @Test
    public void deleteActivities() throws Exception {
        StatusMessage result = controller.deleteActivities(TEST_USER_ID);
        assertEquals(result.getMessage(), "Scheduled activities deleted.");

        verify(mockParticipantService).deleteActivities(app, TEST_USER_ID);
    }

    @Test
    public void resendEmailVerification() throws Exception {
        StatusMessage result = controller.resendEmailVerification(TEST_USER_ID);
        assertEquals(result.getMessage(), "Email verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(app, ChannelType.EMAIL, TEST_USER_ID);
    }

    @Test
    public void resendPhoneVerification() throws Exception {
        StatusMessage result = controller.resendPhoneVerification(TEST_USER_ID);
        assertEquals(result.getMessage(), "Phone verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(app, ChannelType.PHONE, TEST_USER_ID);
    }

    @Test
    public void resendConsentAgreement() throws Exception {
        StatusMessage result = controller.resendConsentAgreement(TEST_USER_ID, SUBPOP_GUID.getGuid());
        assertEquals(result.getMessage(), "Consent agreement resent to user.");

        verify(mockParticipantService).resendConsentAgreement(app, SUBPOP_GUID, TEST_USER_ID);
    }

    @Test
    public void withdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            mockRequestBody(mockRequest, json);

            controller.withdrawFromApp(TEST_USER_ID);

            verify(mockParticipantService).withdrawFromApp(app, TEST_USER_ID, new Withdrawal("Because, reasons."),
                    20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test
    public void withdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            mockRequestBody(mockRequest, json);

            controller.withdrawConsent(TEST_USER_ID, SUBPOP_GUID.getGuid());

            verify(mockParticipantService).withdrawConsent(app, TEST_USER_ID, SUBPOP_GUID,
                    new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test
    public void getUploads() throws Exception {
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z").withZone(DateTimeZone.UTC);
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z").withZone(DateTimeZone.UTC);

        List<? extends Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<? extends Upload> uploads = new ForwardCursorPagedResourceList<>(list, "abc")
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE).withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockParticipantService).getUploads(app, TEST_USER_ID, startTime, endTime, 10, "abc");

        ForwardCursorPagedResourceList<UploadView> result = controller.getUploads(TEST_USER_ID, startTime.toString(),
                endTime.toString(), 10, "abc");

        verify(mockParticipantService).getUploads(app, TEST_USER_ID, startTime, endTime, 10, "abc");

        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test
    public void getUploadsNullsDateRange() throws Exception {
        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE);
        doReturn(uploads).when(mockParticipantService).getUploads(app, TEST_USER_ID, null, null, null, null);

        controller.getUploads(TEST_USER_ID, null, null, null, null);

        verify(mockParticipantService).getUploads(app, TEST_USER_ID, null, null, null, null);
    }

    @Test
    public void getNotificationRegistrations() throws Exception {
        List<NotificationRegistration> list = ImmutableList.of();
        doReturn(list).when(mockParticipantService).listRegistrations(app, TEST_USER_ID);

        ResourceList<NotificationRegistration> result = controller.getNotificationRegistrations(TEST_USER_ID);

        JsonNode node = MAPPER.valueToTree(result);
        assertEquals(node.get("items").size(), 0);
        assertEquals(node.get("type").asText(), "ResourceList");

        verify(mockParticipantService).listRegistrations(app, TEST_USER_ID);
    }

    @Test
    public void sendMessage() throws Exception {
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        StatusMessage result = controller.sendNotification(TEST_USER_ID);

        assertEquals(result.getMessage(), "Message has been sent to external notification service.");

        verify(mockParticipantService).sendNotification(eq(app), eq(TEST_USER_ID), messageCaptor.capture());

        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(captured.getSubject(), "a subject");
        assertEquals(captured.getMessage(), "a message");
    }

    @Test
    public void sendMessageWithSomeErrors() throws Exception {
        Set<String> erroredRegistrations = ImmutableSet.of("123", "456");
        when(mockParticipantService.sendNotification(app, TEST_USER_ID, NOTIFICATION_MESSAGE))
                .thenReturn(erroredRegistrations);
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        StatusMessage result = controller.sendNotification(TEST_USER_ID);

        assertEquals(result.getMessage(),
                "Message has been sent to external notification service. Some registrations returned errors: 123, 456.");
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantsForWorkerOnly() throws Exception {
        DateTime start = DateTime.now();
        DateTime end = DateTime.now();

        controller.getParticipantsForWorker(app.getIdentifier(), "10", "20", "emailSubstring", "phoneSubstring",
                start.toString(), end.toString(), null, null);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantForWorkerOnly() throws Exception {
        controller.getParticipantForWorker(app.getIdentifier(), TEST_USER_ID, true);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        when(mockAppService.getApp(app.getIdentifier())).thenReturn(app);

        JsonNode result = controller.getParticipantsForWorker(app.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", START_TIME.toString(), END_TIME.toString(), null, null);

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search.getOffsetBy(), 10);
        assertEquals(search.getPageSize(), 20);
        assertEquals(search.getEmailFilter(), "emailSubstring");
        assertEquals(search.getPhoneFilter(), "phoneSubstring");
        assertEquals(search.getAllOfGroups(), EMPTY_SET);
        assertEquals(search.getNoneOfGroups(), EMPTY_SET);
        assertEquals(search.getStartTime().toString(), START_TIME.toString());
        assertEquals(search.getEndTime().toString(), END_TIME.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsForWorkerUsingStartTimeEndTime() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        when(mockAppService.getApp(app.getIdentifier())).thenReturn(app);

        JsonNode result = controller.getParticipantsForWorker(app.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", null, null, START_TIME.toString(), END_TIME.toString());

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search.getOffsetBy(), 10);
        assertEquals(search.getPageSize(), 20);
        assertEquals(search.getEmailFilter(), "emailSubstring");
        assertEquals(search.getPhoneFilter(), "phoneSubstring");
        assertEquals(search.getAllOfGroups(), EMPTY_SET);
        assertEquals(search.getNoneOfGroups(), EMPTY_SET);
        assertEquals(search.getStartTime().toString(), START_TIME.toString());
        assertEquals(search.getEndTime().toString(), END_TIME.toString());
    }

    @Test
    public void getParticipantForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(TEST_USER_ID).withHealthCode(HEALTH_CODE)
                .build();

        when(mockAppService.getApp(app.getIdentifier())).thenReturn(app);
        when(mockParticipantService.getParticipant(app, TEST_USER_ID, true)).thenReturn(foundParticipant);

        String result = controller.getParticipantForWorker(app.getIdentifier(), TEST_USER_ID, true);

        JsonNode participantNode = MAPPER.readTree(result);
        assertEquals(participantNode.get("healthCode").textValue(), HEALTH_CODE);
        assertNull(participantNode.get("encryptedHealthCode"));
        assertEquals(participantNode.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).getParticipant(app, TEST_USER_ID, true);
    }

    @Test
    public void getActivityHistoryV3() throws Exception {
        doReturn(createActivityResultsV2("offsetKey", 15)).when(mockParticipantService).getActivityHistory(eq(app),
                eq(TEST_USER_ID), eq(ActivityType.SURVEY), eq("referentGuid"), any(), any(), eq("offsetKey"), eq(15));

        String result = controller.getActivityHistoryV3(TEST_USER_ID, "surveys", "referentGuid", START_TIME.toString(),
                END_TIME.toString(), "offsetKey", "15");

        JsonNode node = MAPPER.readTree(result);
        assertEquals(node.get("requestParams").get("pageSize").intValue(), 15);
        assertEquals(node.get("requestParams").get("offsetKey").asText(), "offsetKey");

        // The fact this can be converted to a forward cursor object is ideal
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(node.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        assertEquals((int) page.getRequestParams().get("pageSize"), 15);

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ActivityType.SURVEY),
                eq("referentGuid"), startTimeCaptor.capture(), endTimeCaptor.capture(), eq("offsetKey"), eq(15));
        assertEquals(startTimeCaptor.getValue().toString(), START_TIME.toString());
        assertEquals(endTimeCaptor.getValue().toString(), END_TIME.toString());
    }

    @Test
    public void getActivityHistoryV3DefaultsToNulls() throws Exception {
        controller.getActivityHistoryV3(TEST_USER_ID, "badtypes", null, null, null, null, null);

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(null), eq(null),
                startTimeCaptor.capture(), endTimeCaptor.capture(), eq(null),
                eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        assertNull(startTimeCaptor.getValue());
        assertNull(endTimeCaptor.getValue());
    }

    @Test
    public void updateIdentifiersWithPhone() throws Exception {
        mockRequestBody(mockRequest, PHONE_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());
        verify(mockCacheProvider).setUserSession(sessionCaptor.capture());
        assertEquals(sessionCaptor.getValue().getId(), participant.getId());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getEmail(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail());
        assertEquals(update.getSignIn().getPassword(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getPhoneUpdate(), PHONE);
        assertNull(update.getEmailUpdate());
    }

    @Test
    public void updateIdentifiersWithEmail() throws Exception {
        mockRequestBody(mockRequest, EMAIL_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getPhone(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone());
        assertEquals(update.getSignIn().getPassword(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getEmailUpdate(), EMAIL);
        assertNull(update.getPhoneUpdate());
    }

    @Test
    public void updateIdentifiersWithSynapseUserId() throws Exception {
        mockRequestBody(mockRequest, SYNAPSE_ID_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getEmail(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail());
        assertEquals(update.getSignIn().getPassword(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getSynapseUserIdUpdate(), SYNAPSE_USER_ID);
        assertNull(update.getPhoneUpdate());
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void updateIdentifierRequiresAuthentication() throws Exception {
        doReturn(null).when(controller).getSessionIfItExists();

        mockRequestBody(mockRequest, PHONE_UPDATE);

        controller.updateIdentifiers();
    }
    
    @Test
    public void getParticipantWithNoConsents() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        when(mockParticipantService.getParticipant(app, "aUser", false)).thenReturn(studyParticipant);

        controller.getParticipant("aUser", false);

        verify(mockParticipantService).getParticipant(app, "aUser", false);
    }

    @Test
    public void getParticipantForWorkerNoConsents() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(TEST_USER_ID).build();

        when(mockAppService.getApp(app.getIdentifier())).thenReturn(app);
        when(mockParticipantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(foundParticipant);

        controller.getParticipantForWorker(app.getIdentifier(), TEST_USER_ID, false);

        verify(mockParticipantService).getParticipant(app, TEST_USER_ID, false);
    }

    @Test
    public void sendSMSForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        mockRequestBody(mockRequest, new SmsTemplate("This is a message"));

        StatusMessage result = controller.sendSmsMessageForWorker(TEST_APP_ID, TEST_USER_ID);

        assertEquals(result.getMessage(), "Message sent.");
        verify(mockParticipantService).sendSmsMessage(eq(app), eq(TEST_USER_ID), templateCaptor.capture());

        SmsTemplate resultTemplate = templateCaptor.getValue();
        assertEquals(resultTemplate.getMessage(), "This is a message");
    }

    @Test
    public void getActivityEventsForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());
        DynamoActivityEvent anEvent = new DynamoActivityEvent();
        anEvent.setEventId("event-id");
        List<ActivityEvent> events = ImmutableList.of(anEvent);
        when(mockParticipantService.getActivityEvents(app, null, TEST_USER_ID)).thenReturn(events);

        ResourceList<ActivityEvent> result = controller.getActivityEventsForWorker(TEST_APP_ID, TEST_USER_ID);

        verify(mockParticipantService).getActivityEvents(app, null, TEST_USER_ID);
        assertEquals(result.getItems().get(0).getEventId(), "event-id");
    }

    @Test
    public void getActivityHistoryForWorkerV2() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        ForwardCursorPagedResourceList<ScheduledActivity> cursor = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(ScheduledActivity.create()), "asdf");
        when(mockParticipantService.getActivityHistory(eq(app), eq(TEST_USER_ID), eq("activityGuid"), any(), any(), eq("asdf"),
                eq(50))).thenReturn(cursor);

        JsonNode result = controller.getActivityHistoryForWorkerV2(TEST_APP_ID, TEST_USER_ID,
                "activityGuid", START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq("activityGuid"), any(), any(),
                eq("asdf"), eq(50));

        ForwardCursorPagedResourceList<ScheduledActivity> retrieved = MAPPER
                .readValue(result.toString(), new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {});
        assertFalse(retrieved.getItems().isEmpty());
    }

    @Test
    public void getActivityHistoryForWorkerV3() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        ForwardCursorPagedResourceList<ScheduledActivity> cursor = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(ScheduledActivity.create()), "asdf");
        when(mockParticipantService.getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ActivityType.TASK), any(), any(),
                any(), eq("asdf"), eq(50))).thenReturn(cursor);

        String result = controller.getActivityHistoryForWorkerV3(TEST_APP_ID, TEST_USER_ID, "tasks",
                START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");

        verify(mockParticipantService).getActivityHistory(eq(app), eq(TEST_USER_ID), eq(ActivityType.TASK), any(), any(),
                any(), eq("asdf"), eq(50));

        ForwardCursorPagedResourceList<ScheduledActivity> retrieved = MAPPER.readValue(result,
                new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {});
        assertFalse(retrieved.getItems().isEmpty());
    }

    @Test
    public void deleteTestUserWorks() {
        participant = new StudyParticipant.Builder().copyOf(participant).withDataGroups(ImmutableSet.of("test_user"))
                .build();

        when(mockParticipantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(participant);
        controller.deleteTestParticipant(TEST_USER_ID);

        verify(mockUserAdminService).deleteUser(app, TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestUserNotAResearcher() {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .withId("notUserId").build();
        session.setParticipant(participant);

        controller.deleteTestParticipant(TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestUserNotATestAccount() {
        participant = new StudyParticipant.Builder().copyOf(participant).withDataGroups(EMPTY_SET).build();

        when(mockParticipantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(participant);
        controller.deleteTestParticipant(TEST_USER_ID);
    }

    @SuppressWarnings("deprecation")
    private <T> void verifyPagedResourceListParameters(JsonNode node) throws Exception {
        assertEquals(node.get("startTime").asText(), START_TIME.toString());
        assertEquals(node.get("endTime").asText(), END_TIME.toString());
        assertEquals(node.get("startDate").asText(), START_TIME.toString());
        assertEquals(node.get("endDate").asText(), END_TIME.toString());

        PagedResourceList<AccountSummary> page = MAPPER.readValue(node.toString(), ACCOUNT_SUMMARY_PAGE);

        assertEquals(page.getItems().size(), 3);
        assertEquals(page.getTotal(), (Integer) 30);
        assertEquals(page.getItems().get(0), SUMMARY1);

        assertDatesWithTimeZoneEqual(START_TIME, page.getStartTime());
        assertDatesWithTimeZoneEqual(END_TIME, page.getEndTime());
        assertEquals(page.getRequestParams().get("startTime"), START_TIME.toString());
        assertEquals(page.getRequestParams().get("endTime"), END_TIME.toString());

        // verify paging/filtering
        assertEquals(page.getRequestParams().get("offsetBy"), (Integer) 10);
        assertEquals(page.getRequestParams().get("pageSize"), 20);
        assertEquals(page.getRequestParams().get("emailFilter"), "foo");
    }

    @Test
    public void searchForAccountSummaries() throws Exception {
        AccountSummarySearch payload = setAccountSummarySearch();

        PagedResourceList<AccountSummary> result = controller.searchForAccountSummaries();

        assertEquals(result.getItems().size(), 3);

        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();

        assertEquals(search, payload);
    }

    @Test
    public void searchForAccountSummariesForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        AccountSummarySearch payload = setAccountSummarySearch();

        PagedResourceList<AccountSummary> result = controller.searchForAccountSummariesForWorker(app.getIdentifier());

        assertEquals(result.getItems().size(), 3);

        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search, payload);
    }
    
    @Test
    public void getEnrollments() {
        doReturn(session).when(controller).getAuthenticatedSession(false, RESEARCHER);
        
        List<EnrollmentDetail> list = ImmutableList.of();
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, null, TEST_USER_ID)).thenReturn(list);
        
        PagedResourceList<EnrollmentDetail> retValue = controller.getEnrollments(TEST_USER_ID);
        assertSame(retValue.getItems(), list);
    }
    
    @Test
    public void getActivityEvents() throws Exception {
        List<ActivityEvent> events = ImmutableList.of(new DynamoActivityEvent(), new DynamoActivityEvent());
        when(mockParticipantService.getActivityEvents(app, null, TEST_USER_ID)).thenReturn(events);        
        
        ResourceList<ActivityEvent> retList = controller.getActivityEvents(TEST_USER_ID);
        assertEquals(retList.getItems().size(), 2);
        
        verify(mockParticipantService).getActivityEvents(app, null, TEST_USER_ID);
    }
    
    @Test
    public void createCustomActivityEvent() throws Exception {
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey("eventKey")
                .withTimestamp(TIMESTAMP).build();
        mockRequestBody(mockRequest, request);
        
        StatusMessage retValue = controller.createCustomActivityEvent(TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Event recorded.");
        
        verify(mockParticipantService).createCustomActivityEvent(
                eq(app), eq(TEST_USER_ID), eventRequestCaptor.capture());
        CustomActivityEventRequest captured = eventRequestCaptor.getValue();
        assertEquals(captured.getEventKey(), "eventKey");
        assertEquals(captured.getTimestamp(), TIMESTAMP);
    }

    private AccountSummarySearch setAccountSummarySearch() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(100)
                .withEmailFilter("email").withPhoneFilter("phone").withAllOfGroups(ImmutableSet.of("group1"))
                .withNoneOfGroups(ImmutableSet.of("group2")).withLanguage("en").withStartTime(START_TIME)
                .withEndTime(END_TIME).build();
        mockRequestBody(mockRequest, search);
        return search;
    }

    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2(String offsetKey, int pageSize) {
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(ACTIVITY_1);
        activity.setHealthCode(HEALTH_CODE);
        activity.setSchedulePlanGuid("schedulePlanGuid");
        List<ScheduledActivity> list = ImmutableList.of(activity);

        return new ForwardCursorPagedResourceList<>(list, null).withRequestParam("pageSize", pageSize)
                .withRequestParam("offsetKey", offsetKey);
    }
}
