package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.dynamodb.DynamoAppDao.APP_WHITELIST_PROPERTY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.App;

public class DynamoAppDaoTest extends Mockito {

    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedScanList<DynamoApp> mockScanList;
    
    @Captor
    ArgumentCaptor<App> studyCaptor;
    
    @InjectMocks
    DynamoAppDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getPropertyAsList(APP_WHITELIST_PROPERTY)).thenReturn(ImmutableList.of("whitelisted-study"));
        dao.setBridgeConfig(config);
    }
    
    @Test
    public void doesIdentifierExistSucceeds() {
        DynamoApp saved = new DynamoApp();
        when(mockMapper.load(any())).thenReturn(saved);
        
        boolean returned = dao.doesIdentifierExist(TEST_APP_ID);
        assertTrue(returned);
        
        verify(mockMapper).load(studyCaptor.capture());
        
        App app = studyCaptor.getValue();
        assertEquals(app.getIdentifier(), TEST_APP_ID);
    }
    
    @Test
    public void doesIdentifierExistFails() {
        boolean returned = dao.doesIdentifierExist(TEST_APP_ID);
        assertFalse(returned);
    }
    
    @Test
    public void getStudy() {
        DynamoApp saved = new DynamoApp();
        doReturn(saved).when(mockMapper).load(any());
        
        App result = dao.getApp(TEST_APP_ID);
        assertSame(result, saved);
        
        verify(mockMapper).load(studyCaptor.capture());
        App key = studyCaptor.getValue();
        assertEquals(key.getIdentifier(), TEST_APP_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getStudyNotFound() {
        dao.getApp(TEST_APP_ID);   
    }
    
    @Test
    public void getStudies() {
        List<App> saved = ImmutableList.of(App.create(), App.create());
        when(mockScanList.toArray()).thenReturn(saved.toArray());
        when(mockMapper.scan(eq(DynamoApp.class), any(DynamoDBScanExpression.class))).thenReturn(mockScanList);
        
        List<App> result = dao.getApps();
        assertEquals(result.size(), 2);
        assertEquals(result, saved);

        verify(mockMapper).scan(eq(DynamoApp.class), any(DynamoDBScanExpression.class));
    }
    
    @Test
    public void createStudy() {
        App app = App.create();
        
        dao.createApp(app);
        
        verify(mockMapper).save(app);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void createStudyFailsIfVersionPresent() {
        App app = App.create();
        app.setVersion(2L);
        
        dao.createApp(app);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createStudyConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        dao.createApp(app);
    }
    
    @Test
    public void updateStudy() {
        App app = App.create();
        app.setVersion(2L);
        
        dao.updateApp(app);
        
        verify(mockMapper).save(app);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void updateStudyWithoutVersionFails() {
        App app = App.create();
        
        dao.updateApp(app);
    }
    
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void updateStudyConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        
        App app = App.create();
        app.setVersion(2L);

        dao.updateApp(app);
    }
    
    @Test
    public void deleteStudy() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        dao.deleteApp(app);
        
        verify(mockMapper).delete(app);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyRespectsWhitelist() {
        App app = App.create();
        app.setIdentifier("whitelisted-study");
        
        dao.deleteApp(app);
    }
    
    @Test
    public void deactivateStudy() {
        App app = App.create();
        app.setActive(true);
        app.setVersion(2L);
        when(mockMapper.load(any())).thenReturn(app);
        
        dao.deactivateApp(TEST_APP_ID);
        
        verify(mockMapper).save(studyCaptor.capture());
        assertFalse(studyCaptor.getValue().isActive());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deactivateStudyRespectsWhitelist() {
        App app = App.create();
        app.setActive(true);
        app.setVersion(2L);
        when(mockMapper.load(any())).thenReturn(app);
        
        dao.deactivateApp("whitelisted-study");
    }    
}
