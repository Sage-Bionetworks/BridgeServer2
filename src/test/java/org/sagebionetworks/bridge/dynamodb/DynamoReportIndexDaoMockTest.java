package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableList;

public class DynamoReportIndexDaoMockTest {

    DynamoReportIndexDao dao;
    
    @Mock
    private DynamoDBMapper mapper;
    
    @Mock
    private PaginatedQueryList<DynamoReportIndex> results;
    
    @Captor
    private ArgumentCaptor<DynamoReportIndex> loadIndexCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoReportIndex> saveIndexCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBSaveExpression> saveExpressionCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoReportIndex>> queryCaptor;
    
    private ReportDataKey KEY = new ReportDataKey.Builder()
            .withIdentifier("report-name").withStudyIdentifier(TEST_STUDY)
            .withReportType(ReportType.STUDY).build();

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new DynamoReportIndexDao();
        dao.setReportIndexMapper(mapper);
    }
    
    @Test
    public void getIndex() {
        dao.getIndex(KEY);
        
        verify(mapper).load(loadIndexCaptor.capture());
        
        DynamoReportIndex index = loadIndexCaptor.getValue();
        assertEquals(index.getKey(), KEY.getIndexKeyString());
        assertEquals(index.getIdentifier(), KEY.getIdentifier());
    }

    @Test
    public void addIndex() {
        dao.addIndex(KEY, TestConstants.USER_SUBSTUDY_IDS);
        
        verify(mapper).load(loadIndexCaptor.capture());
        verify(mapper).save(saveIndexCaptor.capture(), eq(DynamoReportIndexDao.DOES_NOT_EXIST_EXPRESSION));
        
        DynamoReportIndex lookupKey = loadIndexCaptor.getValue();
        assertEquals(lookupKey.getKey(), KEY.getIndexKeyString());
        assertEquals(lookupKey.getIdentifier(), KEY.getIdentifier());
        assertEquals(lookupKey.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        
        DynamoReportIndex savedIndex = saveIndexCaptor.getValue();
        assertEquals(savedIndex.getKey(), KEY.getIndexKeyString());
        assertEquals(savedIndex.getIdentifier(), KEY.getIdentifier());
        assertEquals(savedIndex.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
    }
    
    @Test
    public void addIndexDoesNothingIfIndexExists() {
        when(mapper.load(any())).thenReturn(ReportIndex.create());
        
        dao.addIndex(KEY, TestConstants.USER_SUBSTUDY_IDS);
        
        verify(mapper, never()).save(any(), eq(DynamoReportIndexDao.DOES_NOT_EXIST_EXPRESSION));
    }
    
    @Test
    public void addIndexConditionalCheckFailedDoesNotThrow() {
        doThrow(new ConditionalCheckFailedException("message"))
            .when(mapper).save(any(), eq(DynamoReportIndexDao.DOES_NOT_EXIST_EXPRESSION));
        
        dao.addIndex(KEY, TestConstants.USER_SUBSTUDY_IDS);
    }

    @Test
    public void removeIndex() {
        ReportIndex index = ReportIndex.create();
        when(mapper.load(any())).thenReturn(index);
        
        dao.removeIndex(KEY);
        
        verify(mapper).load(loadIndexCaptor.capture());
        verify(mapper).delete(index);
        
        DynamoReportIndex lookupKey = loadIndexCaptor.getValue();
        assertEquals(lookupKey.getKey(), KEY.getIndexKeyString());
        assertEquals(lookupKey.getIdentifier(), KEY.getIdentifier());
    }
    
    @Test
    public void removeIndexMissingDoesNothing() {
        dao.removeIndex(KEY);
        
        verify(mapper, never()).delete(any());
    }

    @Test
    public void updateIndex() {
        ReportIndex updatedIndex = ReportIndex.create();
        updatedIndex.setIdentifier("asdf");
        updatedIndex.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        updatedIndex.setPublic(true);
        updatedIndex.setKey(KEY.getIndexKeyString());
        
        // This enforces that the update is being done on an existing key/identifier pair.
        // It does not enforce substudy permissions... these are handled in the service.
        // Adding substudies here would always be allowed if it hadn't been blocked by the 
        // service.
        dao.updateIndex(updatedIndex);
        
        verify(mapper).save(saveIndexCaptor.capture(), saveExpressionCaptor.capture());
        
        DynamoReportIndex index = saveIndexCaptor.getValue();
        assertEquals(index.getKey(), "api:STUDY");
        assertEquals(index.getIdentifier(), updatedIndex.getIdentifier());
        assertEquals(index.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        assertTrue(index.isPublic());
        
        DynamoDBSaveExpression expr = saveExpressionCaptor.getValue();
        Map<String,ExpectedAttributeValue> map = expr.getExpected();
        assertEquals(map.get("key").getValue().getS(), index.getKey());
        assertEquals(map.get("identifier").getValue().getS(), index.getIdentifier());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateIndexConditionalCheckFailedThrows() {
        doThrow(new ConditionalCheckFailedException("message"))
            .when(mapper).save(any(), any(DynamoDBSaveExpression.class));
        
        ReportIndex updatedIndex = ReportIndex.create();
        
        dao.updateIndex(updatedIndex);
    }
    
    @Test
    public void getIndices() {
        List<DynamoReportIndex> indexList = ImmutableList.of(new DynamoReportIndex());
        when(results.size()).thenReturn(indexList.size());
        
        when(mapper.query(eq(DynamoReportIndex.class), any())).thenReturn(results);
        
        ReportTypeResourceList<? extends ReportIndex> indices = dao.getIndices(
                TestConstants.TEST_STUDY, ReportType.PARTICIPANT);
        
        assertEquals(indices.getItems().size(), 1);
        assertEquals(indices.getRequestParams().get("reportType"), ReportType.PARTICIPANT);
        
        verify(mapper).query(eq(DynamoReportIndex.class), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoReportIndex> query = queryCaptor.getValue();
        DynamoReportIndex hashKey = query.getHashKeyValues();
        
        assertEquals(hashKey.getKey(), "api:PARTICIPANT");
    }    
}
