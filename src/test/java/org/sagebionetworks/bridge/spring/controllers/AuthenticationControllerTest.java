package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.REQUIRED_SIGNED_CURRENT;
import static org.sagebionetworks.bridge.TestUtils.getStudyParticipant;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.RequestInfoService;

public class AuthenticationControllerTest extends Mockito {
    private static final String USER_AGENT_STRING = "App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4";
    private static final String DOMAIN = "localhost";
    private static final DateTime NOW = DateTime.now();
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final String TEST_INTERNAL_SESSION_ID = "internal-session-id";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_ACCOUNT_ID = "spId";
    private static final String TEST_EMAIL = "email@email.com";
    private static final String TEST_SESSION_TOKEN = "session-token";
    private static final String TEST_STUDY_ID_STRING = "study-key";
    private static final StudyIdentifier TEST_STUDY_ID = new StudyIdentifierImpl(TEST_STUDY_ID_STRING);
    private static final String TEST_TOKEN = "verify-token";
    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
    private static final SignIn EMAIL_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withEmail(TEST_EMAIL).withToken(TEST_TOKEN).build();
    private static final SignIn PHONE_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withPhone(TestConstants.PHONE).build();
    private static final SignIn PHONE_SIGN_IN = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withEmail(TEST_EMAIL).withReauthToken(REAUTH_TOKEN).build();

    @InjectMocks
    @Spy
    AuthenticationController controller;

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    AccountWorkflowService mockWorkflowService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    Metrics metrics;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;
    
    @Captor
    ArgumentCaptor<PasswordReset> passwordResetCaptor;
    
    Study study;
    
    UserSession userSession;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        // Mock the configuration so we can freeze the environment to one that requires SSL.
        when(mockConfig.get("domain")).thenReturn(DOMAIN);
        when(mockConfig.getEnvironment()).thenReturn(Environment.UAT);
        
