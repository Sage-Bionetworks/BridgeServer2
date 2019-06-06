package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Criteria;

public class DynamoCriteriaDaoTest extends Mockito {

    private static final String CRITERIA_KEY = "criteria:key";

    @Mock
    DynamoDBMapper mockMapper;
    
    @Captor
    ArgumentCaptor<DynamoCriteria> criteriaCaptor;
    
    @InjectMocks
    DynamoCriteriaDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createOrUpdateCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setKey(CRITERIA_KEY);
        
        Criteria result = dao.createOrUpdateCriteria(criteria);
        assertSame(result, criteria);
        
        verify(mockMapper).save(criteria);
    }
    
    @Test
    public void getCriteria() {
        Criteria saved = Criteria.create();
        saved.setKey(CRITERIA_KEY);
        when(mockMapper.load(any())).thenReturn(saved);
        
        Criteria result = dao.getCriteria(CRITERIA_KEY);
        assertSame(result, saved);
        
        verify(mockMapper).load(criteriaCaptor.capture());
        DynamoCriteria key = criteriaCaptor.getValue();
        assertEquals(key.getKey(), CRITERIA_KEY);
    }

    @Test
    public void getCriteriaNotFound() {
        assertNull( dao.getCriteria(CRITERIA_KEY) );
    }
    
    @Test
    public void deleteCriteria() {
        Criteria saved = Criteria.create();
        saved.setKey(CRITERIA_KEY);
        when(mockMapper.load(any())).thenReturn(saved);
        
        dao.deleteCriteria(CRITERIA_KEY);
        
        verify(mockMapper).delete(criteriaCaptor.capture());
        DynamoCriteria key = criteriaCaptor.getValue();
        assertEquals(key.getKey(), CRITERIA_KEY);
    }

    @Test
    public void deleteCriteriaNotFound() {
        dao.deleteCriteria(CRITERIA_KEY);
        
        verify(mockMapper, never()).delete(any());
    }
}
