package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.STRING_SET_TYPEREF;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_ACCEPT_LANGUAGE;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID_WITH_HEALTHCODE;
import static org.sagebionetworks.bridge.TestConstants.CONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.IP_ADDRESS;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.REQUEST_ID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_TOKEN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.TIMEZONE_MSK;
import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.sagebionetworks.bridge.TestConstants.UNCONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.SponsorService;
import org.sagebionetworks.bridge.services.AppService;

public class BaseControllerTest extends Mockito {

    @Mock
    private CacheProvider mockCacheProvider;

    @Mock
    private BridgeConfig mockBridgeConfig;

    @Mock
    private AccountService mockAccountService;

    @Mock
    private AppService mockAppService;

    @Mock
    private AuthenticationService mockAuthenticationService;

    @Mock
    private SessionUpdateService mockSessionUpdateService;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private RequestInfoService requestInfoService;
    
    @Mock
    private SponsorService mockSponsorService;

    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    private ArgumentCaptor<Cookie> cookieCaptor;
    
    @Captor
    private ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @InjectMocks
    @Spy
    private BaseController controller = new BaseController() {
    };

    UserSession session;

    App app;

    @BeforeMethod
    private void before() {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();

        session = new UserSession();
        app = App.create();
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA))
                .withCallerLanguages(ImmutableList.of("en", "fr")).build());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        RequestContext.set(null);
    }

    @Test
    public void getSessionIfItExistsReturnsNull() {
        // No session token... no session
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExists() {
        session.setAppId(TEST_APP_ID);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);

        UserSession returnedSession = controller.getSessionIfItExists();
        assertEquals(returnedSession, session);

        verify(mockRequest).setAttribute(eq(BaseController.USER_SESSION_FLAG), any(UserSession.class));
    }

    @Test
    public void getAuthenticatedAndConsentedSession() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UserSession returnedSession = controller.getAuthenticatedAndConsentedSession();
        assertEquals(session, returnedSession);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedAndConsentedSessionNotAuthenticated() {
        controller.getAuthenticatedAndConsentedSession();
    }

    @Test(expectedExceptions = ConsentRequiredException.class)
    public void getAuthenticatedAndConsentedSessionNotConsented() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        controller.getAuthenticatedAndConsentedSession();
    }

    @Test
    public void getAuthenticatedSessionRolesSucceeds() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .withHealthCode(HEALTH_CODE).build());
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        UserSession retrievedSession = controller.getAuthenticatedSession(Roles.DEVELOPER);
        assertEquals(session, retrievedSession);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAuthenticatedSessionRolesFailsRolesMismatched() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN))
                .withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        controller.getAuthenticatedSession(Roles.DEVELOPER);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAuthenticatedSessionRolesFailsNoCallerRole() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        controller.getAuthenticatedSession(Roles.DEVELOPER);
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticationNoSession() {
        controller.getAuthenticatedSession();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticationNotAuthenticated() {
        app.setParticipantIpLockingEnabled(false);
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        session.setAuthenticated(false);
        
        controller.getAuthenticatedSession();
    }

    @Test
    public void getSessionEitherConsentedOrInRoleSucceedsOnRole() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .withHealthCode(HEALTH_CODE).build());
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.RESEARCHER);
        assertEquals(session, retrievedSession);
    }
    
    @Test
    public void getSessionEitherConsentedOrInRoleSucceedsOnConsent() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.RESEARCHER);
        assertEquals(session, retrievedSession);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSessionEitherConsentedOrInRoleFails() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withHealthCode(HEALTH_CODE).build());
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.ADMIN);
        assertEquals(session, retrievedSession);
    }

    @Test
    public void verifySupportedVersionOrThrowExceptionPasses() {
        app.setMinSupportedAppVersions(ImmutableMap.of("iPhone OS", 10));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test
    public void verifySupportedVersionOrThrowExceptionWithNoValuePasses() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void verifySupportedVersionOrThrowExceptionFails() {
        app.setMinSupportedAppVersions(ImmutableMap.of("iPhone OS", 30));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(app);
    }

    @Test
    public void getLanguagesInits() {
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
                .thenReturn("fr-fr;q=0.4,fr;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
        
        controller.getLanguages(session);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID_WITH_HEALTHCODE), any());
        verify(mockSessionUpdateService).updateLanguage(eq(session), contextCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getLanguages(), LANGUAGES);
    }
    
    @Test
    public void getLanguagesDoesNotOverwrite() {
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withLanguages(ImmutableList.of("fr"))
                .withId(TEST_USER_ID).build());
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
                .thenReturn("de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
        
        List<String> returnedLangs = controller.getLanguages(session);
        assertEquals(returnedLangs, ImmutableList.of("fr"));
        
        verify(mockAccountService, never()).editAccount(any(), any());
        verify(mockSessionUpdateService, never()).updateLanguage(any(), any());
    }

    @Test
    public void getCriteriaContextWithAppId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA))
                .withCallerLanguages(ImmutableList.of("en"))
                .build());
        when(mockRequest.getHeader(BridgeConstants.X_FORWARDED_FOR_HEADER)).thenReturn(IP_ADDRESS);
        
        CriteriaContext context = controller.getCriteriaContext(TEST_APP_ID);
        
        assertEquals(context.getAppId(), TEST_APP_ID);
        assertEquals(context.getLanguages(), ImmutableList.of("en"));
        assertEquals(context.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
    }

    @Test
    public void getCriteriaContextWithSession() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        session.setAppId(TEST_APP_ID);
        session.setIpAddress(IP_ADDRESS);
        session.setParticipant(new StudyParticipant.Builder()
                .withDataGroups(USER_DATA_GROUPS).withStudyIds(USER_STUDY_IDS)
                .withLanguages(LANGUAGES).withHealthCode(HEALTH_CODE).withId(TEST_USER_ID).build());
        
        CriteriaContext context = controller.getCriteriaContext(session);
        
        assertEquals(context.getLanguages(), LANGUAGES);
        assertEquals(context.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
        assertEquals(context.getHealthCode(), HEALTH_CODE);
        assertEquals(context.getUserId(), TEST_USER_ID);
        assertEquals(context.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(context.getUserStudyIds(), USER_STUDY_IDS);
        assertEquals(context.getAppId(), TEST_APP_ID);
    }

    @Test
    public void parseJsonSucceeds() throws Exception {
        String json = TestUtils.createJson("{'eventKey':'foo','timestamp':'%s','objectType':'custom'}", TIMESTAMP.toString());
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        CustomActivityEventRequest request = controller.parseJson(CustomActivityEventRequest.class);
        assertEquals(request.getEventKey(), "foo");
        assertEquals(request.getTimestamp(), TIMESTAMP);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void parseJsonFails() throws Exception {
        String json = TestUtils.createJson("{'not json'}", TIMESTAMP.toString());
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        controller.parseJson(CustomActivityEventRequest.class);
    }
    
    @Test(expectedExceptionsMessageRegExp = "startDate can't be after endDate", expectedExceptions = InvalidEntityException.class)
    public void parseJsonCanThrowInvalidEntityExceptionFromBuilder() throws Exception {
        String json = TestUtils.createJson(
                "{'startDate':'2019-10-10','endDate':'2018-10-10'}");
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        controller.parseJson(DateRange.class);
    }
    
    @Test
    public void parseJsonWithTypeReference() throws Exception { 
        String json = TestUtils.createJson("['A', 'B', 'C']");
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        Set<String> set = controller.parseJson(STRING_SET_TYPEREF);
        
        assertEquals(set, ImmutableSet.of("A", "B", "C"));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void parseJsonWithTypeReferenceFails() throws Exception {
        String json = TestUtils.createJson("{'A', 'B', 'C'}"); // not JSON
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        controller.parseJson(STRING_SET_TYPEREF);
    }
    
    @Test
    public void getMetrics() {
        Metrics metrics = new Metrics("a-request-id");
        
        RequestContext.set(
                new RequestContext.Builder().withMetrics(metrics).withRequestId("a-request-id").build());
        
        Metrics retrievedMetrics = controller.getMetrics();
        assertEquals(retrievedMetrics, metrics);
    }
    
    @Test
    public void updateRequestInfoFromSessionTest() {
        session.setSessionToken(SESSION_TOKEN);
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        controller.updateRequestInfoFromSession(session);

        verify(requestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        assertEquals(TIMESTAMP, requestInfoCaptor.getValue().getSignedInOn());
    }

    @Test
    public void getRequestInfoBuilder() {
        RequestInfo existingInfo = new RequestInfo.Builder().withActivitiesAccessedOn(TIMESTAMP).build();
        when(requestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(existingInfo);
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).withLanguages(LANGUAGES)
                .withDataGroups(USER_DATA_GROUPS).withStudyIds(USER_STUDY_IDS)
                .withTimeZone(TIMEZONE_MSK).build());

        RequestInfo info = controller.getRequestInfoBuilder(session).build();
        assertEquals(info.getActivitiesAccessedOn(), TIMESTAMP.withZone(TIMEZONE_MSK));
        assertEquals(info.getUserId(), TEST_USER_ID);
        assertEquals(info.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
        assertEquals(info.getUserAgent(), UA);
        assertEquals(info.getLanguages(), LANGUAGES);
        assertEquals(info.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(info.getUserStudyIds(), USER_STUDY_IDS);
        assertEquals(info.getTimeZone(), TIMEZONE_MSK);
        assertEquals(info.getAppId(), TEST_APP_ID);
    }


    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void testInvalidSupportedVersionThrowsException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
                .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        map.put(OperatingSystem.IOS, 28);
        app.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test
    public void testValidSupportedVersionDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        app.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test
    public void testNullSupportedVersionDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        app.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test
    public void testUnknownOSDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("Asthma/26 BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        app.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(app);
    }
    
    @Test
    public void roleEnforcedWhenRetrievingSession() throws Exception {
        // Mock participant and session.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getSessionIfItExists();

        // Mock app.
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // Single arg success.
        assertNotNull(controller.getAuthenticatedSession(Roles.RESEARCHER));

        // This method, upon confronting the fact that the user does not have this role, 
        // throws an UnauthorizedException.
        try {
            controller.getAuthenticatedSession(Roles.ADMIN);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            // expected exception
        }

        // Success with sets.
        assertNotNull(controller.getAuthenticatedSession(Roles.RESEARCHER));
        assertNotNull(controller.getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER));
        assertNotNull(controller.getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER, Roles.WORKER));

        // Unauthorized with sets
        try {
            controller.getAuthenticatedSession(Roles.ADMIN, Roles.DEVELOPER, Roles.WORKER);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            // expected exception
        }
    }
    
    @Test
    public void canGetLanguagesWhenInSession() {
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withLanguages(LANGUAGES).build());

        // Execute test.
        List<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGES, languages);

        // Participant already has languages. Nothing to save.
        verifyZeroInteractions(mockAccountService);
        verifyZeroInteractions(mockSessionUpdateService);
    }
    
    @Test
    public void canGetLanguagesWhenInHeader() throws Exception {
        Account account = Account.create();
        doAnswer(invocation -> {
            Consumer<Account> consumer = invocation.getArgument(1);
            consumer.accept(account);
            return null;
        }).when(mockAccountService).editAccount(any(), any());
        
        // Set up mocks.
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("en,fr");
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withLanguages(ImmutableList.of()).build());
        session.setSessionToken(SESSION_TOKEN);
        session.setAppId(TEST_APP_ID);
        
        // Verify as well that the values retrieved from the header have been saved in session and ParticipantOptions table.
        List<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGES, languages);

        // Verify we saved the language to the account.
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID_WITH_HEALTHCODE), any());
        assertEquals(account.getLanguages(), LANGUAGES);

        // Verify we call through to the session update service. (This updates both the cache and the participant, as
        // well as other things outside the scope of this test.)
        verify(mockSessionUpdateService).updateLanguage(same(session), contextCaptor.capture());
        assertEquals(LANGUAGES, contextCaptor.getValue().getLanguages());
    }

    @Test
    public void canGetLanguagesWhenNotInSessionOrHeader() throws Exception {
        RequestContext.set(null);

        // Set up mocks.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withLanguages(Lists.newArrayList()).build();
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        // Execute test.
        List<String> languages = controller.getLanguages(session);
        assertTrue(languages.isEmpty());

        // No languages means nothing to save.
        verifyZeroInteractions(mockAccountService);
        verifyZeroInteractions(mockSessionUpdateService);
    }

    @Test
    public void doesNotSetWarnHeaderWhenHasAcceptLanguage() throws Exception {
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
                .thenReturn("de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");

        // verify if it does not set warning header
        verify(mockResponse, times(0)).setHeader(BRIDGE_API_STATUS_HEADER, WARN_NO_ACCEPT_LANGUAGE);
    }
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void ipLockingForPrivilegedAccounts() throws Exception {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress("different address").build());

        // Setup test
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        app.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void ipLockingForParticipantsEnabled() {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress("different address").build());
        
        // Setup test
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setAppId(TEST_APP_ID);

        app.setParticipantIpLockingEnabled(true);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingForParticipantsDisabled() {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress("different address").build());
        
        // Setup test
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setAppId(TEST_APP_ID);

        app.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingSameIpAddress() {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress("same address").build());
        
        // Setup test - Append different load balancers to make sure we handle this properly.
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();

        session.setAuthenticated(true);
        session.setIpAddress("same address");
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        app.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }


    @Test
    public void getSessionWithRoleSucceed() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .withHealthCode(HEALTH_CODE).build();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        UserSession returned = controller.getAuthenticatedSession(false, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    @Test
    public void getAuthenticatedSessionFillsRequestContext() {
        RequestContext.set(new RequestContext.Builder()
                .withRequestId(REQUEST_ID)
                .withCallerIpAddress("1.2.3.4").build());
        
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setIpAddress("1.2.3.4");
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .withOrgMembership(TEST_ORG_ID)
                .withLanguages(LANGUAGES)
                .withStudyIds(USER_STUDY_IDS)
                .withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        app.setIdentifier(TEST_APP_ID);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doAnswer((ans) -> {
            RequestContext.updateFromSession(session, mockSponsorService);
            return session;
        }).when(controller).getSessionIfItExists();
        
        Set<String> orgStudies = ImmutableSet.of("study1", "study2");
        when(mockSponsorService.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID)).thenReturn(orgStudies);
        
        controller.getAuthenticatedSession(true, DEVELOPER);
        
        RequestContext context = RequestContext.get();
        assertNotNull(context.getMetrics());
        assertEquals(context.getId(), REQUEST_ID);
        assertEquals(context.getCallerAppId(), TEST_APP_ID);
        assertEquals(context.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(context.getCallerEnrolledStudies(), USER_STUDY_IDS);
        assertEquals(context.getOrgSponsoredStudies(), orgStudies);
        assertTrue(context.isAdministrator()); 
        assertEquals(context.getCallerUserId(), TEST_USER_ID); 
        assertEquals(context.getCallerClientInfo(), UNKNOWN_CLIENT);
        assertEquals(context.getCallerLanguages(), LANGUAGES);
        assertEquals(context.getCallerIpAddress(), "1.2.3.4");
    }

    // In this scenario, a user without roles receives the consent required exception
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void getSessionWithNoRolesConsentedOrRoleFails() {
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }

    // In this scenario, a user with roles receives the UnauthorizedException
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSessionWithNoConsentConsentedOrRoleFails() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withHealthCode(HEALTH_CODE).build();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithConsentedUserNotInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withHealthCode(HEALTH_CODE).build();
        session.setAuthenticated(true);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }

    @Test
    public void getSessionWithConsentedUserInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .withHealthCode(HEALTH_CODE).build();
        session.setAuthenticated(true);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(participant);
        session.setAppId(TEST_APP_ID);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }
    
    @Test
    public void ifClientSendsHeaderRetrieveIt() throws Exception {
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        assertEquals(controller.getSessionToken(), SESSION_TOKEN);
    }

    @Test
    public void ifClientSendsCookieRetrieveAndResetIt() {
        Cookie providedCookie = new Cookie(SESSION_TOKEN_HEADER, SESSION_TOKEN);
        when(mockRequest.getCookies()).thenReturn(new Cookie[] { providedCookie });
        
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        String token = controller.getSessionToken();
        assertEquals(token, SESSION_TOKEN);
        
        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getValue(), SESSION_TOKEN);
        assertEquals(cookie.getName(), SESSION_TOKEN_HEADER);
        assertEquals(cookie.getMaxAge(), BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        assertEquals(cookie.getPath(), "/");
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.getSecure());
        assertEquals(cookie.getDomain(), "localhost");        
    }

    @Test
    public void doesNotSetCookieExceptForLocal() {
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockBridgeConfig.get("domain")).thenReturn("domain-value");
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.DEV);
        
        String token = controller.getSessionToken();
        assertEquals(SESSION_TOKEN, token);
        
        verify(mockResponse, never()).addCookie(any());
    }
}
