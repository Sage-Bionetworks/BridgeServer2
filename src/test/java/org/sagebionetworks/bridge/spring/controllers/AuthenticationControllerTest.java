package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.APP_ACCESS_EXCEPTION_MSG;
import static org.sagebionetworks.bridge.BridgeConstants.NOT_SYNAPSE_AUTHENTICATED;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.REQUIRED_SIGNED_CURRENT;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.getStudyParticipant;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Optional;

import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;

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
    private static final String TEST_TOKEN = "verify-token";
    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
    private static final SignIn EMAIL_SIGN_IN_REQUEST = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(TEST_EMAIL).withToken(TEST_TOKEN).build();
    private static final SignIn PHONE_SIGN_IN_REQUEST = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withPhone(TestConstants.PHONE).build();
    private static final SignIn PHONE_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(TEST_EMAIL).withReauthToken(REAUTH_TOKEN).build();

    @InjectMocks
    @Spy
    AuthenticationController controller;

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    AccountWorkflowService mockWorkflowService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    SessionUpdateService mockSessionUpdateService;
    
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

    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    App app;
    
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
        userSession.setAppId(TEST_APP_ID);
        
        app = new DynamoApp();
        app.setIdentifier(TEST_APP_ID);
        app.setDataGroups(USER_DATA_GROUPS);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        when(mockAppService.getApp((String)null)).thenThrow(new EntityNotFoundException(App.class));
        
        doReturn(metrics).when(controller).getMetrics();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache(USER_AGENT_STRING);
        RequestContext.set(new RequestContext.Builder()
                .withCallerClientInfo(clientInfo).build());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        RequestContext.set(null);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AuthenticationController.class);
        assertPost(AuthenticationController.class, "oauthSignIn");
        assertPost(AuthenticationController.class, "signInForSuperAdmin");
        assertAccept(AuthenticationController.class, "requestEmailSignIn");
        assertAccept(AuthenticationController.class, "requestPhoneSignIn");
        assertPost(AuthenticationController.class, "emailSignIn");
        assertPost(AuthenticationController.class, "phoneSignIn");
        assertPost(AuthenticationController.class, "signIn");
        assertPost(AuthenticationController.class, "reauthenticate");
        assertPost(AuthenticationController.class, "signInV3");
        assertPost(AuthenticationController.class, "signOut");
        assertGet(AuthenticationController.class, "signOutGet");
        assertPost(AuthenticationController.class, "signOutV4");
        assertCreate(AuthenticationController.class, "signUp");
        assertPost(AuthenticationController.class, "verifyEmail");
        assertAccept(AuthenticationController.class, "resendEmailVerification");
        assertPost(AuthenticationController.class, "verifyPhone");
        assertAccept(AuthenticationController.class, "resendPhoneVerification");
        assertAccept(AuthenticationController.class, "requestResetPassword");
        assertPost(AuthenticationController.class, "resetPassword");
        assertPost(AuthenticationController.class, "changeApp");
    }
    
    @Test
    public void requestEmailSignInWithAppId() throws Exception {
        requestEmailSignIn("appId");
    }
    
    @Test
    public void requestEmailSignInWithStudy() throws Exception {
        requestEmailSignIn("study");
    }
    
    private void requestEmailSignIn(String appFieldName) throws Exception {
        // Mock.
        mockJson("{'" + appFieldName + "':'" + TEST_APP_ID + "','email':'email@email.com'}");
        when(mockWorkflowService.requestEmailSignIn(any())).thenReturn(TEST_ACCOUNT_ID);

        // Execute.
        StatusMessage result = controller.requestEmailSignIn();
        assertEquals(result.getMessage(), AuthenticationController.EMAIL_SIGNIN_REQUEST_MSG);

        // Verify.
        verify(mockWorkflowService).requestEmailSignIn(signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(signInCaptor.getValue().getEmail(), TEST_EMAIL);

        verify(metrics).setAppId(TEST_APP_ID);
        verify(metrics).setUserId(TEST_ACCOUNT_ID);
    }

    @Test
    public void requestEmailSignIn_NoUser() throws Exception {
        // Mock.
        mockJson("{'study':'" + TEST_APP_ID + "','email':'email@email.com'}");
        when(mockWorkflowService.requestEmailSignIn(any())).thenReturn(null);

        // Execute.
        StatusMessage result = controller.requestEmailSignIn();
        assertEquals(result.getMessage(), AuthenticationController.EMAIL_SIGNIN_REQUEST_MSG);

        // Verify.
        verify(mockWorkflowService).requestEmailSignIn(signInCaptor.capture());
        assertEquals(signInCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(signInCaptor.getValue().getEmail(), TEST_EMAIL);

        verify(metrics).setAppId(TEST_APP_ID);
        verify(metrics, never()).setUserId(any());
    }

    @Test
    public void emailSignInWithStudy() throws Exception {
        emailSignIn("study");
    }
    
    @Test
    public void emailSignInWithAppId() throws Exception {
        emailSignIn("appId");
    }
    
    private void emailSignIn(String appFieldName) throws Exception {
        mockJson("{'" + appFieldName + "':'" + TEST_APP_ID + "','email':'email@email.com','token':'ABC'}");
        
        userSession.setAuthenticated(true);
        app.setIdentifier(TEST_APP_ID);
        doReturn(userSession).when(mockAuthService).channelSignIn(eq(ChannelType.EMAIL), any(CriteriaContext.class), any(SignIn.class));
        
        JsonNode node = controller.emailSignIn();
        assertTrue(node.get("authenticated").booleanValue());
     
        verify(mockAuthService).channelSignIn(eq(ChannelType.EMAIL), any(), signInCaptor.capture());

        SignIn captured = signInCaptor.getValue();
        assertEquals(captured.getEmail(), TEST_EMAIL);
        assertEquals(captured.getAppId(), TEST_APP_ID);
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
        when(mockAuthService.channelSignIn(any(), any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.emailSignIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setAppId(TEST_APP_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void reauthenticateWithoutStudyThrowsException() throws Exception {
        mockJson("{'email':'email@email.com','reauthToken':'abc'}");
        
        controller.reauthenticate();
    }
    
    @Test
    public void reauthenticateWithStudy() throws Exception {
        reauthenticate("study");
    }
    
    @Test
    public void reauthenticateWithAppId() throws Exception {
        reauthenticate("appId");
    }
    
    private void reauthenticate(String appFieldName) throws Exception {
        long timestamp = DateTime.now().getMillis();
        DateTimeUtils.setCurrentMillisFixed(timestamp);
        try {
            mockJson("{'" + appFieldName + "':'" + TEST_APP_ID + "','email':'email@email.com','reauthToken':'abc'}");
            when(mockAuthService.reauthenticate(any(), any(), any())).thenReturn(userSession);
            
            JsonNode node = controller.reauthenticate();
            
            verify(mockAuthService).reauthenticate(any(), any(), signInCaptor.capture());
            SignIn signIn = signInCaptor.getValue();
            assertEquals(signIn.getAppId(), TEST_APP_ID);
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
        mockJson("{'study':'" + TEST_APP_ID + "','email':'email@email.com','reauthToken':'abc'}");
        when(mockAuthService.reauthenticate(any(), any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.reauthenticate();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setAppId(TEST_APP_ID);
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
        verify(mockRequest).setAttribute(eq("CreatedUserSession"), any(UserSession.class));
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
        verify(mockRequest).setAttribute(eq("CreatedUserSession"), any(UserSession.class));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields: phone.*")
    public void signUpBadJson() throws Exception {
        mockRequestBody(mockRequest, "{\"phone\":\"+1234567890\"}");
        
        controller.signUp();
    }
    
    @Test
    public void signUpWithCompleteUserDataWithStudy() throws Exception {
        signUpWithCompleteUserData("study");
    }
    
    @Test
    public void signUpWithCompleteUserDataWithAppId() throws Exception {
        signUpWithCompleteUserData("appId");
    }
    
    private void signUpWithCompleteUserData(String appFieldName) throws Exception {
        // Other fields will be passed along to the PartcipantService, but it will not be utilized
        // These are the fields that *can* be changed. They are all passed along.
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put(appFieldName, TEST_APP_ID);
        
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
        
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
        verify(metrics).setAppId(TEST_APP_ID);
    }    

    @Test
    public void signUpWithStudy() throws Exception {
        mockRequestBody(mockRequest, createJson("{'study':'"+TEST_APP_ID+"'}"));
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
    }

    @Test
    public void signUpWithAppId() throws Exception {
        mockRequestBody(mockRequest, createJson("{'appId':'"+TEST_APP_ID+"'}"));
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void signUpAppVersionDisabled() throws Exception {
        // Participant
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_APP_ID);

        // min app version is 20 (which is higher than 14)
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);

        // Setup and execute. This will throw.
        mockRequestBody(mockRequest, node);
        controller.signUp();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signUpNoStudy() throws Exception {
        // Participant - don't add app
        StudyParticipant originalParticipant = getStudyParticipant(AuthenticationControllerTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);

        // Setup and execute. This will throw.
        mockRequestBody(mockRequest, node);
        controller.signUp();
    }
    
    @SuppressWarnings("deprecation")
    private void signInNewSession(String appFieldName, boolean isConsented, Roles role) throws Exception {
        // Even if a session token already exists, we still ignore it and call signIn anyway.
        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(String.class));

        // mock request
        String requestJsonString = "{\n" +
                "   \"email\":\"" + TEST_EMAIL + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"" + appFieldName + "\":\"" + TEST_APP_ID + "\"\n" +
                "}";

        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(requestJsonString));

        // mock AuthenticationService
        ConsentStatus consentStatus = (isConsented) ? TestConstants.REQUIRED_SIGNED_CURRENT : null;
        UserSession session = createSession(consentStatus, role);
        when(mockAuthService.signIn(any(), any(), any())).thenReturn(session);
        
        // execute and validate
        JsonNode result = controller.signInV3();
        assertSessionInJson(result);
        
        controller.response();

        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals("spId", requestInfo.getUserId());
        assertEquals(TEST_APP_ID, requestInfo.getAppId());
        assertTrue(requestInfo.getSignedInOn() != null);
        assertEquals(TestConstants.USER_DATA_GROUPS, requestInfo.getUserDataGroups());
        assertNotNull(requestInfo.getSignedInOn());
        verifyCommonLoggingForSignIns();

        // validate signIn
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        verify(mockAuthService).signIn(same(app), any(), signInCaptor.capture());

        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_EMAIL, signIn.getEmail());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }
    
    @Test
    public void signInNewSessionWithStudy() throws Exception {
        signInNewSession("study", true, null);
    }
    
    @Test
    public void signInNewSessionWithAppId() throws Exception {
        signInNewSession("appId", true, null);
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
        verify(mockRequest).setAttribute(eq("CreatedUserSession"), any(UserSession.class));
        
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
        verify(mockRequest).setAttribute(eq("CreatedUserSession"), any(UserSession.class));
        
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
                "{'sptoken':'"+TEST_TOKEN+"','study':'"+TEST_APP_ID+"'}");
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
                "{'sptoken':'"+TEST_TOKEN+"','study':'"+TEST_APP_ID+"'}");
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
        String json = TestUtils.createJson("{'study':'" + TEST_APP_ID + 
                "','email':'email@email.com','password':'bar'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.signInV3();
    }
    
    @Test
    public void localSignInSetsSessionCookie() throws Exception {
        when(mockConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(String.class));

        // mock request
        String requestJsonString = "{" +
                "\"email\":\"" + TEST_EMAIL + "\"," +
                "\"password\":\"" + TEST_PASSWORD + "\"," +
                "\"study\":\"" + TEST_APP_ID + "\"}";

        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(requestJsonString));

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(mockAuthService.signIn(any(), any(), any())).thenReturn(session);
        
        // execute and validate
        controller.signIn();
    }
    
    @Test
    public void signInOnLocalDoesNotSetCookieWithSSL() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_APP_ID + 
                "','email':'email@email.com','password':'bar'}");
        
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        when(controller.bridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        UserSession session = createSession(null, null);
        when(mockAuthService.signIn(any(), any(), any())).thenReturn(session);
        
        controller.signIn();
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void emailSignInBlockedByVersionKillSwitch() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_APP_ID + 
                "','email':'email@email.com','password':'bar'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.emailSignIn();
    }

    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void phoneSignInBlockedByVersionKillSwitch() throws Exception {
        String json = TestUtils.createJson(
                "{'study':'" + TEST_APP_ID + 
                "','email':'email@email.com','password':'bar'}");
        
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        when(mockRequest.getHeader(USER_AGENT)).thenReturn(USER_AGENT_STRING);
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.phoneSignIn();
    }
    
    @Test
    public void resendEmailVerificationWorks() throws Exception {
        mockSignInWithEmailPayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        
        AccountId accountId = AccountId.forEmail(TEST_APP_ID, TEST_EMAIL);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        controller.resendEmailVerification();
        
        verify(mockWorkflowService).resendVerification(ChannelType.EMAIL, TEST_APP_ID, TEST_USER_ID);
    }
    
    @Test
    public void resendPhoneVerificationWorks() throws Exception {
        mockSignInWithPhonePayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        
        AccountId accountId = AccountId.forPhone(TEST_APP_ID, PHONE);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        controller.resendPhoneVerification();
        
        verify(mockWorkflowService).resendVerification(ChannelType.PHONE, TEST_APP_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resendEmailVerificationAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resendEmailVerification();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendEmailVerificationNoStudy() throws Exception {
        String json = TestUtils.createJson("{'email':'email@email.com'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        controller.resendEmailVerification();
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resendPhoneVerificationAppVersionDisabled() throws Exception {
        mockSignInWithPhonePayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
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
        String json = TestUtils.createJson("{'study':'" + TEST_APP_ID + "','phone':{'number':'4082588569','regionCode':'US'}}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
        controller.resendPhoneVerification();
    }
    
    @Test
    public void resetPassword() throws Exception {
        mockResetPasswordRequest();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        StatusMessage message = controller.resetPassword();
        assertEquals(message.getMessage(), "Password has been changed.");
        
        verify(mockAuthService).resetPassword(passwordResetCaptor.capture());
        
        PasswordReset passwordReset = passwordResetCaptor.getValue();
        assertEquals("aSpToken", passwordReset.getSptoken());
        assertEquals("aPassword", passwordReset.getPassword());
        assertEquals(TEST_APP_ID, passwordReset.getAppId());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void resetPasswordAppVersionDisabled() throws Exception {
        mockResetPasswordRequest();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
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
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        StatusMessage message = controller.requestResetPassword();
        assertEquals(message.getMessage(), AuthenticationController.EMAIL_RESET_PWD_MSG);
        
        verify(mockWorkflowService).requestResetPassword(eq(app), eq(false), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_APP_ID, deser.getAppId());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }

    @Test
    public void requestResetPasswordWithPhone() throws Exception {
        mockSignInWithPhonePayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        StatusMessage message = controller.requestResetPassword();
        assertEquals(message.getMessage(), AuthenticationController.PHONE_RESET_PWD_MSG);
        
        verify(mockWorkflowService).requestResetPassword(eq(app), eq(false), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_APP_ID, deser.getAppId());
        assertEquals(TestConstants.PHONE.getNumber(), deser.getPhone().getNumber());
    }
    
    @Test(expectedExceptions = UnsupportedVersionException.class)
    public void requestResetPasswordAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        app.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20); // blocked
        
        controller.requestResetPassword();
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void requestResetPasswordNoStudy() throws Exception {
        when(mockAppService.getApp((String) any())).thenThrow(new EntityNotFoundException(App.class));
        
        mockRequestBody(mockRequest, new SignIn.Builder().withEmail(TEST_EMAIL).build());
        
        controller.requestResetPassword();
    }
    
    @Test
    public void signUpWithNoCheckForConsentDeclared() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_APP_ID);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpWithCheckForConsentDeclaredFalse() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_APP_ID);
        node.put("checkForConsent", false);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpWithCheckForConsentDeclaredTrue() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_APP_ID);
        node.put("checkForConsent", true);
        mockRequestBody(mockRequest, node);
        
        StatusMessage result = controller.signUp();
        assertEquals(result.getMessage(), "Signed up.");
        
        verify(mockAuthService).signUp(eq(app), participantCaptor.capture());
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
        assertEquals(result.getMessage(), AuthenticationController.PHONE_SIGNIN_REQUEST_MSG);

        // Verify.
        verify(mockWorkflowService).requestPhoneSignIn(signInCaptor.capture());

        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_APP_ID, captured.getAppId());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());

        verify(metrics).setAppId(TEST_APP_ID);
        verify(metrics).setUserId(TEST_ACCOUNT_ID);
    }
    
    @Test
    public void requestPhoneSignIn_NoUser() throws Exception {
        // Mock.
        mockRequestBody(mockRequest, PHONE_SIGN_IN_REQUEST);
        when(mockWorkflowService.requestPhoneSignIn(any())).thenReturn(null);

        // Execute.
        StatusMessage result = controller.requestPhoneSignIn();
        assertEquals(result.getMessage(), AuthenticationController.PHONE_SIGNIN_REQUEST_MSG);

        // Verify.
        verify(mockWorkflowService).requestPhoneSignIn(signInCaptor.capture());

        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_APP_ID, captured.getAppId());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());

        verify(metrics).setAppId(TEST_APP_ID);
        verify(metrics, never()).setUserId(any());
    }

    @Test
    public void phoneSignIn() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.channelSignIn(any(), any(), any())).thenReturn(userSession);
        
        JsonNode result = controller.phoneSignIn();
        
        // Returns user session.
        assertEquals(TEST_SESSION_TOKEN, result.get("sessionToken").textValue());
        assertEquals("UserSessionInfo", result.get("type").textValue());
        
        verify(mockAuthService).channelSignIn(eq(ChannelType.PHONE), contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(TEST_APP_ID, context.getAppId());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_APP_ID, captured.getAppId());
        assertEquals(TEST_TOKEN, captured.getToken());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        
        verifyCommonLoggingForSignIns();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void phoneSignInMissingStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withAppId(null)
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockRequestBody(mockRequest, badPhoneSignIn);
        
        controller.phoneSignIn();
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void phoneSignInBadStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withAppId("bad-study")
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockRequestBody(mockRequest, badPhoneSignIn);
        
        when(mockAppService.getApp((String)any())).thenThrow(new EntityNotFoundException(App.class));
        
        controller.phoneSignIn();
    }

    @Test
    public void failedPhoneSignInStillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        when(mockAuthService.channelSignIn(any(), any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.phoneSignIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setAppId(TEST_APP_ID);
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signInV3ThrowsNotFound() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.signIn(any(), any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signInV3();
    }

    @SuppressWarnings({ "deprecation" })
    @Test
    public void failedSignInV3StillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.signInV3();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setAppId(TEST_APP_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void signInV4ThrowsUnauthoried() throws Exception {
        mockRequestBody(mockRequest, PHONE_SIGN_IN);
        
        when(mockAuthService.signIn(any(), any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signIn();
    }

    @Test
    public void failedSignInV4StillLogsStudyId() throws Exception {
        // Set up test.
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any(), any())).thenThrow(EntityNotFoundException.class);

        // Execute.
        try {
            controller.signIn();
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // Verify metrics.
        verify(metrics).setAppId(TEST_APP_ID);
    }

    @Test
    public void unconsentedSignInSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(mockAuthService.signIn(any(), any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
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
        when(mockAuthService.channelSignIn(any(), any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
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
        when(mockAuthService.channelSignIn(any(), any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.phoneSignIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }
    
    @Test
    public void oauthSignIn() throws Exception {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "synapse", "authToken", "callbackUrl");
        mockRequestBody(mockRequest, token);
        
        when(mockAuthService.oauthSignIn(any(), any())).thenReturn(userSession);
        
        JsonNode node = controller.oauthSignIn();
        assertEquals(node.get("sessionToken").textValue(), TEST_SESSION_TOKEN);
        
        verifyCommonLoggingForSignIns();
        verify(mockAuthService).oauthSignIn(any(), eq(token));
    }
    
    @Test
    public void unconsentedReauthSetsMetrics() throws Exception {
        mockRequestBody(mockRequest, REAUTH_REQUEST);
        when(mockAuthService.reauthenticate(any(), any(), any())).thenThrow(new ConsentRequiredException(userSession));

        try {
            controller.reauthenticate();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
            // expected exception
        }
        verifyCommonLoggingForSignIns();
    }
    
    @Test
    public void changeAppAsAdmin() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withId(TEST_ACCOUNT_ID).withRoles(ImmutableSet.of(DEVELOPER, ADMIN)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        AccountId accountId = AccountId.forId("my-new-study", TEST_ACCOUNT_ID);
        when(mockAccountService.getAccountId("my-new-study", "synapseuserid:"+SYNAPSE_USER_ID))
            .thenReturn(Optional.of(TEST_ACCOUNT_ID));
        
        Account account = Account.create();
        when(mockAccountService.getAccount(accountId))
            .thenReturn(Optional.of(account));
        
        App newApp = App.create();
        newApp.setIdentifier("my-new-study");
        when(mockAppService.getApp("my-new-study")).thenReturn(newApp);

        UserSession session = new UserSession();
        session.setSessionToken("new-session-token");
        when(mockAuthService.getSession(eq(newApp), any())).thenReturn(session);
        
        JsonNode node = controller.changeApp();
        assertEquals(node.get("sessionToken").textValue(), "new-session-token");

        InOrder inOrder = Mockito.inOrder(mockAuthService, mockCacheProvider);
        inOrder.verify(mockAuthService).signOut(userSession);
        inOrder.verify(mockAuthService).getSession(eq(newApp), any());
        inOrder.verify(mockCacheProvider).setUserSession(session);
        assertTrue(session.isSynapseAuthenticated());
    }
    
    @Test
    public void changeAppAsSuperadmin() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withId(TEST_ACCOUNT_ID).withRoles(ImmutableSet.of(SUPERADMIN)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        App newApp = App.create();
        newApp.setIdentifier("my-new-study");
        when(mockAppService.getApp("my-new-study")).thenReturn(newApp);
        
        JsonNode node = controller.changeApp();
        assertEquals(node.get("sessionToken").textValue(), "session-token");
        assertTrue(userSession.isSynapseAuthenticated());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = NOT_SYNAPSE_AUTHENTICATED)
    public void changeAppNotAuthenticateWithSynapse() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withId(TEST_ACCOUNT_ID).withRoles(ImmutableSet.of(DEVELOPER, ADMIN)).build());
        userSession.setSynapseAuthenticated(false);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        controller.changeApp();
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = ".*" + APP_ACCESS_EXCEPTION_MSG + ".*")
    public void changeAppNotAuthorized() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        controller.changeApp();
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=".*Account has not been assigned a Synapse user ID.*")
    public void changeAppNotAssignedSynapseId() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        controller.changeApp();
    }
    
    @Test
    public void changeAppSupportsCrossStudyAdmin() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        // Note that the cross-app administrator does not have a synapse user ID
        userSession.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_ACCOUNT_ID).withRoles(ImmutableSet.of(SUPERADMIN)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_ACCOUNT_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(Account.create()));
        
        App newApp = App.create();
        newApp.setIdentifier("my-new-study");
        when(mockAppService.getApp("my-new-study")).thenReturn(newApp);

        JsonNode node = controller.changeApp();
        // Note that we reuse the session here, as we did in an initial implementation for cross-app
        // administrators.
        assertEquals(node.get("sessionToken").textValue(), "session-token");
        
        verify(mockSessionUpdateService).updateApp(userSession, newApp.getIdentifier());
        
        verify(mockAuthService, never()).signOut(any());
        verify(mockAuthService, never()).getSession(any(), any());
        verify(mockCacheProvider, never()).setUserSession(any());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = ".*" + APP_ACCESS_EXCEPTION_MSG + ".*")
    public void changeAppUserHasNoAccessToStudy() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withRoles(ImmutableSet.of(DEVELOPER)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        when(mockAccountService.getAppIdsForUser(SYNAPSE_USER_ID))
            .thenReturn(ImmutableList.of(TEST_APP_ID));
        
        App newApp = App.create();
        newApp.setIdentifier("my-new-study");
        when(mockAppService.getApp("my-new-study")).thenReturn(newApp);

        controller.changeApp();
    }
    
    // This would not appear to be logically possible, but to avoid a potention NPE exception
    // and a 500 error, so we check this.
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = ".*" + APP_ACCESS_EXCEPTION_MSG + ".*")
    public void changeAppWhereTheAccountSomehowDoesNotExist() throws Exception {
        mockRequestBody(mockRequest, new SignIn.Builder().withAppId("my-new-study").build());
        userSession.setParticipant(new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withRoles(ImmutableSet.of(DEVELOPER)).build());
        userSession.setSynapseAuthenticated(true);
        doReturn(userSession).when(controller).getAuthenticatedSession();
        
        App newApp = App.create();
        newApp.setIdentifier("my-new-study");
        when(mockAppService.getApp("my-new-study")).thenReturn(newApp);

        controller.changeApp();
    }
    
    @Test
    public void signInForSuperadmin() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withRoles(ImmutableSet.of(SUPERADMIN)).withEmail(EMAIL).build();
        userSession.setParticipant(participant);
        
        // This will use "api" which is hard-coded in the method, not the TEST_APP_ID
        when(mockAppService.getApp(BridgeConstants.API_APP_ID)).thenReturn(app);
        
        // Set environment to local in order to test that cookies are set
        when(mockConfig.getEnvironment()).thenReturn(LOCAL);
        when(mockConfig.get("domain")).thenReturn("localhost");

        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
                .withPassword(PASSWORD).build();
        mockRequestBody(mockRequest, signIn);

        when(mockAuthService.signIn(eq(app), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(userSession);

        JsonNode result = controller.signInForSuperAdmin();
        assertEquals(result.get("email").textValue(), EMAIL); // it's the session

        // This isn't in the session that is returned to the user, but verify it has been changed
        assertEquals(signInCaptor.getValue().getAppId(), BridgeConstants.API_APP_ID);
    }

    @Test
    public void signInForAdminNotASuperAdmin() throws Exception {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
                .withPassword("password").build();
        mockRequestBody(mockRequest, signIn);
        
        // This will use "api" which is hard-coded in the method, not the TEST_APP_ID
        when(mockAppService.getApp(BridgeConstants.API_APP_ID)).thenReturn(app);

        // But this person is actually a worker, not an admin
        userSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        when(mockAuthService.signIn(eq(app), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(userSession);

        try {
            controller.signInForSuperAdmin();
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
        }
        verify(mockAuthService).signOut(userSession);
    }
    
    private void mockResetPasswordRequest() throws Exception {
        String json = TestUtils.createJson("{'study':'" + TEST_APP_ID + 
            "','sptoken':'aSpToken','password':'aPassword'}");
        mockRequestBody(mockRequest, BridgeObjectMapper.get().readTree(json));
    }
    
    private void mockSignInWithEmailPayload() throws Exception {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(TEST_EMAIL).build();
        
        mockRequestBody(mockRequest, signIn);
    }

    private void mockSignInWithPhonePayload() throws Exception {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(PHONE).build();
        
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
        session.setAppId(TEST_APP_ID);
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
    
    private void verifyCommonLoggingForSignIns() throws Exception {
        verify(controller).updateRequestInfoFromSession(any(UserSession.class));
        verify(mockRequest).setAttribute(eq("CreatedUserSession"), any(UserSession.class));
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        verify(mockResponse, never()).addCookie(any());        
        RequestInfo info = requestInfoCaptor.getValue();
        assertEquals(NOW.getMillis(), info.getSignedInOn().getMillis());
    }
}
