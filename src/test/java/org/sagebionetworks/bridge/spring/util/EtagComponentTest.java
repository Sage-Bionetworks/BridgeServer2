package org.sagebionetworks.bridge.spring.util;

import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;

public class EtagComponentTest extends Mockito {
    
    private static final String TEST_OTHER_STUDY_ID = "some-other-study";
    private static final String TEST_ETAG_HASH = "ABCD";
    private static final String TEST_SESSION_TOKEN = "session-token";
    private static final Timeline TIMELINE = new Timeline.Builder().build();

    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    DigestUtils mockMd5DigestUtils;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    ProceedingJoinPoint mockJoinPoint;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    UserSession mockSession;
    
    @Mock
    EtagContext mockContext;
    
    @InjectMocks
    @Spy
    EtagComponent component;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(component).request();
        doReturn(mockResponse).when(component).response();
        doReturn(Timeline.class).when(mockContext).getModel();
        doReturn(mockContext).when(component).context(mockJoinPoint);
    }
    
    @Test
    public void testCacheHit() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // Mock the cache provider
        CacheKey key = CacheKey.etag(Timeline.class, new String[] {TEST_APP_ID, TEST_STUDY_ID});
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(TEST_ETAG_HASH);

        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_STUDY_ID));

        Object retValue = component.checkEtag(mockJoinPoint);
        assertNull(retValue);
        verify(mockResponse).setStatus(304);
    }
    
    @Test
    public void testCacheMissNoHeader() throws Throwable {
        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_OTHER_STUDY_ID));
        
        // mock join point return value and the hash calculated from it
        byte[] hash = mockMethodCall();

        Object retValue = component.checkEtag(mockJoinPoint);
        assertEquals(retValue, TIMELINE);
        
        CacheKey newKey = CacheKey.etag(Timeline.class, TEST_APP_ID, TEST_OTHER_STUDY_ID);
        String newHash = Hex.encodeHexString(hash);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, newHash);
        verify(mockCacheProvider).setObject(newKey, newHash);
    }

    @Test
    public void testCacheMissNoCacheEntry() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);

        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_OTHER_STUDY_ID));
        
        // mock join point return value and the hash calculated from it
        byte[] hash = mockMethodCall();

        Object retValue = component.checkEtag(mockJoinPoint);
        assertEquals(retValue, TIMELINE);
        
        CacheKey newKey = CacheKey.etag(Timeline.class, TEST_APP_ID, TEST_OTHER_STUDY_ID);
        String newHash = Hex.encodeHexString(hash);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, newHash);
        verify(mockCacheProvider).setObject(newKey, newHash);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "EtagSupport: no value for key: appId")
    public void missingSessionThrowsError() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // Mock the cache provider
        CacheKey key = CacheKey.etag(Timeline.class, new String[] {TEST_APP_ID, TEST_STUDY_ID});
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(TEST_ETAG_HASH);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_STUDY_ID));

        component.checkEtag(mockJoinPoint);
    }

    @Test
    public void missingSessionOkWhenNotNeeded() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // There is no session, but the cache key doesn't need anything from it. This is ok.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_STUDY_ID));

        // mock join point return value and the hash calculated from it
        byte[] hash = mockMethodCall();

        Object retValue = component.checkEtag(mockJoinPoint);
        assertEquals(retValue, TIMELINE);
        
        CacheKey newKey = CacheKey.etag(Timeline.class, TEST_STUDY_ID);
        String newHash = Hex.encodeHexString(hash);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, newHash);
        verify(mockCacheProvider).setObject(newKey, newHash);
    }
    
    @Test
    public void methodParamsOverrideSessionValues() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // Mock the cache provider
        CacheKey key = CacheKey.etag(Timeline.class, new String[] {TEST_APP_ID, TEST_USER_ID});
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(TEST_ETAG_HASH);

        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        when(mockSession.getId()).thenReturn("some-other-id");
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "userId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("userId", TEST_USER_ID));

        Object retValue = component.checkEtag(mockJoinPoint);
        assertNull(retValue);
        verify(mockResponse).setStatus(304);
        
        verify(mockSession, never()).getId(); // not needed
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "EtagSupport: no value for key: studyId")
    public void nullArgThrowsException() throws Throwable {
        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        Map<String,Object> map = new HashMap<>();
        map.put("studyId", null);
        when(mockContext.getArgValues()).thenReturn(map);

        component.checkEtag(mockJoinPoint);
    }
    
    // appIdRetrievedFromSession is tested by testCacheHit
    
    @Test
    public void userIdRetrievedFromSession() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // Mock the cache provider
        CacheKey key = CacheKey.etag(Timeline.class, new String[] {TEST_APP_ID, TEST_USER_ID});
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(TEST_ETAG_HASH);

        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        when(mockSession.getId()).thenReturn(TEST_USER_ID);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "userId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_USER_ID));

        Object retValue = component.checkEtag(mockJoinPoint);
        assertNull(retValue);
        verify(mockResponse).setStatus(304);
    }
    
    @Test
    public void orgUserIdRetrievedFromSession() throws Throwable {
        // Mock the request
        when(mockRequest.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(TEST_ETAG_HASH);
        
        // Mock the cache provider
        CacheKey key = CacheKey.etag(Timeline.class, new String[] {TEST_APP_ID, TEST_ORG_ID});
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(TEST_ETAG_HASH);

        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        when(mockSession.getParticipant()).thenReturn(
                new StudyParticipant.Builder().withOrgMembership(TEST_ORG_ID).build());
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "orgId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_ORG_ID));

        Object retValue = component.checkEtag(mockJoinPoint);
        assertNull(retValue);
        verify(mockResponse).setStatus(304);
    }
    
    @Test
    public void noEtagSkipsCaching() throws Throwable {
        // Mock the session
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn(TEST_SESSION_TOKEN);
        when(mockCacheProvider.getUserSession(TEST_SESSION_TOKEN)).thenReturn(mockSession);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        // In this test, one value will come from the method arguments, and one will come 
        // from the user’s session.
        when(mockContext.getCacheKeys()).thenReturn(ImmutableList.of("appId", "studyId"));
        when(mockContext.getArgValues()).thenReturn(ImmutableMap.of("studyId", TEST_STUDY_ID));
        
        byte[] hash = mockMethodCall();

        Object retValue = component.checkEtag(mockJoinPoint);
        assertEquals(retValue, TIMELINE);
        
        CacheKey newKey = CacheKey.etag(Timeline.class, TEST_APP_ID, TEST_STUDY_ID);
        String newHash = Hex.encodeHexString(hash);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, newHash);
        verify(mockCacheProvider).setObject(newKey, newHash);
        
        verify(mockCacheProvider, never()).getObject(any(), eq(String.class));
    }
    
    private byte[] mockMethodCall() throws Throwable { 
        // mock join point return value and the hash calculated from it
        when(mockJoinPoint.proceed()).thenReturn(TIMELINE);
        byte[] hash = "EFGH".getBytes();
        when(mockMd5DigestUtils.digest((byte[])any())).thenReturn(hash);
        return hash;
    }
}
