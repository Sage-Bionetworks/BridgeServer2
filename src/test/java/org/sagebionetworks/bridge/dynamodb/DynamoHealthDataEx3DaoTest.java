package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

@SuppressWarnings({ "ConstantConditions", "unchecked" })
public class DynamoHealthDataEx3DaoTest {
    private static final long CREATED_ON_START = 1595560908000L;
    private static final long CREATED_ON_END = 1595647308000L;
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";

    @Mock
    private DynamoDBMapper mockMapper;

    @InjectMocks
    @Spy
    private DynamoHealthDataEx3Dao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateRecord_NewRecord() {
        HealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();

        HealthDataRecordEx3 returnedRecord = dao.createOrUpdateRecord(record);
        assertSame(returnedRecord, record);
        assertNotNull(returnedRecord.getId());

        verify(mockMapper).save(same(record));
    }

    @Test
    public void createOrUpdateRecord_UpdateRecord() {
        HealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        record.setId(RECORD_ID);

        HealthDataRecordEx3 returnedRecord = dao.createOrUpdateRecord(record);
        assertSame(returnedRecord, record);
        assertEquals(record.getId(), RECORD_ID);

        verify(mockMapper).save(same(record));
    }

    @Test
    public void deleteRecordsForHealthCode() {
        // Mock dependencies.
        List<DynamoHealthDataRecordEx3> recordList = ImmutableList.of(new DynamoHealthDataRecordEx3());
        doReturn(recordList).when(dao).queryHelper(any());

        // Execute.
        dao.deleteRecordsForHealthCode(TestConstants.HEALTH_CODE);

        // Validate.
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataRecordEx3>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = queryCaptor.getValue();
        assertFalse(query.isConsistentRead());
        assertEquals(query.getIndexName(), DynamoHealthDataRecordEx3.HEALTHCODE_CREATEDON_INDEX);
        assertEquals(query.getHashKeyValues().getHealthCode(), TestConstants.HEALTH_CODE);
        assertNull(query.getRangeKeyConditions());

        verify(mockMapper).batchDelete(same(recordList));
    }

    @Test
    public void deleteRecordsForHealthCode_NoRecords() {
        // Mock dependencies.
        doReturn(ImmutableList.of()).when(dao).queryHelper(any());

        // Execute.
        dao.deleteRecordsForHealthCode(TestConstants.HEALTH_CODE);

        // Query is already validated. Just validate that we don't call batchDelete().
        verify(mockMapper, never()).batchDelete(anyList());
    }

    @Test
    public void getRecord() {
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        when(mockMapper.load(DynamoHealthDataRecordEx3.class, RECORD_ID)).thenReturn(record);

        Optional<HealthDataRecordEx3> result = dao.getRecord(RECORD_ID);
        assertSame(result.get(), record);
    }

    @Test
    public void getRecord_NoRecord() {
        when(mockMapper.load(DynamoHealthDataRecordEx3.class, RECORD_ID)).thenReturn(null);

        Optional<HealthDataRecordEx3> result = dao.getRecord(RECORD_ID);
        assertFalse(result.isPresent());
    }

    @Test
    public void getRecordsForHealthCode() {
        // Mock dependencies.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        doReturn(ImmutableList.of(record)).when(dao).queryHelper(any());

        // Execute.
        List<HealthDataRecordEx3> resultList = dao.getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START,
                CREATED_ON_END);
        assertEquals(resultList.size(), 1);
        assertSame(resultList.get(0), record);

        // Validate.
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataRecordEx3>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = queryCaptor.getValue();
        assertFalse(query.isConsistentRead());
        assertEquals(query.getIndexName(), DynamoHealthDataRecordEx3.HEALTHCODE_CREATEDON_INDEX);
        assertEquals(query.getHashKeyValues().getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(query.getRangeKeyConditions().size(), 1);

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("createdOn");
        assertEquals(rangeKeyCondition.getComparisonOperator(), ComparisonOperator.BETWEEN.toString());
        assertEquals(rangeKeyCondition.getAttributeValueList().size(), 2);
        assertEquals(rangeKeyCondition.getAttributeValueList().get(0).getN(), String.valueOf(CREATED_ON_START));
        assertEquals(rangeKeyCondition.getAttributeValueList().get(1).getN(), String.valueOf(CREATED_ON_END));
    }

    @Test
    public void getRecordsForApp() {
        // Mock dependencies.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        doReturn(ImmutableList.of(record)).when(dao).queryHelper(any());

        // Execute.
        List<HealthDataRecordEx3> resultList = dao.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START,
                CREATED_ON_END);
        assertEquals(resultList.size(), 1);
        assertSame(resultList.get(0), record);

        // Validate.
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataRecordEx3>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = queryCaptor.getValue();
        assertFalse(query.isConsistentRead());
        assertEquals(query.getIndexName(), DynamoHealthDataRecordEx3.APPID_CREATEDON_INDEX);
        assertEquals(query.getHashKeyValues().getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(query.getRangeKeyConditions().size(), 1);

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("createdOn");
        assertEquals(rangeKeyCondition.getComparisonOperator(), ComparisonOperator.BETWEEN.toString());
        assertEquals(rangeKeyCondition.getAttributeValueList().size(), 2);
        assertEquals(rangeKeyCondition.getAttributeValueList().get(0).getN(), String.valueOf(CREATED_ON_START));
        assertEquals(rangeKeyCondition.getAttributeValueList().get(1).getN(), String.valueOf(CREATED_ON_END));
    }

    @Test
    public void getRecordsForAppAndStudy() {
        // Mock dependencies.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        doReturn(ImmutableList.of(record)).when(dao).queryHelper(any());

        // Execute.
        List<HealthDataRecordEx3> resultList = dao.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,
                CREATED_ON_START, CREATED_ON_END);
        assertEquals(resultList.size(), 1);
        assertSame(resultList.get(0), record);

        // Validate.
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataRecordEx3>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = queryCaptor.getValue();
        assertFalse(query.isConsistentRead());
        assertEquals(query.getIndexName(), DynamoHealthDataRecordEx3.APPSTUDYKEY_CREATEDON_INDEX);
        assertEquals(query.getHashKeyValues().getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(query.getHashKeyValues().getStudyId(), STUDY_ID);
        assertEquals(query.getRangeKeyConditions().size(), 1);

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("createdOn");
        assertEquals(rangeKeyCondition.getComparisonOperator(), ComparisonOperator.BETWEEN.toString());
        assertEquals(rangeKeyCondition.getAttributeValueList().size(), 2);
        assertEquals(rangeKeyCondition.getAttributeValueList().get(0).getN(), String.valueOf(CREATED_ON_START));
        assertEquals(rangeKeyCondition.getAttributeValueList().get(1).getN(), String.valueOf(CREATED_ON_END));
    }
}
