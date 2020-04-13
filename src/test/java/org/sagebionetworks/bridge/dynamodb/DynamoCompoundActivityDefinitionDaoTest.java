package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;

public class DynamoCompoundActivityDefinitionDaoTest extends Mockito {

    static String TASK_ID = "oneTaskId";
    
    static DynamoCompoundActivityDefinition KEYS = new DynamoCompoundActivityDefinition();
    static {
        KEYS.setStudyId(TEST_STUDY_IDENTIFIER);
        KEYS.setTaskId(TASK_ID);
    }
    
    static DynamoCompoundActivityDefinition COMPOUND_ACTIVITY_DEF = new DynamoCompoundActivityDefinition();
    static {
        COMPOUND_ACTIVITY_DEF.setStudyId(TEST_STUDY_IDENTIFIER);
        COMPOUND_ACTIVITY_DEF.setTaskId(TASK_ID);
    }
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedQueryList<DynamoCompoundActivityDefinition> mockQueryList;
    
    @Mock
    List<DynamoDBMapper.FailedBatch> mockFailedBatchList; 
    
    @Captor
    ArgumentCaptor<DynamoCompoundActivityDefinition> defCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoCompoundActivityDefinition>> queryCaptor;
    
    @InjectMocks
    DynamoCompoundActivityDefinitionDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createCompoundActivityDefinition() {
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setStudyId(TEST_STUDY_IDENTIFIER);
        def.setTaskId(TASK_ID);
        def.setVersion(1L);
        
        CompoundActivityDefinition result = dao.createCompoundActivityDefinition(def);
        assertSame(result, def);
        
        verify(mockMapper).save(defCaptor.capture());
        assertNull(defCaptor.getValue().getVersion());
    }
    
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void createCompoundActivityDefinitionConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        dao.createCompoundActivityDefinition(COMPOUND_ACTIVITY_DEF);
    }

    @Test
    public void deleteCompoundActivityDefinition() {
        when(mockMapper.load(any())).thenReturn(COMPOUND_ACTIVITY_DEF);
        
        dao.deleteCompoundActivityDefinition(TEST_STUDY_IDENTIFIER, TASK_ID);
        
        verify(mockMapper).delete(COMPOUND_ACTIVITY_DEF);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteCompoundActivityDefinitionEntityNotFoundException() {
        when(mockMapper.load(any())).thenReturn(COMPOUND_ACTIVITY_DEF);
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).delete(any());
        
        dao.deleteCompoundActivityDefinition(TEST_STUDY_IDENTIFIER, TASK_ID);
    }

    @Test
    public void deleteAllCompoundActivityDefinitionsInStudy() {
        when(mockMapper.query(eq(DynamoCompoundActivityDefinition.class),
                queryCaptor.capture())).thenReturn(mockQueryList);
        
        dao.deleteAllCompoundActivityDefinitionsInStudy(TEST_STUDY_IDENTIFIER);
        
        verify(mockMapper).batchDelete(mockQueryList);
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void deleteAllCompoundActivityDefinitionsInStudyWithErrors() {
        when(mockMapper.query(eq(DynamoCompoundActivityDefinition.class),
                queryCaptor.capture())).thenReturn(mockQueryList);
        
        DynamoDBMapper.FailedBatch failure1 = new DynamoDBMapper.FailedBatch();
        failure1.setException(new IllegalArgumentException("First errror message"));
        failure1.setUnprocessedItems(ImmutableMap.of());
        
        DynamoDBMapper.FailedBatch failure2 = new DynamoDBMapper.FailedBatch();
        failure2.setException(new UnsupportedOperationException("Second errror message"));
        failure2.setUnprocessedItems(ImmutableMap.of());
        
        when(mockFailedBatchList.iterator()).thenReturn(ImmutableList.of(failure1, failure2).iterator());
        when(mockMapper.batchDelete(mockQueryList)).thenReturn(mockFailedBatchList);
        
        dao.deleteAllCompoundActivityDefinitionsInStudy(TEST_STUDY_IDENTIFIER);
        
        verify(mockMapper).batchDelete(mockQueryList);
    }
    
    @Test
    public void getAllCompoundActivityDefinitionsInStudy() {
        List<DynamoCompoundActivityDefinition> defList = ImmutableList.of(
                new DynamoCompoundActivityDefinition(),
                new DynamoCompoundActivityDefinition());
        when(mockQueryList.toArray()).thenReturn(defList.toArray());
        when(mockMapper.query(eq(DynamoCompoundActivityDefinition.class), any())).thenReturn(mockQueryList);
        
        List<CompoundActivityDefinition> results = dao.getAllCompoundActivityDefinitionsInStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(results.size(), 2);
        
        verify(mockMapper).query(any(), queryCaptor.capture());
        assertEquals(queryCaptor.getValue().getHashKeyValues().getStudyId(), TEST_STUDY_IDENTIFIER);
    }

    @Test
    public void getCompoundActivityDefinition() {
        when(mockMapper.load(any())).thenReturn(COMPOUND_ACTIVITY_DEF);
        
        CompoundActivityDefinition result = dao.getCompoundActivityDefinition(TEST_STUDY_IDENTIFIER, TASK_ID);
        assertSame(result, COMPOUND_ACTIVITY_DEF);
        
        verify(mockMapper).load(defCaptor.capture());
        CompoundActivityDefinition def = defCaptor.getValue();
        assertEquals(def.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(def.getTaskId(), TASK_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getCompoundActivityDefinitionEntityNotFoundException() {
        dao.getCompoundActivityDefinition(TEST_STUDY_IDENTIFIER, TASK_ID);
    }
    
    @Test
    public void updateCompoundActivityDefinition() {
        when(mockMapper.load(any())).thenReturn(COMPOUND_ACTIVITY_DEF);
        
        CompoundActivityDefinition result = dao.updateCompoundActivityDefinition(COMPOUND_ACTIVITY_DEF);
        assertSame(result, COMPOUND_ACTIVITY_DEF);
        
        verify(mockMapper).save(COMPOUND_ACTIVITY_DEF);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateCompoundActivityDefinitionEntityNotFoundException() {
        dao.updateCompoundActivityDefinition(COMPOUND_ACTIVITY_DEF);
    }
    
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void updateCompoundActivityDefinitionConcurrentModificationException() {
        when(mockMapper.load(any())).thenReturn(COMPOUND_ACTIVITY_DEF);
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(COMPOUND_ACTIVITY_DEF);
        
        dao.updateCompoundActivityDefinition(COMPOUND_ACTIVITY_DEF);
    }
}
