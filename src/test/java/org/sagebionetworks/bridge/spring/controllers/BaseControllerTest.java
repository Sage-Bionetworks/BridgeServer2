package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.STRING_SET_TYPEREF;
import static org.sagebionetworks.bridge.BridgeConstants.WARN_NO_ACCEPT_LANGUAGE;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.CONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.IP_ADDRESS;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.REQUEST_ID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_TOKEN;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.TIMEZONE_MSK;
import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.sagebionetworks.bridge.TestConstants.UNCONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
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

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

public class BaseControllerTest extends Mockito {

    @Mock
    private CacheProvider mockCacheProvider;

    @Mock
    private BridgeConfig mockBridgeConfig;

    @Mock
    private AccountService mockAccountService;

    @Mock
    private StudyService mockStudyService;

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

    Study study;

    @BeforeMethod
    private void before() {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        MockitoAnnotations.initMocks(this);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();

        session = new UserSession();
        study = Study.create();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA))
                .withCallerLanguages(ImmutableList.of("en", "fr")).build());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void getSessionIfItExistsReturnsNull() {
        // No session token... no session
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExists() {
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);

        UserSession returnedSession = controller.getSessionIfItExists();
        assertEquals(returnedSession, session);
    }

    @Test
    public void getAuthenticatedAndConsentedSession() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

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
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        controller.getAuthenticatedAndConsentedSession();
    }

    @Test
    public void getAuthenticatedSessionRolesSucceeds() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        UserSession retrievedSession = controller.getAuthenticatedSession(Roles.DEVELOPER);
        assertEquals(session, retrievedSession);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAuthenticatedSessionRolesFailsRolesMismatched() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        controller.getAuthenticatedSession(Roles.DEVELOPER);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAuthenticatedSessionRolesFailsNoCallerRole() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        controller.getAuthenticatedSession(Roles.DEVELOPER);
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticationNoSession() {
        controller.getAuthenticatedSession();
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticationNotAuthenticated() {
        study.setParticipantIpLockingEnabled(false);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        session.setAuthenticated(false);
        
        controller.getAuthenticatedSession();
    }

    @Test
    public void getSessionEitherConsentedOrInRoleSucceedsOnRole() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.RESEARCHER);
        assertEquals(session, retrievedSession);
    }
    
    @Test
    public void getSessionEitherConsentedOrInRoleSucceedsOnConsent() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.RESEARCHER);
        assertEquals(session, retrievedSession);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSessionEitherConsentedOrInRoleFails() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER)).build());
        session.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(SESSION_TOKEN);
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);

        when(mockAuthenticationService.getSession(SESSION_TOKEN)).thenReturn(session);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        UserSession retrievedSession = controller.getSessionEitherConsentedOrInRole(Roles.DEVELOPER, Roles.ADMIN);
        assertEquals(session, retrievedSession);
    }

    @Test
    public void verifySupportedVersionOrThrowExceptionPasses() {
        study.setMinSupportedAppVersions(ImmutableMap.of("iPhone OS", 10));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void verifySupportedVersionOrThrowExceptionWithNoValuePasses() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void verifySupportedVersionOrThrowExceptionFails() {
        study.setMinSupportedAppVersions(ImmutableMap.of("iPhone OS", 30));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        controller.verifySupportedVersionOrThrowException(study);
    }

    @Test
    public void getLanguagesInits() {
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
                .thenReturn("fr-fr;q=0.4,fr;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
        
        controller.getLanguages(session);
        
        verify(mockAccountService).editAccount(eq(TEST_STUDY_IDENTIFIER), eq(HEALTH_CODE), any());
        verify(mockSessionUpdateService).updateLanguage(eq(session), contextCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getLanguages(), LANGUAGES);
    }
    
    @Test
    public void getLanguagesDoesNotOverwrite() {
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withLanguages(ImmutableList.of("fr"))
                .withHealthCode(HEALTH_CODE).build());
        when(mockRequest.getHeader(ACCEPT_LANGUAGE))
                .thenReturn("de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
        
        List<String> returnedLangs = controller.getLanguages(session);
        assertEquals(returnedLangs, ImmutableList.of("fr"));
        
        verify(mockAccountService, never()).editAccount(any(), any(), any());
        verify(mockSessionUpdateService, never()).updateLanguage(any(), any());
    }

    @Test
    public void getCriteriaContextWithStudyId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA))
                .withCallerLanguages(ImmutableList.of("en"))
                .build());
        when(mockRequest.getHeader(BridgeConstants.X_FORWARDED_FOR_HEADER)).thenReturn(IP_ADDRESS);
        
        CriteriaContext context = controller.getCriteriaContext(TEST_STUDY_IDENTIFIER);
        
        assertEquals(context.getStudyIdentifier(), TEST_STUDY_IDENTIFIER);
        assertEquals(context.getLanguages(), ImmutableList.of("en"));
        assertEquals(context.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
    }

    @Test
    public void getCriteriaContextWithSession() {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setIpAddress(IP_ADDRESS);
        session.setParticipant(new StudyParticipant.Builder()
                .withDataGroups(USER_DATA_GROUPS).withSubstudyIds(USER_SUBSTUDY_IDS)
                .withLanguages(LANGUAGES).withHealthCode(HEALTH_CODE).withId(USER_ID).build());
        
        CriteriaContext context = controller.getCriteriaContext(session);
        
        assertEquals(context.getLanguages(), LANGUAGES);
        assertEquals(context.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
        assertEquals(context.getHealthCode(), HEALTH_CODE);
        assertEquals(context.getUserId(), USER_ID);
        assertEquals(context.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(context.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(context.getStudyIdentifier(), TEST_STUDY_IDENTIFIER);
    }

    @Test
    public void parseJsonSucceeds() throws Exception {
        String json = TestUtils.createJson("{'eventKey':'foo','timestamp':'%s'}", TIMESTAMP.toString());
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
        
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withMetrics(metrics).withRequestId("a-request-id").build());
        
        Metrics retrievedMetrics = controller.getMetrics();
        assertEquals(retrievedMetrics, metrics);
    }

    @Test
    public void writeSessionInfoToMetrics() {
        // Mock metrics
        Metrics metrics = new Metrics(REQUEST_ID);
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withMetrics(metrics)
                .withRequestId(REQUEST_ID).build());
        
        session.setInternalSessionToken("internalSessionToken");
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        controller.writeSessionInfoToMetrics(session);
        
        ObjectNode node = metrics.getJson();
        assertEquals(node.get("session_id").textValue(), "internalSessionToken");
        assertEquals(node.get("user_id").textValue(), USER_ID);
        assertEquals(node.get("study").textValue(), TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void writeSessionInfoToMetricsNoSession() {
        // Mock metrics
        Metrics metrics = mock(Metrics.class);
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withMetrics(metrics)
                .withRequestId(REQUEST_ID).build());
        
        controller.writeSessionInfoToMetrics(null);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void writeSessionInfoToMetricsNoMetrics() {
        Metrics metrics = mock(Metrics.class);
        controller.writeSessionInfoToMetrics(null);
        verifyNoMoreInteractions(metrics);
    }
    
    @Test
    public void setCookieAndRecordMetrics() {
        session.setSessionToken(SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        controller.setCookieAndRecordMetrics(session);
        
        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getValue(), SESSION_TOKEN);
        assertEquals(cookie.getName(), SESSION_TOKEN_HEADER);
        assertEquals(cookie.getMaxAge(), BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        assertEquals(cookie.getPath(), "/");
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.getSecure());
        assertEquals(cookie.getDomain(), "localhost");
        
        verify(controller).writeSessionInfoToMetrics(session);
        verify(requestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        assertEquals(TIMESTAMP, requestInfoCaptor.getValue().getSignedInOn());
    }
    
    @Test
    public void setCookieAndRecordMetricsNoCookieOutsideLocal() throws Exception {
        session.setSessionToken(SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        when(mockBridgeConfig.get("domain")).thenReturn("domain-value");
        
        controller.setCookieAndRecordMetrics(session);
        
        verify(mockResponse, never()).addCookie(any());
        
        verify(controller).writeSessionInfoToMetrics(session);
        verify(requestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        assertEquals(TIMESTAMP, requestInfoCaptor.getValue().getSignedInOn());        
    }

    @Test
    public void getRequestInfoBuilder() {
        RequestInfo existingInfo = new RequestInfo.Builder().withActivitiesAccessedOn(TIMESTAMP).build();
        when(requestInfoService.getRequestInfo(USER_ID)).thenReturn(existingInfo);
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(UA);
        
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).withLanguages(LANGUAGES)
                .withDataGroups(USER_DATA_GROUPS).withSubstudyIds(USER_SUBSTUDY_IDS)
                .withTimeZone(TIMEZONE_MSK).build());

        RequestInfo info = controller.getRequestInfoBuilder(session).build();
        assertEquals(info.getActivitiesAccessedOn(), TIMESTAMP.withZone(TIMEZONE_MSK));
        assertEquals(info.getUserId(), USER_ID);
        assertEquals(info.getClientInfo(), ClientInfo.fromUserAgentCache(UA));
        assertEquals(info.getUserAgent(), UA);
        assertEquals(info.getLanguages(), LANGUAGES);
        assertEquals(info.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(info.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(info.getTimeZone(), TIMEZONE_MSK);
        assertEquals(info.getStudyIdentifier(), TEST_STUDY_IDENTIFIER);
    }


    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void testInvalidSupportedVersionThrowsException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
                .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        map.put(OperatingSystem.IOS, 28);
        study.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testValidSupportedVersionDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        study.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testNullSupportedVersionDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        HashMap<String, Integer> map = new HashMap<>();
        study.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testUnknownOSDoesNotThrowException() throws Exception {
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("Asthma/26 BridgeSDK/4");
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put(OperatingSystem.IOS, 25);
        study.setMinSupportedAppVersions(map);
        
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void roleEnforcedWhenRetrievingSession() throws Exception {
        // Mock participant and session.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        doReturn(session).when(controller).getSessionIfItExists();

        // Mock study.
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

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
            Consumer<Account> consumer = invocation.getArgument(2);
            consumer.accept(account);
            return null;
        }).when(mockAccountService).editAccount(any(), any(), any());
        
        // Set up mocks.
        when(mockRequest.getHeader(ACCEPT_LANGUAGE)).thenReturn("en,fr");
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withLanguages(ImmutableList.of()).build());
        session.setSessionToken(SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        // Verify as well that the values retrieved from the header have been saved in session and ParticipantOptions table.
        List<String> languages = controller.getLanguages(session);
        assertEquals(LANGUAGES, languages);

        // Verify we saved the language to the account.
        verify(mockAccountService).editAccount(eq(TEST_STUDY_IDENTIFIER), eq(HEALTH_CODE), any());
        assertEquals(account.getLanguages(), LANGUAGES);

        // Verify we call through to the session update service. (This updates both the cache and the participant, as
        // well as other things outside the scope of this test.)
        verify(mockSessionUpdateService).updateLanguage(same(session), contextCaptor.capture());
        assertEquals(LANGUAGES, contextCaptor.getValue().getLanguages());
    }

    @Test
    public void canGetLanguagesWhenNotInSessionOrHeader() throws Exception {
        BridgeUtils.setRequestContext(null);

        // Set up mocks.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withLanguages(Lists.newArrayList()).build();
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

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
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress("different address").build());

        // Setup test
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        study.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void ipLockingForParticipantsEnabled() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress("different address").build());
        
        // Setup test
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        study.setParticipantIpLockingEnabled(true);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // Execute, should throw
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingForParticipantsDisabled() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress("different address").build());
        
        // Setup test
        session.setAuthenticated(true);
        session.setIpAddress("original address");
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        study.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void ipLockingSameIpAddress() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress("same address").build());
        
        // Setup test - Append different load balancers to make sure we handle this properly.
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();

        session.setAuthenticated(true);
        session.setIpAddress("same address");
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        study.setParticipantIpLockingEnabled(false);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // Execute, should succeed
        controller.getAuthenticatedSession(false);
    }

    @Test
    public void getSessionPopulatesTheRequestContext() {
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).build();
        assertNotNull(context.getId());
        assertNull(context.getCallerStudyId());
        assertEquals(ImmutableSet.of(), context.getCallerSubstudies());
        assertFalse(context.isAdministrator());
        BridgeUtils.setRequestContext(context);
        
        Set<Roles> roles = ImmutableSet.of(DEVELOPER);
        
        StudyParticipant participant = new StudyParticipant.Builder().withSubstudyIds(USER_SUBSTUDY_IDS)
                .withRoles(roles).withId(USER_ID).build();
        session.setParticipant(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        controller.getAuthenticatedSession(false);
        
        context = BridgeUtils.getRequestContext();
        assertEquals(context.getId(), REQUEST_ID);
        assertEquals(context.getCallerStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(context.getCallerSubstudies(), USER_SUBSTUDY_IDS);
        assertTrue(context.isAdministrator());
        assertTrue(context.isInRole(DEVELOPER));
        assertEquals(context.getCallerUserId(), USER_ID);
    }

    @Test
    public void getSessionWithRoleSucceed() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        UserSession returned = controller.getAuthenticatedSession(false, Roles.DEVELOPER);
        assertEquals(session, returned);
    }

    // In this scenario, a user without roles receives the consent required exception
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void getSessionWithNoRolesConsentedOrRoleFails() {
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }

    // In this scenario, a user with roles receives the UnauthorizedException
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSessionWithNoConsentConsentedOrRoleFails() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .build();
        session.setAuthenticated(true);
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        controller.getAuthenticatedSession(true, Roles.DEVELOPER);
    }
    
    @Test
    public void getSessionWithConsentedUserNotInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .build();
        session.setAuthenticated(true);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        UserSession returned = controller.getAuthenticatedSession(true, Roles.DEVELOPER);
        assertEquals(session, returned);
    }

    @Test
    public void getSessionWithConsentedUserInRoleSuccess() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.DEVELOPER))
                .build();
        session.setAuthenticated(true);
        session.setConsentStatuses(CONSENTED_STATUS_MAP);
        session.setParticipant(participant);
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
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
