package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertNotNull;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.RequestInfo;

public class HibernateRequestInfoDaoTest extends Mockito {
    
    @InjectMocks
    HibernateRequestInfoDao dao;
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void updateRequestInfoNoExistingObject() {
        RequestInfo newInfo = new RequestInfo.Builder().withUserId(USER_ID).build();
        when(mockHelper.getById(RequestInfo.class, USER_ID)).thenReturn(null);
        
        dao.updateRequestInfo(newInfo);

        verify(mockHelper).create(newInfo);
    }
    
    @Test
    public void updateRequestInfoMergedWithExistingObject() {
        RequestInfo newInfo = new RequestInfo.Builder().withUserId(USER_ID)
                .withActivitiesAccessedOn(DateTime.now()).build();
        RequestInfo existingInfo = new RequestInfo.Builder().withUserId(USER_ID)
                .withSignedInOn(DateTime.now()).build();
        when(mockHelper.getById(RequestInfo.class, USER_ID)).thenReturn(existingInfo);
        
        dao.updateRequestInfo(newInfo);

        verify(mockHelper).update(requestInfoCaptor.capture());
        
        RequestInfo captured = requestInfoCaptor.getValue();
        assertNotNull(captured.getActivitiesAccessedOn());
        assertNotNull(captured.getSignedInOn());
    }

    @Test
    public void getRequestInfo() {
        dao.getRequestInfo(USER_ID);
        verify(mockHelper).getById(RequestInfo.class, USER_ID);
    }

    @Test
    public void removeRequestInfo() {
        RequestInfo existingInfo = new RequestInfo.Builder().withUserId(USER_ID)
                .withSignedInOn(DateTime.now()).build();
        when(mockHelper.getById(RequestInfo.class, USER_ID)).thenReturn(existingInfo);
        
        dao.removeRequestInfo(USER_ID);
        verify(mockHelper).deleteById(RequestInfo.class, USER_ID);
    }       
    
    @Test
    public void removeRequestInfoNoObject() {
        dao.removeRequestInfo(USER_ID);
        verify(mockHelper, never()).deleteById(any(), any());
    }       
}
