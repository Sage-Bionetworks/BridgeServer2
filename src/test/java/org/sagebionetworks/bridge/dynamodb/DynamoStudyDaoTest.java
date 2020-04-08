package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.dynamodb.DynamoStudyDao.STUDY_WHITELIST_PROPERTY;
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
import org.sagebionetworks.bridge.models.studies.Study;

public class DynamoStudyDaoTest extends Mockito {

    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedScanList<DynamoStudy> mockScanList;
    
    @Captor
    ArgumentCaptor<Study> studyCaptor;
    
    @InjectMocks
    DynamoStudyDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getPropertyAsList(STUDY_WHITELIST_PROPERTY)).thenReturn(ImmutableList.of("whitelisted-study"));
        dao.setBridgeConfig(config);
    }
    
    @Test
    public void doesIdentifierExistSucceeds() {
        DynamoStudy saved = new DynamoStudy();
        when(mockMapper.load(any())).thenReturn(saved);
        
        boolean returned = dao.doesIdentifierExist(API_APP_ID);
        assertTrue(returned);
        
        verify(mockMapper).load(studyCaptor.capture());
        
        Study study = studyCaptor.getValue();
        assertEquals(study.getIdentifier(), API_APP_ID);
    }
    
    @Test
    public void doesIdentifierExistFails() {
        boolean returned = dao.doesIdentifierExist(API_APP_ID);
        assertFalse(returned);
    }
    
    @Test
    public void getStudy() {
        DynamoStudy saved = new DynamoStudy();
        doReturn(saved).when(mockMapper).load(any());
        
        Study result = dao.getStudy(API_APP_ID);
        assertSame(result, saved);
        
        verify(mockMapper).load(studyCaptor.capture());
        Study key = studyCaptor.getValue();
        assertEquals(key.getIdentifier(), API_APP_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getStudyNotFound() {
        dao.getStudy(API_APP_ID);   
    }
    
    @Test
    public void getStudies() {
        List<Study> saved = ImmutableList.of(Study.create(), Study.create());
        when(mockScanList.toArray()).thenReturn(saved.toArray());
        when(mockMapper.scan(eq(DynamoStudy.class), any(DynamoDBScanExpression.class))).thenReturn(mockScanList);
        
        List<Study> result = dao.getStudies();
        assertEquals(result.size(), 2);
        assertEquals(result, saved);

        verify(mockMapper).scan(eq(DynamoStudy.class), any(DynamoDBScanExpression.class));
    }
    
    @Test
    public void createStudy() {
        Study study = Study.create();
        
        dao.createStudy(study);
        
        verify(mockMapper).save(study);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void createStudyFailsIfVersionPresent() {
        Study study = Study.create();
        study.setVersion(2L);
        
        dao.createStudy(study);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createStudyConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        Study study = Study.create();
        study.setIdentifier(API_APP_ID);
        
        dao.createStudy(study);
    }
    
    @Test
    public void updateStudy() {
        Study study = Study.create();
        study.setVersion(2L);
        
        dao.updateStudy(study);
        
        verify(mockMapper).save(study);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void updateStudyWithoutVersionFails() {
        Study study = Study.create();
        
        dao.updateStudy(study);
    }
    
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void updateStudyConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        
        Study study = Study.create();
        study.setVersion(2L);

        dao.updateStudy(study);
    }
    
    @Test
    public void deleteStudy() {
        Study study = Study.create();
        study.setIdentifier(API_APP_ID);
        
        dao.deleteStudy(study);
        
        verify(mockMapper).delete(study);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyRespectsWhitelist() {
        Study study = Study.create();
        study.setIdentifier("whitelisted-study");
        
        dao.deleteStudy(study);
    }
    
    @Test
    public void deactivateStudy() {
        Study study = Study.create();
        study.setActive(true);
        study.setVersion(2L);
        when(mockMapper.load(any())).thenReturn(study);
        
        dao.deactivateStudy(API_APP_ID);
        
        verify(mockMapper).save(studyCaptor.capture());
        assertFalse(studyCaptor.getValue().isActive());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deactivateStudyRespectsWhitelist() {
        Study study = Study.create();
        study.setActive(true);
        study.setVersion(2L);
        when(mockMapper.load(any())).thenReturn(study);
        
        dao.deactivateStudy("whitelisted-study");
    }    
}
