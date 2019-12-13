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
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
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
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
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
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class ParticipantControllerTest extends Mockito {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();

    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };

    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>() {
    };

    private static final Set<Roles> CALLER_ROLES = ImmutableSet.of(RESEARCHER);

    private static final Set<String> CALLER_SUBS = ImmutableSet.of("substudyA");

    private static final String ACTIVITY_GUID = ACTIVITY_1.getGuid();

    private static final DateTime START_TIME = TIMESTAMP.minusHours(3);

    private static final DateTime END_TIME = TIMESTAMP;

    private static final Set<String> EMPTY_SET = ImmutableSet.of();

    private static final AccountSummary SUMMARY = new AccountSummary("firstName", "lastName", EMAIL,
            SYNAPSE_USER_ID, PHONE, ImmutableMap.of("substudyA", "externalId"), USER_ID, TIMESTAMP,
            ENABLED, TEST_STUDY, EMPTY_SET);

    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withStudy(TEST_STUDY_IDENTIFIER).withPhone(PHONE)
            .withPassword(PASSWORD).build();
    private static final IdentifierUpdate PHONE_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            PHONE, null, null);
    private static final IdentifierUpdate EMAIL_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST,
            EMAIL, null, null, null);
    private static final IdentifierUpdate EXTID_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST, null,
            null, "some-new-extid", null);
    private static final IdentifierUpdate SYNAPSE_ID_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            null, null, SYNAPSE_USER_ID);

    @InjectMocks
    @Spy
    ParticipantController controller;

    @Mock
    ConsentService mockConsentService;

    @Mock
    ParticipantService mockParticipantService;

    @Mock
    StudyService mockStudyService;

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    CacheProvider mockCacheProvider;

    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
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
    ArgumentCaptor<CriteriaContext> contextCaptor;

    @Captor
    ArgumentCaptor<IdentifierUpdate> identifierUpdateCaptor;

    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;

    @Captor
    ArgumentCaptor<SmsTemplate> templateCaptor;
    
    UserSession session;

    Study study;

    StudyParticipant participant;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        study = new DynamoStudy();
        study.setUserProfileAttributes(Sets.newHashSet("foo", "baz"));
        study.setIdentifier(TEST_STUDY_IDENTIFIER);

        participant = new StudyParticipant.Builder().withRoles(CALLER_ROLES).withSubstudyIds(CALLER_SUBS)
                .withId(USER_ID).build();

        session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(participant);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        List<AccountSummary> summaries = ImmutableList.of(SUMMARY, SUMMARY, SUMMARY);
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(summaries, 30).withRequestParam("offsetBy", 10)
                .withRequestParam("pageSize", 20).withRequestParam("startTime", START_TIME)
                .withRequestParam("endTime", END_TIME).withRequestParam("emailFilter", "foo");

        when(mockParticipantService.getPagedAccountSummaries(eq(study), any())).thenReturn(page);

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
        BridgeUtils.setRequestContext(null);
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
        assertPost(ParticipantController.class, "withdrawFromStudy");
        assertPost(ParticipantController.class, "withdrawConsent");
        assertGet(ParticipantController.class, "getUploads");
        assertGet(ParticipantController.class, "getNotificationRegistrations");
        assertAccept(ParticipantController.class, "sendNotification");
        assertGet(ParticipantController.class, "getActivityEvents");
        assertAccept(ParticipantController.class, "sendSmsMessageForWorker");
    }

    @Test
    public void createSmsNotificationRegistration() throws Exception {
        // Requires researcher role.
        session.setParticipant(
                new StudyParticipant.Builder().copyOf(session.getParticipant()).withRoles(CALLER_ROLES).build());

        // Execute.
        StatusMessage result = controller.createSmsRegistration(USER_ID);
        assertEquals(result.getMessage(), "SMS notification registration created");

        // Verify dependent services.
        verify(mockParticipantService).createSmsRegistration(study, USER_ID);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipants() throws Exception {
        JsonNode result = controller.getParticipants("10", "20", "emailSubstring", "phoneSubstring",
                START_TIME.toString(), END_TIME.toString(), null, null);

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

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
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());
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
        study.setHealthCodeExportEnabled(true);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).build();

        when(mockParticipantService.getParticipant(study, USER_ID, true)).thenReturn(studyParticipant);

        String json = controller.getParticipant(USER_ID, true);
        JsonNode node = MAPPER.readTree(json);

        // StudyParticipant will encrypt the healthCode when you ask for it, so validate the
        // JSON itself.
        assertTrue(node.has("firstName"));
        assertTrue(node.has("healthCode"));
        assertFalse(node.has("encryptedHealthCode"));

        verify(mockParticipantService).getParticipant(study, USER_ID, true);
    }

    @Test
    public void getParticipantWithNoHealthCode() throws Exception {
        study.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(study, USER_ID, true)).thenReturn(studyParticipant);

        String json = controller.getParticipant(USER_ID, true);

        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);

        assertEquals(retrievedParticipant.getFirstName(), "Test");
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantWhereHealthCodeIsPrevented() throws Exception {
        study.setHealthCodeExportEnabled(false);
        
        controller.getParticipant("healthCode:"+USER_ID, true);
    }
    
    @Test
    public void getParticipantWithHealthCodeIfAdmin() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of(RESEARCHER, ADMIN)).build();
        session.setParticipant(participant);
        
        study.setHealthCodeExportEnabled(false);
        
        controller.getParticipant("healthCode:"+USER_ID, true);
    }
    
    @Test
    public void getParticipantForWorkerWithHealthCodeNotPrevented() throws Exception {
        // The caller is a worker
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        // Health codes are disabled
        study.setHealthCodeExportEnabled(false);
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withHealthCode(HEALTH_CODE).build();
        when(mockParticipantService.getParticipant(study, "healthCode:" + USER_ID, true)).thenReturn(studyParticipant);
        
        // You can still retrieve the user with a health code
        String result = controller.getParticipantForWorker(TEST_STUDY_IDENTIFIER, "healthCode:"+USER_ID, true);
        assertNotNull(result);
    }

    @Test
    public void signUserOut() throws Exception {
        StatusMessage result = controller.signOut(USER_ID, false);
        assertEquals(result.getMessage(), "User signed out.");

        verify(mockParticipantService).signUserOut(study, USER_ID, false);
    }

    
    @Test
    public void updateParticipant() throws Exception {
        study.getUserProfileAttributes().add("can_be_recontacted");
        String json = createJson("{'firstName':'firstName'," + "'lastName':'lastName'," + "'email':'email@email.com',"
                + "'externalId':'externalId'," + "'password':'newUserPassword',"
                + "'sharingScope':'sponsors_and_partners'," + "'notifyByEmail':true,"
                + "'dataGroups':['group2','group1']," + "'attributes':{'can_be_recontacted':'true'},"
                + "'languages':['en','fr']}");

        mockRequestBody(mockRequest, json);

        StatusMessage result = controller.updateParticipant(USER_ID);
        assertEquals(result.getMessage(), "Participant updated.");

        // Both the caller roles and the caller's substudies are passed to participantService
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());

        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getId(), USER_ID);
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
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

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
        doReturn(holder).when(mockParticipantService).createParticipant(eq(study), any(StudyParticipant.class),
                eq(true));

        IdentifierHolder result = controller.createParticipant();

        assertEquals(result.getIdentifier(), USER_ID);

        verify(mockParticipantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));

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
                .withTimeZone(DateTimeZone.forOffsetHours(-7)).withStudyIdentifier(TEST_STUDY).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        RequestInfo result = controller.getRequestInfo("userId");

        // serialization was tested separately... just validate the object is there
        assertEquals(result, requestInfo);
    }

    @Test
    public void getParticipantRequestInfoIsNullsafe() throws Exception {
        // There is no request info.
        RequestInfo result = controller.getRequestInfo("userId");

        assertNotNull(result); // values are all null, but object is returned
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantRequestInfoOnlyReturnsCurrentStudyInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(new StudyIdentifierImpl("some-other-study")).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        controller.getRequestInfo("userId");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantRequestInfoForWorkerOnly() throws Exception {
        controller.getRequestInfoForWorker(study.getIdentifier(), USER_ID);
    }
    
    @Test
    public void getParticipantRequestInfoForWorker() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7)).withStudyIdentifier(TEST_STUDY).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        RequestInfo result = controller.getRequestInfoForWorker(study.getIdentifier(), "userId");

        assertEquals(result, requestInfo);
    }
    
    @Test
    public void getParticipantRequestInfoForWorkerIsNullsafe() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        // There is no request info.
        RequestInfo result = controller.getRequestInfoForWorker(study.getIdentifier(), "userId");

        assertNotNull(result); // values are all null, but object is returned
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantRequestInfoForWorkerOnlyReturnsCurrentStudyInfo() throws Exception {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(WORKER)).build();
        session.setParticipant(participant);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(new StudyIdentifierImpl("some-other-study")).build();

        doReturn(requestInfo).when(mockRequestInfoService).getRequestInfo("userId");
        controller.getRequestInfoForWorker(study.getIdentifier(), "userId");
    }
    
    private IdentifierHolder setUpCreateParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder(USER_ID);

        study.getUserProfileAttributes().add("phone");
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

        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());

        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals(persisted.getId(), "id1");
    }

    @Test
    public void getSelfParticipantNoConsentHistories() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withId(USER_ID)
                .withEncryptedHealthCode(ENCRYPTED_HEALTH_CODE).withFirstName("Test").build();

        when(mockParticipantService.getSelfParticipant(eq(study), any(), eq(false))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(false);

        verify(mockParticipantService).getSelfParticipant(eq(study), contextCaptor.capture(), eq(false));
        assertEquals(contextCaptor.getValue().getUserId(), USER_ID);

        StudyParticipant deserParticipant = MAPPER.readValue(result, StudyParticipant.class);

        assertEquals(deserParticipant.getFirstName(), "Test");
        assertNull(deserParticipant.getHealthCode());
        assertNull(deserParticipant.getEncryptedHealthCode());
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

        when(mockParticipantService.getSelfParticipant(eq(study), any(), eq(true))).thenReturn(studyParticipant);

        String result = controller.getSelfParticipant(true);

        verify(mockParticipantService).getSelfParticipant(eq(study), any(), eq(true));

        JsonNode nodeParticipant = MAPPER.readTree(result);
        JsonNode nodeHistory = nodeParticipant.get("consentHistories").get("guid").get(0);
        // Verify these are formatted correctly. These are just the unusual fields that required an annotation
        assertEquals(nodeHistory.get("consentCreatedOn").textValue(), timestamp.toString());
        assertEquals(nodeHistory.get("signedOn").textValue(), timestamp.toString());
        assertEquals(nodeHistory.get("withdrewOn").textValue(), timestamp.toString());

        StudyParticipant deserParticipant = MAPPER.treeToValue(nodeParticipant, StudyParticipant.class);

        assertEquals(deserParticipant.getFirstName(), "Test");
        assertNull(deserParticipant.getHealthCode());
        assertNull(deserParticipant.getEncryptedHealthCode());

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
    public void updateSelfParticipant() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress(IP_ADDRESS)
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyB")).build());

        // All values should be copied over here, also add a healthCode to verify it is not unset.
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(TestUtils.getStudyParticipant(ParticipantControllerTest.class)).withId(USER_ID)
                .withLanguages(LANGUAGES).withRoles(ImmutableSet.of(DEVELOPER)) // <-- should not be passed along
                .withDataGroups(USER_DATA_GROUPS).withSubstudyIds(USER_SUBSTUDY_IDS)
                .withHealthCode(HEALTH_CODE).build();
        session.setParticipant(participant);
        session.setIpAddress(IP_ADDRESS); // if this is not the same as request, you get an authentication error

        doReturn(participant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());

        String json = MAPPER.writeValueAsString(participant);
        mockRequestBody(mockRequest, json);

        JsonNode result = controller.updateSelfParticipant();

        assertEquals(result.get("type").asText(), "UserSessionInfo");
        assertNull(result.get("healthCode"));

        // verify the object is passed to service, one field is sufficient
        verify(mockCacheProvider).setUserSession(any());
        
        InOrder inOrder = inOrder(mockParticipantService);
        inOrder.verify(mockParticipantService).getParticipant(study, USER_ID, false);
        // No roles are passed in this method, and the substudies of the user are passed
        inOrder.verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        inOrder.verify(mockParticipantService).getParticipant(study, USER_ID, true);
        
        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), USER_ID);
        assertEquals(captured.getFirstName(), "FirstName");
        assertEquals(captured.getSharingScope(), ALL_QUALIFIED_RESEARCHERS);
        assertTrue(captured.isNotifyByEmail());
        assertEquals(captured.getDataGroups(), USER_DATA_GROUPS);
        assertEquals(captured.getSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(captured.getAttributes().get("can_be_recontacted"), "true");

        verify(mockConsentService).getConsentStatuses(contextCaptor.capture());
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getStudyIdentifier(), TEST_STUDY);
        assertEquals(context.getHealthCode(), HEALTH_CODE);
        assertEquals(context.getUserId(), USER_ID);
        assertEquals(context.getClientInfo(), ClientInfo.UNKNOWN_CLIENT);
        assertEquals(context.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(context.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(context.getLanguages(), LANGUAGES);
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
        doReturn(participant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());

        String json = createJson("{'externalId':'simpleStringChange'," + "'sharingScope':'no_sharing',"
                + "'notifyByEmail':false," + "'attributes':{'baz':'belgium'}," + "'languages':['fr'],"
                + "'status':'enabled'," + "'roles':['admin']}");
        mockRequestBody(mockRequest, json);

        JsonNode result = controller.updateSelfParticipant();
        assertEquals("UserSessionInfo", result.get("type").asText());

        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), USER_ID);
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
        doReturn(participant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());

        String json = createJson("{'sharingScope':'all_qualified_researchers'}");
        mockRequestBody(mockRequest, json);

        controller.updateSelfParticipant();

        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getSharingScope(), NO_SHARING);
    }

    @Test
    public void participantUpdateSelfWithNullSharingDoesNotClearSharing() throws Exception {
        // It's not a matter of consent... the user is consented:
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        
        StudyParticipant participant = new StudyParticipant.Builder().withSharingScope(ALL_QUALIFIED_RESEARCHERS).build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());

        String json = createJson("{}");
        mockRequestBody(mockRequest, json);

        controller.updateSelfParticipant();

        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getSharingScope(), ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        StatusMessage result = controller.requestResetPassword(USER_ID);
        assertEquals(result.getMessage(), "Request to reset password sent to user.");

        verify(mockParticipantService).requestResetPassword(study, USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotResetPasswordIfNotResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.DEVELOPER)).build();
        session.setParticipant(participant);

        controller.requestResetPassword(USER_ID);
    }

    @Test
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(USER_ID).build();
        doReturn(participant).when(mockParticipantService).getParticipant(eq(study), eq(USER_ID), anyBoolean());

        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();

        mockRequestBody(mockRequest, participant);

        JsonNode result = controller.updateSelfParticipant();
        assertEquals(result.get("type").asText(), "UserSessionInfo");

        // verify the object is passed to service, one field is sufficient
        verify(mockParticipantService).updateParticipant(eq(study), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), USER_ID);
    }

    @Test
    public void canGetActivityHistoryV2() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(study),
                eq(USER_ID), eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));

        JsonNode result = controller.getActivityHistoryV2(USER_ID, ACTIVITY_GUID, START_TIME.toString(),
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

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityHistoryV2WithOffsetKey() throws Exception {
        doReturn(createActivityResultsV2("200", 77)).when(mockParticipantService).getActivityHistory(eq(study),
                eq(USER_ID), eq(ACTIVITY_GUID), any(), any(), eq("200"), eq(77));

        JsonNode result = controller.getActivityHistoryV2(USER_ID, ACTIVITY_GUID, START_TIME.toString(),
                END_TIME.toString(), null, "200", "77");
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(result.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);

        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals(activity.getSchedulePlanGuid(), "schedulePlanGuid");
        assertNull(activity.getHealthCode());

        assertEquals(page.getItems().size(), 1); // have not mocked out these items, but the list is there.
        assertEquals(page.getRequestParams().get("pageSize"), 77);
        assertEquals(page.getRequestParams().get("offsetKey"), "200");

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(START_TIME.isEqual(startsOnCaptor.getValue()));
        assertTrue(END_TIME.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityV2WithNullValues() throws Exception {
        doReturn(createActivityResultsV2(null, API_DEFAULT_PAGE_SIZE)).when(mockParticipantService).getActivityHistory(
                eq(study), eq(USER_ID), eq(ACTIVITY_GUID), any(), any(), eq(null), eq(API_DEFAULT_PAGE_SIZE));

        JsonNode result = controller.getActivityHistoryV2(USER_ID, ACTIVITY_GUID, null, null, null, null, null);
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(result.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);

        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals(activity.getSchedulePlanGuid(), "schedulePlanGuid");
        assertNull(activity.getHealthCode());
        assertEquals(page.getItems().size(), 1); // have not mocked out these items, but the list is there.
        assertEquals(page.getRequestParams().get("pageSize"), API_DEFAULT_PAGE_SIZE);

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(ACTIVITY_GUID), eq(null), eq(null),
                eq(null), eq(API_DEFAULT_PAGE_SIZE));
    }

    @Test
    public void deleteActivities() throws Exception {
        StatusMessage result = controller.deleteActivities(USER_ID);
        assertEquals(result.getMessage(), "Scheduled activities deleted.");

        verify(mockParticipantService).deleteActivities(study, USER_ID);
    }

    @Test
    public void resendEmailVerification() throws Exception {
        StatusMessage result = controller.resendEmailVerification(USER_ID);
        assertEquals(result.getMessage(), "Email verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(study, ChannelType.EMAIL, USER_ID);
    }

    @Test
    public void resendPhoneVerification() throws Exception {
        StatusMessage result = controller.resendPhoneVerification(USER_ID);
        assertEquals(result.getMessage(), "Phone verification request has been resent to user.");

        verify(mockParticipantService).resendVerification(study, ChannelType.PHONE, USER_ID);
    }

    @Test
    public void resendConsentAgreement() throws Exception {
        StatusMessage result = controller.resendConsentAgreement(USER_ID, SUBPOP_GUID.getGuid());
        assertEquals(result.getMessage(), "Consent agreement resent to user.");

        verify(mockParticipantService).resendConsentAgreement(study, SUBPOP_GUID, USER_ID);
    }

    @Test
    public void withdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            mockRequestBody(mockRequest, json);

            controller.withdrawFromStudy(USER_ID);

            verify(mockParticipantService).withdrawFromStudy(study, USER_ID, new Withdrawal("Because, reasons."),
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

            controller.withdrawConsent(USER_ID, SUBPOP_GUID.getGuid());

            verify(mockParticipantService).withdrawConsent(study, USER_ID, SUBPOP_GUID,
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
        doReturn(uploads).when(mockParticipantService).getUploads(study, USER_ID, startTime, endTime, 10, "abc");

        ForwardCursorPagedResourceList<UploadView> result = controller.getUploads(USER_ID, startTime.toString(),
                endTime.toString(), 10, "abc");

        verify(mockParticipantService).getUploads(study, USER_ID, startTime, endTime, 10, "abc");

        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test
    public void getUploadsNullsDateRange() throws Exception {
        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE);
        doReturn(uploads).when(mockParticipantService).getUploads(study, USER_ID, null, null, null, null);

        controller.getUploads(USER_ID, null, null, null, null);

        verify(mockParticipantService).getUploads(study, USER_ID, null, null, null, null);
    }

    @Test
    public void getNotificationRegistrations() throws Exception {
        List<NotificationRegistration> list = ImmutableList.of();
        doReturn(list).when(mockParticipantService).listRegistrations(study, USER_ID);

        ResourceList<NotificationRegistration> result = controller.getNotificationRegistrations(USER_ID);

        JsonNode node = MAPPER.valueToTree(result);
        assertEquals(node.get("items").size(), 0);
        assertEquals(node.get("type").asText(), "ResourceList");

        verify(mockParticipantService).listRegistrations(study, USER_ID);
    }

    @Test
    public void sendMessage() throws Exception {
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        StatusMessage result = controller.sendNotification(USER_ID);

        assertEquals(result.getMessage(), "Message has been sent to external notification service.");

        verify(mockParticipantService).sendNotification(eq(study), eq(USER_ID), messageCaptor.capture());

        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(captured.getSubject(), "a subject");
        assertEquals(captured.getMessage(), "a message");
    }

    @Test
    public void sendMessageWithSomeErrors() throws Exception {
        Set<String> erroredRegistrations = ImmutableSet.of("123", "456");
        when(mockParticipantService.sendNotification(study, USER_ID, NOTIFICATION_MESSAGE))
                .thenReturn(erroredRegistrations);
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        StatusMessage result = controller.sendNotification(USER_ID);

        assertEquals(result.getMessage(),
                "Message has been sent to external notification service. Some registrations returned errors: 123, 456.");
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantsForWorkerOnly() throws Exception {
        DateTime start = DateTime.now();
        DateTime end = DateTime.now();

        controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring", "phoneSubstring",
                start.toString(), end.toString(), null, null);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getParticipantForWorkerOnly() throws Exception {
        controller.getParticipantForWorker(study.getIdentifier(), USER_ID, true);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getParticipantsForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        JsonNode result = controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", START_TIME.toString(), END_TIME.toString(), null, null);

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

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

        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        JsonNode result = controller.getParticipantsForWorker(study.getIdentifier(), "10", "20", "emailSubstring",
                "phoneSubstring", null, null, START_TIME.toString(), END_TIME.toString());

        verifyPagedResourceListParameters(result);

        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

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

        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(USER_ID).withHealthCode(HEALTH_CODE)
                .build();

        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, USER_ID, true)).thenReturn(foundParticipant);

        String result = controller.getParticipantForWorker(study.getIdentifier(), USER_ID, true);

        JsonNode participantNode = MAPPER.readTree(result);
        assertEquals(participantNode.get("healthCode").textValue(), HEALTH_CODE);
        assertNull(participantNode.get("encryptedHealthCode"));
        assertEquals(participantNode.get("id").textValue(), USER_ID);

        verify(mockParticipantService).getParticipant(study, USER_ID, true);
    }

    @Test
    public void getActivityHistoryV3() throws Exception {
        doReturn(createActivityResultsV2("offsetKey", 15)).when(mockParticipantService).getActivityHistory(eq(study),
                eq(USER_ID), eq(ActivityType.SURVEY), eq("referentGuid"), any(), any(), eq("offsetKey"), eq(15));

        String result = controller.getActivityHistoryV3(USER_ID, "surveys", "referentGuid", START_TIME.toString(),
                END_TIME.toString(), "offsetKey", "15");

        JsonNode node = MAPPER.readTree(result);
        assertEquals(node.get("requestParams").get("pageSize").intValue(), 15);
        assertEquals(node.get("requestParams").get("offsetKey").asText(), "offsetKey");

        // The fact this can be converted to a forward cursor object is ideal
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(node.toString(),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        assertEquals((int) page.getRequestParams().get("pageSize"), 15);

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(ActivityType.SURVEY),
                eq("referentGuid"), startTimeCaptor.capture(), endTimeCaptor.capture(), eq("offsetKey"), eq(15));
        assertEquals(startTimeCaptor.getValue().toString(), START_TIME.toString());
        assertEquals(endTimeCaptor.getValue().toString(), END_TIME.toString());
    }

    @Test
    public void getActivityHistoryV3DefaultsToNulls() throws Exception {
        controller.getActivityHistoryV3(USER_ID, "badtypes", null, null, null, null, null);

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(null), eq(null),
                startTimeCaptor.capture(), endTimeCaptor.capture(), eq(null),
                eq(BridgeConstants.API_DEFAULT_PAGE_SIZE));
        assertNull(startTimeCaptor.getValue());
        assertNull(endTimeCaptor.getValue());
    }

    @Test
    public void updateIdentifiersWithPhone() throws Exception {
        mockRequestBody(mockRequest, PHONE_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(),
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

        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getPhone(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone());
        assertEquals(update.getSignIn().getPassword(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getEmailUpdate(), EMAIL);
        assertNull(update.getPhoneUpdate());
    }

    @Test
    public void updateIdentifiersWithExternalId() throws Exception {
        mockRequestBody(mockRequest, EXTID_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getPhone(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone());
        assertEquals(update.getSignIn().getPassword(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getExternalIdUpdate(), "some-new-extid");
        assertNull(update.getPhoneUpdate());
    }
    
    @Test
    public void updateIdentifiersWithSynapseUserId() throws Exception {
        mockRequestBody(mockRequest, SYNAPSE_ID_UPDATE);

        when(mockParticipantService.updateIdentifiers(eq(study), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(study), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getEmail(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail());
        assertEquals(update.getSignIn().getPassword(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getSynapseUserIdUpdate(), SYNAPSE_USER_ID);
        assertNull(update.getExternalIdUpdate());
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
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(studyParticipant);

        controller.getParticipant(USER_ID, false);

        verify(mockParticipantService).getParticipant(study, USER_ID, false);
    }

    @Test
    public void getParticipantForWorkerNoConsents() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        StudyParticipant foundParticipant = new StudyParticipant.Builder().withId(USER_ID).build();

        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(foundParticipant);

        controller.getParticipantForWorker(study.getIdentifier(), USER_ID, false);

        verify(mockParticipantService).getParticipant(study, USER_ID, false);
    }

    @Test
    public void sendSMSForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        mockRequestBody(mockRequest, new SmsTemplate("This is a message"));

        StatusMessage result = controller.sendSmsMessageForWorker(TEST_STUDY_IDENTIFIER, USER_ID);

        assertEquals(result.getMessage(), "Message sent.");
        verify(mockParticipantService).sendSmsMessage(eq(study), eq(USER_ID), templateCaptor.capture());

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
        when(mockParticipantService.getActivityEvents(study, USER_ID)).thenReturn(events);

        ResourceList<ActivityEvent> result = controller.getActivityEventsForWorker(TEST_STUDY_IDENTIFIER, USER_ID);

        verify(mockParticipantService).getActivityEvents(study, USER_ID);
        assertEquals(result.getItems().get(0).getEventId(), "event-id");
    }

    @Test
    public void getActivityHistoryForWorkerV2() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        ForwardCursorPagedResourceList<ScheduledActivity> cursor = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(ScheduledActivity.create()), "asdf");
        when(mockParticipantService.getActivityHistory(eq(study), eq(USER_ID), eq("activityGuid"), any(), any(), eq("asdf"),
                eq(50))).thenReturn(cursor);

        JsonNode result = controller.getActivityHistoryForWorkerV2(TEST_STUDY_IDENTIFIER, USER_ID,
                "activityGuid", START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq("activityGuid"), any(), any(),
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
        when(mockParticipantService.getActivityHistory(eq(study), eq(USER_ID), eq(ActivityType.TASK), any(), any(),
                any(), eq("asdf"), eq(50))).thenReturn(cursor);

        String result = controller.getActivityHistoryForWorkerV3(TEST_STUDY_IDENTIFIER, USER_ID, "tasks",
                START_TIME.toString(), END_TIME.toString(), null, "asdf", "50");

        verify(mockParticipantService).getActivityHistory(eq(study), eq(USER_ID), eq(ActivityType.TASK), any(), any(),
                any(), eq("asdf"), eq(50));

        ForwardCursorPagedResourceList<ScheduledActivity> retrieved = MAPPER.readValue(result,
                new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {});
        assertFalse(retrieved.getItems().isEmpty());
    }

    @Test
    public void deleteTestUserWorks() {
        participant = new StudyParticipant.Builder().copyOf(participant).withDataGroups(ImmutableSet.of("test_user"))
                .build();

        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(participant);
        controller.deleteTestParticipant(USER_ID);

        verify(mockUserAdminService).deleteUser(study, USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestUserNotAResearcher() {
        participant = new StudyParticipant.Builder().copyOf(participant).withRoles(ImmutableSet.of(Roles.ADMIN))
                .build();
        session.setParticipant(participant);

        controller.deleteTestParticipant(USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestUserNotATestAccount() {
        participant = new StudyParticipant.Builder().copyOf(participant).withDataGroups(EMPTY_SET).build();

        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(participant);
        controller.deleteTestParticipant(USER_ID);
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
        assertEquals(page.getItems().get(0), SUMMARY);

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

        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();

        assertEquals(search, payload);
    }

    @Test
    public void searchForAccountSummariesForWorker() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.WORKER)).build());

        AccountSummarySearch payload = setAccountSummarySearch();

        PagedResourceList<AccountSummary> result = controller.searchForAccountSummariesForWorker(study.getIdentifier());

        assertEquals(result.getItems().size(), 3);

        verify(mockParticipantService).getPagedAccountSummaries(eq(study), searchCaptor.capture());

        AccountSummarySearch search = searchCaptor.getValue();
        assertEquals(search, payload);
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