        userSession = new UserSession();
        userSession.setReauthToken(REAUTH_TOKEN);
        userSession.setSessionToken(TEST_SESSION_TOKEN);
        userSession.setParticipant(new StudyParticipant.Builder().withId(TEST_ACCOUNT_ID).build());
        userSession.setInternalSessionToken(TEST_INTERNAL_SESSION_ID);
        userSession.setStudyIdentifier(TEST_STUDY_ID);
        
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_ID_STRING);
        study.setDataGroups(TestConstants.USER_DATA_GROUPS);
        when(mockStudyService.getStudy(TEST_STUDY_ID_STRING)).thenReturn(study);
        when(mockStudyService.getStudy(TEST_STUDY_ID)).thenReturn(study);
        when(mockStudyService.getStudy((String)null)).thenThrow(new EntityNotFoundException(Study.class));
        
        doReturn(metrics).when(controller).getMetrics();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache(USER_AGENT_STRING);
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerClientInfo(clientInfo).build());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }

    @Test
    public void requestEmailSignIn() throws Exception {
        // Mock.
        mockJson("{'study':'study-key','email':'email@email.com'}");
        when(mockWorkflowService.requestEmailSignIn(any())).thenReturn(TEST_ACCOUNT_ID);

        // Execute.
        StatusMessage result = controller.requestEmailSignIn();
        assertEquals(result.getMessage(), "Email sent.");

        // Verify.
        verify(mockWorkflowService).requestEmailSignIn(signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getStudyId(), "study-key");
        assertEquals(signInCaptor.getValue().getEmail(), TEST_EMAIL);

        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
        verify(metrics).setUserId(TEST_ACCOUNT_ID);
    }

    @Test
    public void requestEmailSignIn_NoUser() throws Exception {
        // Mock.
        mockJson("{'study':'study-key','email':'email@email.com'}");
        when(mockWorkflowService.requestEmailSignIn(any())).thenReturn(null);

        // Execute.
        StatusMessage result = controller.requestEmailSignIn();
        assertEquals(result.getMessage(), "Email sent.");

        // Verify.
        verify(mockWorkflowService).requestEmailSignIn(signInCaptor.capture());
        assertEquals("study-key", signInCaptor.getValue().getStudyId());
        assertEquals(TEST_EMAIL, signInCaptor.getValue().getEmail());

        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
        verify(metrics, never()).setUserId(any());
    }

    @Test
    public void emailSignIn() throws Exception {
        mockJson("{'study':'study-key','email':'email@email.com','token':'ABC'}");
        
        userSession.setAuthenticated(true);
        study.setIdentifier("study-test");
        doReturn(userSession).when(mockAuthService).emailSignIn(any(SignIn.class));
        
        JsonNode node = controller.emailSignIn();
        assertTrue(node.get("authenticated").booleanValue());
     
        verify(mockAuthService).emailSignIn(signInCaptor.capture());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(captured.getEmail(), TEST_EMAIL);
        assertEquals(captured.getStudyId(), "study-key");
        assertEquals(captured.getToken(), "ABC");
        
        verifyCommonLoggingForSignIns();
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void emailSignInMissingStudyId() throws Exception {
        mockJson("{'email':'email@email.com','token':'abc'}");

        controller.emailSignIn();
    }

    @Test
    public void failedEmailSignInStillLogsStudyId() throws Exception {
        // Set up test.
        TestUtils.mockRequestBody(mockRequest, EMAIL_SIGN_IN_REQUEST);
        when(mockAuthService.emailSignIn(any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.emailSignIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void reauthenticateWithoutStudyThrowsException() throws Exception {
        mockJson("{'email':'email@email.com','reauthToken':'abc'}");
        
        controller.reauthenticate();
    }
    
    @Test
    public void reauthenticate() throws Exception {
        long timestamp = DateTime.now().getMillis();
        DateTimeUtils.setCurrentMillisFixed(timestamp);
        try {
            mockJson("{'study':'study-key','email':'email@email.com','reauthToken':'abc'}");
            when(mockAuthService.reauthenticate(any(), any())).thenReturn(userSession);
            
            JsonNode node = controller.reauthenticate();
            
            verify(mockAuthService).reauthenticate(any(), signInCaptor.capture());
            SignIn signIn = signInCaptor.getValue();
            assertEquals(signIn.getStudyId(), "study-key");
            assertEquals(signIn.getEmail(), "email@email.com");
            assertEquals(signIn.getReauthToken(), "abc");
            
            assertEquals(node.get("reauthToken").textValue(), REAUTH_TOKEN);
            
            verifyCommonLoggingForSignIns();
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void failedReauthStillLogsStudyId() throws Exception {
        // Set up test.
        mockJson("{'study':'study-key','email':'email@email.com','reauthToken':'abc'}");
        when(mockAuthService.reauthenticate(any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.reauthenticate();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }

    @Test
    public void getSessionIfItExistsNullToken() {
        doReturn(null).when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }
    
    @Test
    public void getSessionIfItExistsEmptyToken() {
        doReturn("").when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExistsBlankToken() {
        doReturn("   ").when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }
    
    @Test
    public void getSessionIfItExistsSuccess() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(userSession);

        // execute and validate
        UserSession retVal = controller.getSessionIfItExists();
        assertSame(userSession, retVal);
        verifyMetrics();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNullToken() {
        doReturn(null).when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedSessionEmptyToken() {
        doReturn("").when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedSessionBlankToken() {
        doReturn("   ").when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNullSession() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(null);

        // execute
        controller.getAuthenticatedSession();
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNotAuthenticated() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(REQUIRED_SIGNED_CURRENT, null);
        session.setAuthenticated(false);
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute
        controller.getAuthenticatedSession();
    }
    
    @Test
    public void getAuthenticatedSessionSuccess() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(REQUIRED_SIGNED_CURRENT, null);
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        UserSession retVal = controller.getAuthenticatedSession();
        assertSame(session, retVal);
        verifyMetrics();
    }
    
    @Test
    public void signUpWithCompleteUserData() throws Exception {
        // Other fields will be passed along to the PartcipantService, but it will not be utilized
        // These are the fields that *can* be changed. They are all passed along.
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_STUDY_ID_STRING);
        
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(study), participantCaptor.capture());
        
        StudyParticipant persistedParticipant = participantCaptor.getValue();
        assertEquals(persistedParticipant.getFirstName(), originalParticipant.getFirstName());
        assertEquals(persistedParticipant.getLastName(), originalParticipant.getLastName());
        assertEquals(persistedParticipant.getEmail(), originalParticipant.getEmail());
        assertEquals(persistedParticipant.getPassword(), originalParticipant.getPassword());
        assertEquals(persistedParticipant.getSharingScope(), originalParticipant.getSharingScope());
        assertEquals(persistedParticipant.getExternalId(), originalParticipant.getExternalId());
        assertTrue(persistedParticipant.isNotifyByEmail());
        assertEquals(persistedParticipant.getDataGroups(), originalParticipant.getDataGroups());
        assertEquals(persistedParticipant.getAttributes(), originalParticipant.getAttributes());
        assertEquals(persistedParticipant.getLanguages(), originalParticipant.getLanguages());

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }

    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void signUpAppVersionDisabled() throws Exception {
        // Participant
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_STUDY_ID_STRING);

        // min app version is 20 (which is higher than 14)
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);

        // Setup and execute. This will throw.
        mockRequestBody(mockRequest, node);
        controller.signUp();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signUpNoStudy() throws Exception {
        // Participant - don't add study
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);

        // Setup and execute. This will throw.
        mockRequestBody(mockRequest, node);
        controller.signUp();
    }
    
    @SuppressWarnings("deprecation")
    private void signInNewSession(boolean isConsented, Roles role) throws Exception {

        // mock request
        String requestJsonString = "{\n" +
                "   \"email\":\"" + TEST_EMAIL + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";

        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(requestJsonString));

        // mock AuthenticationService
        ConsentStatus consentStatus = (isConsented) ? REQUIRED_SIGNED_CURRENT : null;
        UserSession session = createSession(consentStatus, role);
        when(mockAuthService.signIn(any(), any())).thenReturn(session);
        
        // execute and validate
        JsonNode result = controller.signInV3();
        assertSessionInJson(result);
        
        controller.response();

        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("spId", requestInfo.getUserId());
        assertEquals(TEST_STUDY_ID, requestInfo.getStudyIdentifier());
        assertTrue(requestInfo.getSignedInOn() != null);
        assertEquals(TestConstants.USER_DATA_GROUPS, requestInfo.getUserDataGroups());
        assertNotNull(requestInfo.getSignedInOn());
        verifyCommonLoggingForSignIns();

        // validate signIn
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        verify(mockAuthService).signIn(same(study), signInCaptor.capture());

        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_EMAIL, signIn.getEmail());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }
    
    @Test
    public void signInNewSession() throws Exception {
        signInNewSession(true, null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void signOut() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        StatusMessage result = controller.signOut();
        assertEquals(result.getMessage(), "Signed out.");
        
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        
        verify(mockAuthService).signOut(session);
        verify(mockResponse).addCookie(cookieCaptor.capture());
        verifyMetrics();
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getValue(), "");
        assertEquals(cookie.getMaxAge(), 0);
    }
    
    @Test
    public void signOutV4() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(mockAuthService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        StatusMessage result = controller.signOutV4();
        assertEquals(result.getMessage(), "Signed out.");

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        
        verify(mockAuthService).signOut(session);
        verify(mockResponse).addCookie(cookieCaptor.capture());
        verify(mockResponse).setHeader(BridgeConstants.CLEAR_SITE_DATA_HEADER, BridgeConstants.CLEAR_SITE_DATA_VALUE);
        verifyMetrics();
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getValue(), "");
        assertEquals(cookie.getMaxAge(), 0);
    }
    
    @Test
    public void signOutV4Throws() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        // execute and validate
        try {
            controller.signOutV4();
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            
        }
        
        verify(mockResponse).addCookie(any()); // tested in prior test to be the correct cookie values
        verify(mockResponse).setHeader(BridgeConstants.CLEAR_SITE_DATA_HEADER, BridgeConstants.CLEAR_SITE_DATA_VALUE);
        // We do not send metrics if you don't have a session, for better or worse.
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void signOutAlreadySignedOut() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        // execute and validate
        StatusMessage result = controller.signOut();
        assertEquals(result.getMessage(), "Signed out.");

        // No session, so no check on metrics or AuthService.signOut()
    }

    @Test
    public void verifyEmail() throws Exception {
        // mock request
        String json = TestUtils.createJson(
                "{'sptoken':'"+TEST_TOKEN+"','study':'"+TEST_STUDY_ID_STRING+"'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));

        ArgumentCaptor<Verification> verificationCaptor = ArgumentCaptor.forClass(Verification.class);

        // execute and validate
        StatusMessage result = controller.verifyEmail();
        assertEquals(result.getMessage(), "Email address verified.");

        // validate email verification
        verify(mockAuthService).verifyChannel(eq(ChannelType.EMAIL), verificationCaptor.capture());
        Verification verification = verificationCaptor.getValue();
        assertEquals(TEST_TOKEN, verification.getSptoken());
    }

    @Test
    public void verifyPhone() throws Exception {
        // mock request
        String json = TestUtils.createJson(
                "{'sptoken':'"+TEST_TOKEN+"','study':'"+TEST_STUDY_ID_STRING+"'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));

        ArgumentCaptor<Verification> verificationCaptor = ArgumentCaptor.forClass(Verification.class);

        // execute and validate
        StatusMessage result = controller.verifyPhone();
        assertEquals(result.getMessage(), "Phone number verified.");

        // validate phone verification
        verify(mockAuthService).verifyChannel(eq(ChannelType.PHONE), verificationCaptor.capture());
        Verification verification = verificationCaptor.getValue();
        assertEquals(TEST_TOKEN, verification.getSptoken());
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void signInBlockedByVersionKillSwitch() throws Exception {
        String json = TestUtils.createJson("{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.signInV3();
    }
    
    @Test
    public void localSignInSetsSessionCookie() throws Exception {
        when(mockConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        // mock request
        String requestJsonString = "{" +
                "\"email\":\"" + TEST_EMAIL + "\"," +
                "\"password\":\"" + TEST_PASSWORD + "\"," +
                "\"study\":\"" + TEST_STUDY_ID_STRING + "\"}";

        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(requestJsonString));

        // mock AuthenticationService
        UserSession session = createSession(REQUIRED_SIGNED_CURRENT, null);
        when(mockAuthService.signIn(any(), any())).thenReturn(session);
        
        // execute and validate
        controller.signIn();

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        
        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getName(), BridgeConstants.SESSION_TOKEN_HEADER);
        assertEquals(cookie.getValue(), TEST_SESSION_TOKEN);
        assertEquals(cookie.getMaxAge(), BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        assertEquals(cookie.getPath(), "/");
        assertEquals(cookie.getDomain(), DOMAIN);
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.getSecure());
    }
    
    @Test
    public void signInOnLocalDoesNotSetCookieWithSSL() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        when(controller.bridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        UserSession session = createSession(null, null);
        when(mockAuthService.signIn(any(), any())).thenReturn(session);
        
        controller.signIn();
        
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        
        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getName(), BridgeConstants.SESSION_TOKEN_HEADER);
        assertEquals(cookie.getValue(), TEST_SESSION_TOKEN);
        assertEquals(cookie.getMaxAge(), BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        assertEquals(cookie.getPath(), "/");
        assertEquals(cookie.getDomain(), DOMAIN);
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.getSecure());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void emailSignInBlockedByVersionKillSwitch() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.emailSignIn();
    }

    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void phoneSignInBlockedByVersionKillSwitch() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(USER_AGENT_STRING);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.phoneSignIn();
    }
    
    @Test
    public void resendEmailVerificationWorks() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resendEmailVerification();
        
        verify(mockAuthService).resendVerification(eq(ChannelType.EMAIL), accountIdCaptor.capture());
        AccountId deser = accountIdCaptor.getValue();
        assertEquals(TEST_STUDY_ID.getIdentifier(), deser.getStudyId());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resendEmailVerificationAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resendEmailVerification();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendEmailVerificationNoStudy() throws Exception {
        String json = TestUtils.createJson("{'email':'email@email.com'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        controller.resendEmailVerification();
    }
    
    @Test
    public void resendPhoneVerificationWorks() throws Exception {
        mockSignInWithPhonePayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resendPhoneVerification();
        
        verify(mockAuthService).resendVerification(eq(ChannelType.PHONE), accountIdCaptor.capture());
        AccountId deser = accountIdCaptor.getValue();
        assertEquals(TEST_STUDY_ID.getIdentifier(), deser.getStudyId());
        assertEquals(TestConstants.PHONE, deser.getPhone());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resendPhoneVerificationAppVersionDisabled() throws Exception {
        mockSignInWithPhonePayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resendPhoneVerification();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendPhoneVerificationNoStudy() throws Exception {
        String json = TestUtils.createJson("{'phone':{'number':'4082588569','regionCode':'US'}}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        controller.resendPhoneVerification();
    }
    
    @Test
    public void resendPhoneVerificationVerifyPhone() throws Exception {
        String json = TestUtils.createJson("{'study':'study-key','phone':{'number':'4082588569','regionCode':'US'}}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        controller.resendPhoneVerification();
    }
    
    @Test
    public void resetPassword() throws Exception {
        mockResetPasswordRequest();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resetPassword();
        
        verify(mockAuthService).resetPassword(passwordResetCaptor.capture());
        
        PasswordReset passwordReset = passwordResetCaptor.getValue();
        assertEquals("aSpToken", passwordReset.getSptoken());
        assertEquals("aPassword", passwordReset.getPassword());
        assertEquals(TEST_STUDY_ID_STRING, passwordReset.getStudyIdentifier());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resetPasswordAppVersionDisabled() throws Exception {
        mockResetPasswordRequest();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resetPassword();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resetPasswordNoStudy() throws Exception {
        mockRequestBody(mockRequest, new PasswordReset("aPassword", "aSpToken", null));
        controller.resetPassword();
    }

    @Test
    public void requestResetPasswordWithEmail() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.requestResetPassword();
        
        verify(mockAuthService).requestResetPassword(eq(study), eq(false), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, deser.getStudyId());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test
    public void requestResetPasswordWithPhone() throws Exception {
        mockSignInWithPhonePayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.requestResetPassword();
        
        verify(mockAuthService).requestResetPassword(eq(study), eq(false), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, deser.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), deser.getPhone().getNumber());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void requestResetPasswordAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20); // blocked
        
        controller.requestResetPassword();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void requestResetPasswordNoStudy() throws Exception {
        when(mockStudyService.getStudy((String) any())).thenThrow(new EntityNotFoundException(Study.class));
        
        mockRequestBody(mockRequest, new SignIn.Builder().withEmail(TEST_EMAIL).build());
        
        controller.requestResetPassword();
    }
    
    @Test
    public void signUpWithNoCheckForConsentDeclared() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(study), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpWithCheckForConsentDeclaredFalse() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        node.put("checkForConsent", false);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(study), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpWithCheckForConsentDeclaredTrue() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        node.put("checkForConsent", true);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(study), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }

    @Test
    public void requestPhoneSignIn() throws Exception {
        // Mock.
        mockRequestBody(mockRequest, PHONE_SIGN_IN_REQUEST);
        when(mockWorkflowService.requestPhoneSignIn(any())).thenReturn(TEST_ACCOUNT_ID);

        // Execute.
        StatusMessage result = controller.requestPhoneSignIn();
        assertEquals(result.getMessage(), "Message sent.");

        // Verify.
        verify(mockWorkflowService).requestPhoneSignIn(signInCaptor.capture());

        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, captured.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());

        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
        verify(metrics).setUserId(TEST_ACCOUNT_ID);
    }
    
    @Test
    public void requestPhoneSignIn_NoUser() throws Exception {
        // Mock.
        mockRequestBody(mockRequest, PHONE_SIGN_IN_REQUEST);
        when(mockWorkflowService.requestPhoneSignIn(any())).thenReturn(null);

        // Execute.
        StatusMessage result = controller.requestPhoneSignIn();
        assertEquals(result.getMessage(), "Message sent.");

        // Verify.
        verify(mockWorkflowService).requestPhoneSignIn(signInCaptor.capture());

        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, captured.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());

        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
        verify(metrics, never()).setUserId(any());
    }

    @Test
    public void phoneSignIn() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.phoneSignIn(any())).thenReturn(userSession);
        
        JsonNode result = controller.phoneSignIn();
        
        // Returns user session.
        assertEquals(TEST_SESSION_TOKEN, result.get("sessionToken").textValue());
        assertEquals("UserSessionInfo", result.get("type").textValue());
        
        verify(mockAuthService).phoneSignIn(signInCaptor.capture());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, captured.getStudyId());
        assertEquals(TEST_TOKEN, captured.getToken());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        
        verifyCommonLoggingForSignIns();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void phoneSignInMissingStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withStudy(null)
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockRequestBody(mockRequest, badPhoneSignIn);
        
        controller.phoneSignIn();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void phoneSignInBadStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withStudy("bad-study")
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockRequestBody(mockRequest, badPhoneSignIn);
        
        when(mockStudyService.getStudy((String)any())).thenThrow(new EntityNotFoundException(Study.class));
        
        controller.phoneSignIn();
    }

    @Test
    public void failedPhoneSignInStillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        when(mockAuthService.phoneSignIn(any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.phoneSignIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signInV3ThrowsNotFound() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.signIn(any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signInV3();
    }

    @SuppressWarnings({ "deprecation" })
    @Test
    public void failedSignInV3StillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.signInV3();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void signInV4ThrowsUnauthoried() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.signIn(any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signIn();
    }

    @Test
    public void failedSignInV4StillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.signIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setStudy(TEST_STUDY_ID_STRING);
    }

    @Test
    public void unconsentedSignInSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.signIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }

    @Test
    public void unconsentedEmailSignInSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, EMAIL_SIGN_IN_REQUEST);
        when(mockAuthService.emailSignIn(any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.emailSignIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }
    
    @Test
    public void unconsentedPhoneSignInSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN_REQUEST);
        when(mockAuthService.phoneSignIn(any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.phoneSignIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }

    @Test
    public void unconsentedReauthSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, REAUTH_REQUEST);
        when(mockAuthService.reauthenticate(any(), any())).thenThrow(new ConsentRequiredException(userSession));

        try {
            controller.reauthenticate();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
            // expected exception
        }
        verifyCommonLoggingForSignIns();
    }

    private void mockResetPasswordRequest() throws Exception {
        String json = TestUtils.createJson("{'study':'" + TEST_STUDY_ID_STRING + 
            "','sptoken':'aSpToken','password':'aPassword'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
    }
    
    private void mockSignInWithEmailPayload() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING).withEmail(TEST_EMAIL).build();
        
        mockRequestBody(mockRequest, signIn);
    }

    private void mockSignInWithPhonePayload() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING).withPhone(TestConstants.PHONE).build();
        
        mockRequestBody(mockRequest, signIn);
    }
    
    private static void assertSessionInJson(JsonNode resultNode) throws Exception {
        // test only a few key values
        assertTrue(resultNode.get("authenticated").booleanValue());
        assertEquals(TEST_SESSION_TOKEN, resultNode.get("sessionToken").textValue());
    }
    
    private UserSession createSession(ConsentStatus status, Roles role) {
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        builder.withId(TEST_ACCOUNT_ID);
        // set this value so we can verify it is copied into RequestInfo on a sign in.
        builder.withDataGroups(TestConstants.USER_DATA_GROUPS);
        if (role != null) {
            builder.withRoles(Sets.newHashSet(role));
        }
        UserSession session = new UserSession(builder.build());
        session.setAuthenticated(true);
        session.setInternalSessionToken(TEST_INTERNAL_SESSION_ID);
        session.setSessionToken(TEST_SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_ID);
        if (status != null){
            session.setConsentStatuses(ImmutableMap.of(
                SubpopulationGuid.create(status.getSubpopulationGuid()), status));    
        }
        return session;
    }

    private void mockJson(String json) throws Exception {
        String escapedJson = TestUtils.createJson(json);
        ServletInputStream is = TestUtils.toInputStream(escapedJson);
        when(mockRequest.getInputStream()).thenReturn(is);
    }
    
    private void verifyMetrics() {
        verify(controller, atLeastOnce()).getMetrics();
        
        verify(metrics, atLeastOnce()).setSessionId(TEST_INTERNAL_SESSION_ID);
        verify(metrics, atLeastOnce()).setUserId(TEST_ACCOUNT_ID);
        verify(metrics, atLeastOnce()).setStudy(TEST_STUDY_ID_STRING);
    }
    
    private void verifyCommonLoggingForSignIns() throws Exception {
        verifyMetrics();
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        verify(mockResponse, never()).addCookie(any());        
        RequestInfo info = requestInfoCaptor.getValue();
        assertEquals(NOW.getMillis(), info.getSignedInOn().getMillis());
    }
}
