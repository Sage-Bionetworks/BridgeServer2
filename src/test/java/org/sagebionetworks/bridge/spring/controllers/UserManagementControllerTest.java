package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UserAdminService;

public class UserManagementControllerTest extends Mockito {

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    AppService mockAppService;

    @Mock
    UserAdminService mockUserAdminService;

    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    RequestInfoService mockRequestInfoService;

    @Mock
    BridgeConfig mockBridgeConfig;

    @Mock
    App mockApp;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    AccountService mockAccountService;
    
    @Spy
    @InjectMocks
    UserManagementController controller;

    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    ArgumentCaptor<Cookie> cookieCaptor;

    SessionUpdateService sessionUpdateService;

    UserSession session;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withId(TEST_USER_ID).withRoles(ImmutableSet.of(SUPERADMIN)).withEmail(EMAIL).build();

        session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        session.setAuthenticated(true);

        sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);

        doReturn(session).when(mockUserAdminService).createUser(any(), any(), any(), anyBoolean(), anyBoolean());
        doReturn(session).when(mockAuthService).getSession(any(String.class));
        doReturn(mockApp).when(mockAppService).getApp(TEST_APP_ID);

        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        doReturn(null).when(controller).getMetrics();

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UserManagementController.class);
        assertPost(UserManagementController.class, "signInForSuperAdmin");
        assertPost(UserManagementController.class, "changeAppForAdmin");
        assertCreate(UserManagementController.class, "createUser");
        assertCreate(UserManagementController.class, "createUserWithAppId");
        assertDelete(UserManagementController.class, "deleteUser");
    }

    @Test
    public void signInForSuperadmin() throws Exception {
        // We look specifically for an account in the API app
        doReturn(mockApp).when(mockAppService).getApp(API_APP_ID);
        
        // Set environment to local in order to test that cookies are set
        when(mockBridgeConfig.getEnvironment()).thenReturn(LOCAL);
        when(mockBridgeConfig.get("domain")).thenReturn("localhost");

        SignIn signIn = new SignIn.Builder().withAppId("originalStudy").withEmail(EMAIL)
                .withPassword(PASSWORD).build();
        mockRequestBody(mockRequest, signIn);

        when(mockAuthService.signIn(eq(mockApp), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(session);

        JsonNode result = controller.signInForSuperAdmin();
        assertEquals(result.get("email").textValue(), EMAIL); // it's the session

        // This isn't in the session that is returned to the user, but verify it has been changed
        assertEquals(session.getAppId(), "originalStudy");
        assertEquals(signInCaptor.getValue().getAppId(), API_APP_ID);
    }

    @Test
    public void signInForAdminNotASuperAdmin() throws Exception {
        // We look specifically for an account in the API app
        doReturn(mockApp).when(mockAppService).getApp(API_APP_ID);
        
        SignIn signIn = new SignIn.Builder().withAppId("originalStudy").withEmail(EMAIL)
                .withPassword("password").build();
        mockRequestBody(mockRequest, signIn);

        // But this person is actually a worker, not an admin
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        when(mockAuthService.signIn(eq(mockApp), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(session);

        try {
            controller.signInForSuperAdmin();
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
        }
        verify(mockAuthService).signOut(session);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void changeStudyForAdmin() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Account.create());

        SignIn signIn = new SignIn.Builder().withAppId("nextStudy").build();
        mockRequestBody(mockRequest, signIn);

        App nextApp = App.create();
        nextApp.setIdentifier("nextStudy");
        when(mockAppService.getApp("nextStudy")).thenReturn(nextApp);

        controller.changeAppForAdmin();
        assertEquals(session.getAppId(), "nextStudy");
        verify(mockCacheProvider).setUserSession(session);
    }
    
    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = UnauthorizedException.class)
    public void changeStudyRejectsStudyAdmin() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        SignIn signIn = new SignIn.Builder().withAppId("nextStudy").build();
        mockRequestBody(mockRequest, signIn);
        
        controller.changeAppForAdmin();
    }

    @Test
    public void createdResponseReturnsJSONPayload() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        JsonNode result = controller.createUser();
        assertEquals(result.get("type").textValue(), "UserSessionInfo");
        assertEquals(result.get("email").textValue(), EMAIL);
    }

    @Test
    public void createdResponseReturnsJSONPayloadWithAppId() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        
        // same app id as above test
        StatusMessage result = controller.createUserWithAppId(TEST_APP_ID);
        assertEquals(result, UserManagementController.CREATED_MSG);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createUserWithAppIdRejectsStudyAdmin() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.createUserWithAppId(TEST_APP_ID);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields: phone.*")
    public void createUserBadJson() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.createUser();
    }

    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields: phone.*")
    public void createUserWithAppIdBadJson() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.createUserWithAppId(TEST_APP_ID);
    }
    
    @Test
    public void deleteUser() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        StatusMessage result = controller.deleteUser(TEST_USER_ID);
        assertEquals(result, UserManagementController.DELETED_MSG);

        verify(mockUserAdminService).deleteUser(mockApp, TEST_USER_ID);
    }
}
