package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.OAuthService;
import org.sagebionetworks.bridge.services.StudyService;

public class OAuthControllerTest extends Mockito {
    
    private static final String NEXT_PAGE_OFFSET_KEY = "offsetKey2";
    private static final String OFFSET_KEY = "offsetKey";
    private static final String AUTH_TOKEN = "authToken";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String VENDOR_ID = "vendorId";
    private static final String PROVIDER_USER_ID = "providerUserId";
    private static final List<String> HEALTH_CODE_LIST = ImmutableList.of("a", "b", "c");
    // Set an offset so we can verify it exists.
    private static final DateTime EXPIRES_ON = DateTime.parse("2017-11-28T14:20:22.123-03:00");
    
    @Mock
    OAuthService mockOauthService;
    
    @Mock
    Study mockStudy;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<OAuthAuthorizationToken> authTokenCaptor;
    
    @Spy
    @InjectMocks
    OAuthController controller;
    
    UserSession session; 
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setStudyIdentifier(API_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        
        when(mockStudyService.getStudy(API_APP_ID)).thenReturn(mockStudy);
        when(mockStudyService.getStudy(API_APP_ID)).thenReturn(mockStudy);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(OAuthController.class);
        assertPost(OAuthController.class, "requestAccessToken");
        assertGet(OAuthController.class, "getHealthCodesGrantingAccess");
        assertGet(OAuthController.class, "getAccessToken");
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void requestAccessTokenMustBeAuthenticated() {
        session.setAuthenticated(false);
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.requestAccessToken(VENDOR_ID);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void requestAccessTokenMustBeConsented() throws Exception {
        session.setAuthenticated(true);
        
        Map<SubpopulationGuid, ConsentStatus> statuses = ImmutableMap.of(SubpopulationGuid.create("ABC"),
                TestConstants.REQUIRED_UNSIGNED);
        session.setConsentStatuses(statuses);
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.requestAccessToken(VENDOR_ID);
    }
    
    @Test
    public void requestAccessTokenWithAccessToken() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        OAuthAuthorizationToken authToken = new OAuthAuthorizationToken(API_APP_ID, null, AUTH_TOKEN, null);
        mockRequestBody(mockRequest, authToken);
        
        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID,
                null);
        when(mockOauthService.requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), any()))
                .thenReturn(accessToken);
        
        OAuthAccessToken result = controller.requestAccessToken(VENDOR_ID);
        
        assertEquals(result, accessToken);
        
        verify(mockOauthService).requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), authTokenCaptor.capture());
        OAuthAuthorizationToken captured = authTokenCaptor.getValue();
        assertEquals(captured.getAuthToken(), AUTH_TOKEN);
        assertEquals(captured.getVendorId(), VENDOR_ID);
    }
    
    @Test
    public void requestAccessTokenWithoutAccessToken() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        mockRequestBody(mockRequest, "{}");
        
        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID,
                null);
        when(mockOauthService.requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), any()))
                .thenReturn(accessToken);
        
        OAuthAccessToken result = controller.requestAccessToken(VENDOR_ID);
        
        assertEquals(result, accessToken);
        // verify that the time zone is preserved
        assertEquals(result.getExpiresOn().toString(), "2017-11-28T14:20:22.123-03:00");
        
        verify(mockOauthService).requestAccessToken(eq(mockStudy), eq(HEALTH_CODE), authTokenCaptor.capture());
        OAuthAuthorizationToken captured = authTokenCaptor.getValue();
        assertNull(captured.getAuthToken());
        assertEquals(captured.getVendorId(), VENDOR_ID);
    }

    @Test
    public void getHealthCodesGrantingAccessWithDefaults() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(HEALTH_CODE_LIST,
                NEXT_PAGE_OFFSET_KEY);
        when(mockOauthService.getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, API_DEFAULT_PAGE_SIZE,
                null)).thenReturn(page);
        
        ForwardCursorPagedResourceList<String> result = controller.getHealthCodesGrantingAccess(API_APP_ID,
                VENDOR_ID, null, null);
        
        verify(mockOauthService).getHealthCodesGrantingAccess(mockStudy, VENDOR_ID,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
        
        assertEquals(result.getItems(), HEALTH_CODE_LIST);
        assertEquals(result.getNextPageOffsetKey(), NEXT_PAGE_OFFSET_KEY);
        assertNull(result.getRequestParams().get(OFFSET_KEY));
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getHealthCodesGrantingAccessRequiresWorker() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.getHealthCodesGrantingAccess(API_APP_ID, VENDOR_ID, OFFSET_KEY, "20");
    }
    
    @Test
    public void getHealthCodesGrantingAccess() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(HEALTH_CODE_LIST,
                NEXT_PAGE_OFFSET_KEY).withRequestParam(OFFSET_KEY, OFFSET_KEY);
        when(mockOauthService.getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, 20, OFFSET_KEY)).thenReturn(page);
        
        ForwardCursorPagedResourceList<String> result = controller.getHealthCodesGrantingAccess(API_APP_ID,
                VENDOR_ID, OFFSET_KEY, "20");
        
        verify(mockOauthService).getHealthCodesGrantingAccess(mockStudy, VENDOR_ID, 20, OFFSET_KEY);
        
        assertEquals(result.getItems(), HEALTH_CODE_LIST);
        assertEquals(result.getNextPageOffsetKey(), NEXT_PAGE_OFFSET_KEY);
        assertEquals(result.getRequestParams().get(OFFSET_KEY), OFFSET_KEY);
    }
    
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAccessTokenRequiresWorker() throws Exception {
        doReturn(session).when(controller).getSessionIfItExists();
        
        controller.getAccessToken(API_APP_ID, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test
    public void getAccessToken() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER)).build());
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);

        OAuthAccessToken accessToken = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, EXPIRES_ON, PROVIDER_USER_ID,
                null);
        
        when(mockOauthService.getAccessToken(mockStudy, VENDOR_ID, HEALTH_CODE)).thenReturn(accessToken);
        
        OAuthAccessToken result = controller.getAccessToken(API_APP_ID, VENDOR_ID, HEALTH_CODE);
        
        assertEquals(result, accessToken);
        // verify that the time zone is preserved
        assertEquals(result.getExpiresOn().toString(), "2017-11-28T14:20:22.123-03:00");
        
        verify(mockOauthService).getAccessToken(mockStudy, VENDOR_ID, HEALTH_CODE);
    }
}
