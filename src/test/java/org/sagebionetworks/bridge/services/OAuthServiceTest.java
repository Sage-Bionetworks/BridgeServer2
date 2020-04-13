package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.OAuthAccessGrantDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Lists;

public class OAuthServiceTest {
    private static final DateTime NOW = DateTime.now(DateTimeZone.UTC);
    private static final DateTime EXPIRES_ON = NOW.plusHours(3);
    private static final String HEALTH_CODE = "healthCode";
    private static final String VENDOR_ID = "vendorId";
    private static final String AUTH_TOKEN_STRING = "authToken";
    private static final String CLIENT_ID = "clientId";
    private static final String SECRET = "secret";
    private static final String ENDPOINT = "endpoint";
    private static final String CALLBACK_URL = "callbackUrl";
    private static final String INTROSPECT_URL = "http://example.com/introspect";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final List<String> SCOPE_LIST = ImmutableList.of("foo", "bar", "baz");
    private static final Set<String> SCOPE_SET = ImmutableSet.copyOf(SCOPE_LIST);
    private static final OAuthProvider PROVIDER = new OAuthProvider(CLIENT_ID, SECRET, ENDPOINT, CALLBACK_URL,
            INTROSPECT_URL);
    private static final OAuthAuthorizationToken AUTH_TOKEN = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, VENDOR_ID, AUTH_TOKEN_STRING, null);
    private static final OAuthAuthorizationToken NO_AUTH_TOKEN = new OAuthAuthorizationToken(TEST_STUDY_IDENTIFIER, VENDOR_ID, null, null);
    
    @Spy
    private OAuthService service;
    
    @Mock
    private OAuthAccessGrantDao mockGrantDao;
    
    @Mock
    private OAuthProviderService mockProviderService;
    
    @Captor
    private ArgumentCaptor<OAuthAccessGrant> grantCaptor;
    
    private Study study;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        service.setOAuthAccessGrantDao(mockGrantDao);
        service.setOAuthProviderService(mockProviderService);
        
        Map<String,OAuthProvider> providers = new HashMap<String,OAuthProvider>();
        providers.put("vendorId", PROVIDER);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setOAuthProviders(providers);
        
        when(service.getDateTime()).thenReturn(NOW);
    }
    
    private OAuthAccessGrant createGrant(DateTime expiresOn) {
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setCreatedOn(NOW.getMillis());
        grant.setExpiresOn(expiresOn.getMillis());
        grant.setAccessToken(ACCESS_TOKEN);
        grant.setVendorId(VENDOR_ID);
        grant.setRefreshToken(REFRESH_TOKEN);
        grant.setScopes(SCOPE_LIST);
        return grant;
    }
    
    private void setupDaoWithCurrentGrant() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockGrantDao.getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE)).thenReturn(grant);
    }
    private void setupDaoWithExpiredGrant() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON.minusHours(4));
        when(mockGrantDao.getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE)).thenReturn(grant);
    }
    
    private void setupValidGrantCall() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockProviderService.requestAccessGrant(PROVIDER, AUTH_TOKEN)).thenReturn(grant);
    }
    private void setupInvalidGrantCall() {
        setupInvalidGrantCall(new EntityNotFoundException(OAuthAccessGrant.class));
    }
    private void setupInvalidGrantCall(BridgeServiceException e) {
        when(mockProviderService.requestAccessGrant(PROVIDER, AUTH_TOKEN))
                .thenThrow(e);
    }
    
    private void setupValidRefreshCall() {
        OAuthAccessGrant grant = createGrant(EXPIRES_ON);
        when(mockProviderService.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN)).thenReturn(grant);
    }
    private void setupInvalidRefreshCall() {
        when(mockProviderService.refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN))
                .thenThrow(new EntityNotFoundException(OAuthAccessGrant.class));
    }
    
    private void assertAccessToken(OAuthAccessToken authToken) {
        assertEquals(authToken.getVendorId(), VENDOR_ID);
        assertEquals(authToken.getAccessToken(), ACCESS_TOKEN);
        assertEquals(authToken.getExpiresOn(), EXPIRES_ON);
        assertEquals(authToken.getScopes(), SCOPE_SET);
    }
    private void assertGrant(OAuthAccessGrant grant) {
        assertEquals(grant.getHealthCode(), HEALTH_CODE);
        assertEquals(grant.getVendorId(), VENDOR_ID);
        assertEquals(grant.getAccessToken(), ACCESS_TOKEN);
        assertEquals(grant.getRefreshToken(), REFRESH_TOKEN);
        assertEquals(grant.getScopes(), SCOPE_SET);
        assertEquals(grant.getCreatedOn(), NOW.getMillis());
        assertEquals(grant.getExpiresOn(), EXPIRES_ON.getMillis());
    }
    
    // All of these tests are for requestAccessToken(...)
    
    @Test
    public void requestCurrentGrant() {
        setupDaoWithCurrentGrant();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TEST_STUDY_IDENTIFIER), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void requestExpiredGrantValidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupValidRefreshCall();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verify(mockGrantDao).saveAccessGrant(eq(TEST_STUDY_IDENTIFIER), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void requestExpiredGrantInvalidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupInvalidRefreshCall();
        
        try {
            service.requestAccessToken(study, HEALTH_CODE, NO_AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        verify(mockGrantDao).getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verify(mockGrantDao).deleteAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }
    
    @Test
    public void requestNoGrantValidAuthToken() {
        setupValidGrantCall();
        
        OAuthAccessToken token = service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
        
        assertAccessToken(token);
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verify(mockGrantDao).saveAccessGrant(eq(TEST_STUDY_IDENTIFIER), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getHealthCodesThrowsExceptionOnIncorrectOAuthProvider() {
        service.getHealthCodesGrantingAccess(study, "notAVendor", 50, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getHealthCodesThrowsExceptionOnIncorrectOAuthProvider2() {
        service.getHealthCodesGrantingAccess(study, "notAVendor", 50, null);
    }
    
    @Test
    public void requestNoGrantInvalidAuthToken() {
        setupInvalidGrantCall();
        try {
            service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verify(mockGrantDao).deleteAccessGrant(study.getIdentifier(), VENDOR_ID, HEALTH_CODE);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }

    // getAccessToken tests
    
    @Test
    public void getCurrentGrant() {
        setupDaoWithCurrentGrant();
        
        OAuthAccessToken token = service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TEST_STUDY_IDENTIFIER), grantCaptor.capture());
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test
    public void getExpiredGrantValidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupValidRefreshCall();

        OAuthAccessToken token = service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
        
        assertAccessToken(token);
        verify(mockGrantDao).getAccessGrant(TEST_STUDY_IDENTIFIER, VENDOR_ID, HEALTH_CODE);
        verify(mockGrantDao).saveAccessGrant(eq(TEST_STUDY_IDENTIFIER), grantCaptor.capture());
        verify(mockProviderService).refreshAccessGrant(PROVIDER, VENDOR_ID, REFRESH_TOKEN);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
        assertGrant(grantCaptor.getValue());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getExpiredGrantInvalidRefreshToken() {
        setupDaoWithExpiredGrant(); 
        setupInvalidRefreshCall();

        service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getNoGrant() {
        service.getAccessToken(study, VENDOR_ID, HEALTH_CODE);
    }
    
    @Test
    public void getHealthCodesGrantingAccess() {
        List<OAuthAccessGrant> items = Lists.newArrayList();
        items.add(OAuthAccessGrant.create());
        items.add(OAuthAccessGrant.create());
        ForwardCursorPagedResourceList<OAuthAccessGrant> page = new ForwardCursorPagedResourceList<>(items, "nextPageOffset")
                .withRequestParam("offsetKey", "offsetKey");
        
        when(mockGrantDao.getAccessGrants(study.getIdentifier(), VENDOR_ID, "offsetKey", 30)).thenReturn(page);
        
        ForwardCursorPagedResourceList<String> results = service.getHealthCodesGrantingAccess(study, VENDOR_ID, 30,
                "offsetKey");
        
        verify(mockGrantDao).getAccessGrants(study.getIdentifier(), VENDOR_ID, "offsetKey", 30);
        // Just verify a couple of fields to verify this is the page returned
        assertEquals(results.getNextPageOffsetKey(), "nextPageOffset");
        assertEquals(results.getItems().size(), 2);
    }
    
    @Test
    public void transientProviderErrorDoesNotDeleteGrant() {
        setupInvalidGrantCall(new BridgeServiceException("Temporary error", 503));
        
        try {
            service.requestAccessToken(study, HEALTH_CODE, AUTH_TOKEN);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            
        }
        verify(mockProviderService).requestAccessGrant(PROVIDER, AUTH_TOKEN);
        verifyNoMoreInteractions(mockGrantDao);
        verifyNoMoreInteractions(mockProviderService);
    }
}
