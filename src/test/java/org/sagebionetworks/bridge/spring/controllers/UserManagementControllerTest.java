package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

public class UserManagementControllerTest extends Mockito {

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    StudyService mockStudyService;

    @Mock
    UserAdminService mockUserAdminService;

    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    RequestInfoService mockRequestInfoService;

    @Mock
    BridgeConfig mockBridgeConfig;

    @Mock
    Study mockStudy;

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
                .withId(USER_ID).withRoles(ImmutableSet.of(SUPERADMIN)).withEmail(EMAIL).build();

        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);

        sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        controller.setSessionUpdateService(sessionUpdateService);

        doReturn(session).when(mockUserAdminService).createUser(any(), any(), any(), anyBoolean(), anyBoolean());
        doReturn(session).when(mockAuthService).getSession(any(String.class));
        doReturn(mockStudy).when(mockStudyService).getStudy(TEST_STUDY);
        doReturn(mockStudy).when(mockStudyService).getStudy(TEST_STUDY_IDENTIFIER);

        when(mockStudy.getStudyIdentifier()).thenReturn(TEST_STUDY);
        doReturn(null).when(controller).getMetrics();

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UserManagementController.class);
        assertPost(UserManagementController.class, "signInForSuperAdmin");
        assertPost(UserManagementController.class, "changeStudyForAdmin");
        assertCreate(UserManagementController.class, "createUser");
        assertCreate(UserManagementController.class, "createUserWithStudyId");
        assertDelete(UserManagementController.class, "deleteUser");
    }

    @Test
    public void signInForSuperadmin() throws Exception {
        // Set environment to local in order to test that cookies are set
        when(mockBridgeConfig.getEnvironment()).thenReturn(LOCAL);
        when(mockBridgeConfig.get("domain")).thenReturn("localhost");

        SignIn signIn = new SignIn.Builder().withStudy("originalStudy").withEmail(EMAIL)
                .withPassword(PASSWORD).build();
        mockRequestBody(mockRequest, signIn);

        when(mockAuthService.signIn(eq(mockStudy), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(session);

        JsonNode result = controller.signInForSuperAdmin();
        assertEquals(result.get("email").textValue(), EMAIL); // it's the session

        // This isn't in the session that is returned to the user, but verify it has been changed
        assertEquals(session.getStudyIdentifier().getIdentifier(), "originalStudy");
        assertEquals(signInCaptor.getValue().getStudyId(), "api");

        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getName(), SESSION_TOKEN_HEADER);
        assertEquals(cookie.getValue(), session.getSessionToken());
        assertEquals(cookie.getMaxAge(), BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        assertEquals(cookie.getPath(), "/");
        assertEquals(cookie.getDomain(), "localhost");
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.getSecure());
    }

    @Test
    public void signInForAdminNotASuperAdmin() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy("originalStudy").withEmail(EMAIL)
                .withPassword("password").build();
        mockRequestBody(mockRequest, signIn);

        // But this person is actually a worker, not an admin
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        when(mockAuthService.signIn(eq(mockStudy), any(CriteriaContext.class), signInCaptor.capture()))
                .thenReturn(session);

        try {
            controller.signInForSuperAdmin();
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
        }
        verify(mockAuthService).signOut(session);
    }

    @Test
    public void changeStudyForAdmin() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Account.create());

        SignIn signIn = new SignIn.Builder().withStudy("nextStudy").build();
        mockRequestBody(mockRequest, signIn);

        Study nextStudy = Study.create();
        nextStudy.setIdentifier("nextStudy");
        when(mockStudyService.getStudy("nextStudy")).thenReturn(nextStudy);

        controller.changeStudyForAdmin();
        assertEquals(session.getStudyIdentifier().getIdentifier(), "nextStudy");
        verify(mockCacheProvider).setUserSession(session);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void changeStudyRejectsStudyAdmin() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        SignIn signIn = new SignIn.Builder().withStudy("nextStudy").build();
        mockRequestBody(mockRequest, signIn);
        
        controller.changeStudyForAdmin();
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
    public void createdResponseReturnsJSONPayloadWithStudyId() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        
        // same study id as above test
        StatusMessage result = controller.createUserWithStudyId(TEST_STUDY_IDENTIFIER);
        assertEquals(result, UserManagementController.CREATED_MSG);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createUserWithStudyIdRejectsStudyAdmin() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.createUserWithStudyId(TEST_STUDY_IDENTIFIER);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body fields: phone.*")
    public void createUserBadJson() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.createUser();
    }

    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body fields: phone.*")
    public void createUserWithStudyIdBadJson() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.createUserWithStudyId(TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void deleteUser() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        StatusMessage result = controller.deleteUser(USER_ID);
        assertEquals(result, UserManagementController.DELETED_MSG);

        verify(mockUserAdminService).deleteUser(mockStudy, USER_ID);
    }
}
