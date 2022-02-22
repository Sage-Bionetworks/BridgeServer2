package org.sagebionetworks.bridge.spring.util;

import static com.google.common.net.HttpHeaders.IF_NONE_MATCH;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Study;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;

public class EtagComponentTest extends Mockito {
    
    private static final String ETAG = "45544147";

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
    public void beforeMethod() throws Throwable {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(component).request();
        doReturn(mockResponse).when(component).response();
        doReturn(mockContext).when(component).context(mockJoinPoint);
        
        EtagCacheKey studyKeyAnn = new EtagCacheKey() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EtagCacheKey.class;
            }
            @Override
            public Class<?> model() {
                return Study.class;
            }
            @Override
            public String[] keys() {
                return new String[] {"appId", "studyId"};
            }
        };
        EtagCacheKey userKeyAnn = new EtagCacheKey() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EtagCacheKey.class;
            }
            @Override
            public Class<?> model() {
                return Account.class;
            }
            @Override
            public String[] keys() {
                return new String[] {"userId"};
            }
        };
        doReturn(Timeline.class).when(mockContext).getModel();
        doReturn(ImmutableList.of(studyKeyAnn, userKeyAnn)).when(mockContext).getCacheKeys();
        doReturn(ImmutableMap.of("studyId", TEST_STUDY_ID)).when(mockContext).getArgValues();
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        when(mockSession.getId()).thenReturn(TEST_USER_ID);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID).build());
        when(mockRequest.getHeader(SESSION_TOKEN_HEADER)).thenReturn("ABC");
        when(mockCacheProvider.getUserSession("ABC")).thenReturn(mockSession);
        
        CacheKey studyKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        when(mockCacheProvider.getObject(studyKey, DateTime.class)).thenReturn(CREATED_ON);
        CacheKey userKey = CacheKey.etag(Account.class, TEST_USER_ID);
        when(mockCacheProvider.getObject(userKey, DateTime.class)).thenReturn(MODIFIED_ON);
        
        String stringToHash = CREATED_ON.toString() + " " + MODIFIED_ON.toString();
        when(mockMd5DigestUtils.digest(stringToHash.getBytes())).thenReturn("ETAG".getBytes());
        
        when(mockJoinPoint.proceed()).thenReturn(ACCOUNT_ID);
    }
    
    @Test
    public void cacheHit() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertNull(retValue);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse).setStatus(304);
    }

    @Test
    public void cacheMiss() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn("SOME-OTHER-ETAG");
        
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertEquals(retValue, ACCOUNT_ID);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse, never()).setStatus(304);
    }
    
    @Test
    public void cacheMiss_noEtag() throws Throwable {
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertEquals(retValue, ACCOUNT_ID);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse, never()).setStatus(304);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void missingSession_notAuthenticated() throws Throwable {
        when(mockCacheProvider.getUserSession(any())).thenReturn(null);
        component.checkEtag(mockJoinPoint);
    }

    @Test
    public void methodParamsOverrideSessionValues() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        when(mockSession.getId()).thenReturn("this-is-not-the-right-value");
        doReturn(ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID)).when(mockContext).getArgValues();
        
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertNull(retValue);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse).setStatus(304);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "EtagSupport: no value for key: studyId")
    public void nullArgThrowsException() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        doReturn(ImmutableMap.of()).when(mockContext).getArgValues();
        
        component.checkEtag(mockJoinPoint);
    }
    
    @Test
    public void cacheMiss_timestampMissing() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        CacheKey userKey = CacheKey.etag(Account.class, TEST_USER_ID);
        when(mockCacheProvider.getObject(userKey, DateTime.class)).thenReturn(null);
        
        Object retValue = component.checkEtag(mockJoinPoint);
        assertEquals(retValue, ACCOUNT_ID);
    }
    
    // retrieving appId and userId are tested in the default set up for these tests.
    
    @Test
    public void orgIdRetrievedFromSession() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        EtagCacheKey orgKeyAnn = new EtagCacheKey() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EtagCacheKey.class;
            }
            @Override
            public Class<?> model() {
                return Organization.class;
            }
            @Override
            public String[] keys() {
                return new String[] {"orgId"};
            }
        };
        doReturn(ImmutableList.of(orgKeyAnn)).when(mockContext).getCacheKeys();
        
        CacheKey orgKey = CacheKey.etag(Organization.class, TEST_ORG_ID);
        when(mockCacheProvider.getObject(orgKey, DateTime.class)).thenReturn(MODIFIED_ON);
        
        String stringToHash = MODIFIED_ON.toString();
        when(mockMd5DigestUtils.digest(stringToHash.getBytes())).thenReturn("ETAG".getBytes());
        
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertNull(retValue);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse).setStatus(304);        
    }
    
    // This will probably never trigger in reality, since the values are usually path parameters
    // in the controller. But it's logically possible.
    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = "EtagSupport: no value for key: studyId")
    public void shouldHaveProvidedArgumentValueButDidNot() throws Throwable {
        when(mockRequest.getHeader(IF_NONE_MATCH)).thenReturn(ETAG);
        
        Map<String, Object> map = new HashMap<>();
        map.put("studyId", null);
        doReturn(map).when(mockContext).getArgValues();
        
        Object retValue = component.checkEtag(mockJoinPoint);
        
        assertNull(retValue);
        verify(mockResponse).addHeader(HttpHeaders.ETAG, ETAG);
        verify(mockResponse).setStatus(304);
    }
}
