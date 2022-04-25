package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

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
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.IntegrationTestUserService;

public class IntegrationTestUserControllerTest extends Mockito {

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    AppService mockAppService;

    @Mock
    IntegrationTestUserService mockUserManagementService;

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
    IntegrationTestUserController controller;

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

        doReturn(session).when(mockUserManagementService).createUser(any(), any(), any(), anyBoolean(), anyBoolean());
        doReturn(session).when(mockAuthService).getSession(any(String.class));
        doReturn(mockApp).when(mockAppService).getApp(TEST_APP_ID);

        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        doReturn(null).when(controller).getMetrics();

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(IntegrationTestUserController.class);
        assertCreate(IntegrationTestUserController.class, "createUser");
        assertDelete(IntegrationTestUserController.class, "deleteUser");
    }

    @Test
    public void createdResponseReturnsJSONPayload() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        JsonNode result = controller.createUser();
        assertEquals(result.get("type").textValue(), "UserSessionInfo");
        assertEquals(result.get("email").textValue(), EMAIL);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields: phone.*")
    public void createUserBadJson() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        mockRequestBody(mockRequest, "{\"phone\": \"+1234567890\"}");
        
        controller.createUser();
    }
    
    @Test
    public void deleteUser() throws Exception {
        mockRequestBody(mockRequest, "{}");
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("AAA");

        StatusMessage result = controller.deleteUser(TEST_USER_ID);
        assertEquals(result, IntegrationTestUserController.DELETED_MSG);

        verify(mockUserManagementService).deleteUser(mockApp, TEST_USER_ID);
    }
}
