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

import org.sagebionetworks.bridge.dao.RequestInfoDao;
import org.sagebionetworks.bridge.models.RequestInfo;

public class RequestInfoServiceTest extends Mockito {
    
    @InjectMocks
    RequestInfoService service;

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
    public void getRequestInfo() {
        RequestInfo info = new RequestInfo.Builder().build();
        when(mockRequestInfoDao.getRequestInfo(USER_ID)).thenReturn(info);
        
        RequestInfo retrieved = service.getRequestInfo(USER_ID);
        assertSame(retrieved, info);
        
        // No need to execute this path
        verify(mockRequestInfoDao, never()).updateRequestInfo(any());
    }
    
    @Test
    public void getRequestInfoReturnsNothing() {
        RequestInfo retrieved = service.getRequestInfo(USER_ID);
        assertNull(retrieved);
        verify(mockRequestInfoDao, never()).updateRequestInfo(any());
    }
    
    @Test
    public void removeRequestInfo() {
        service.removeRequestInfo(USER_ID);
        verify(mockRequestInfoDao).removeRequestInfo(USER_ID);
    }
}
