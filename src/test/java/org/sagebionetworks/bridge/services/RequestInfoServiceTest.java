package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.RequestInfoDao;
import org.sagebionetworks.bridge.models.RequestInfo;

public class RequestInfoServiceTest extends Mockito {
    
    @InjectMocks
    RequestInfoService service;

    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    RequestInfoDao mockRequestInfoDao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void updateRequestInfo() {
        RequestInfo info = new RequestInfo.Builder().build();
        service.updateRequestInfo(info);
        
        verify(mockRequestInfoDao).updateRequestInfo(info);
    }
    
    @Test
    public void getRequestInfoFromDatabase() {
        RequestInfo info = new RequestInfo.Builder().build();
        when(mockRequestInfoDao.getRequestInfo(USER_ID)).thenReturn(info);
        
        RequestInfo retrieved = service.getRequestInfo(USER_ID);
        assertSame(retrieved, info);
        
        // No need to execute this path
        verify(mockCacheProvider, never()).getRequestInfo(any());
        verify(mockRequestInfoDao, never()).updateRequestInfo(any());
    }
    
    @Test
    public void getRequestInfoFromCache() {
        RequestInfo info = new RequestInfo.Builder().build();
        when(mockCacheProvider.getRequestInfo(USER_ID)).thenReturn(info);
        
        RequestInfo retrieved = service.getRequestInfo(USER_ID);
        assertSame(retrieved, info);
        
        // And it was saved
        verify(mockRequestInfoDao).updateRequestInfo(info);
    }
    
    @Test
    public void getRequestInfoReturnsNothing() {
        RequestInfo retrieved = service.getRequestInfo(USER_ID);
        assertNull(retrieved);
    }
    
    @Test
    public void removeRequestInfo() {
        service.removeRequestInfo(USER_ID);
        verify(mockRequestInfoDao).removeRequestInfo(USER_ID);
    }
}
