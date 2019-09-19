package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DynamoMasterSchedulerConfigDaoTest extends Mockito {
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedScanList<DynamoMasterSchedulerConfig> mockScanList;
    
    @Captor
    private ArgumentCaptor<DynamoMasterSchedulerConfig> configCaptor;
    
    @Captor
    ArgumentCaptor<DynamoMasterSchedulerConfig> scanCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBSaveExpression> saveExpressionCaptor;
    
    @InjectMocks
    private DynamoMasterSchedulerConfigDao dao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        dao = new DynamoMasterSchedulerConfigDao();
        dao.setMapper(mockMapper);
    }
    
    @Test
    public void testGetAllSchedulerConfig() {
        List<DynamoMasterSchedulerConfig> configList = ImmutableList.of(
                new DynamoMasterSchedulerConfig(), new DynamoMasterSchedulerConfig());
        
        when(mockScanList.toArray()).thenReturn(configList.toArray());
        when(mockMapper.scan(eq(DynamoMasterSchedulerConfig.class), any(DynamoDBScanExpression.class))).thenReturn(mockScanList);
        
        List<MasterSchedulerConfig> result = dao.getAllSchedulerConfig();
        assertEquals(result.size(), 2);
        assertEquals(result, configList);

        verify(mockMapper).scan(eq(DynamoMasterSchedulerConfig.class), any(DynamoDBScanExpression.class));
    }
    
    @Test
    public void testGetSchedulerConfig() {
        DynamoMasterSchedulerConfig config = new DynamoMasterSchedulerConfig();
        config.setScheduleId(SCHEDULE_ID);
        when(mockMapper.load(any())).thenReturn(config);
        
        MasterSchedulerConfig result = dao.getSchedulerConfig(config.getScheduleId());
        assertSame(result, config);
        
        verify(mockMapper).load(configCaptor.capture());
        DynamoMasterSchedulerConfig key = configCaptor.getValue();
        assertEquals(key.getScheduleId(), SCHEDULE_ID);
    }
    
    @Test
    public void testCreateSchedulerConfig() {
        MasterSchedulerConfig config = TestUtils.getMasterSchedulerConfig();
        
        MasterSchedulerConfig result = dao.createSchedulerConfig(config);
        
        // verify mapper call
        verify(mockMapper).save(configCaptor.capture(), any(DynamoDBSaveExpression.class));
        
        DynamoMasterSchedulerConfig mapperConfig = configCaptor.getValue();
        assertEquals(mapperConfig.getScheduleId(), SCHEDULE_ID);
        assertEquals(mapperConfig.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(mapperConfig.getRequestTemplate().get("a").booleanValue());
        assertEquals(mapperConfig.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(mapperConfig.getSqsQueueUrl(), SQS_QUEUE_URL);
        
        // config returned by dao is the same one that was sent to the mapper
        assertSame(result, mapperConfig);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateInvalidConfig() {
        MasterSchedulerConfig updatedConfig = MasterSchedulerConfig.create();
        
        dao.createSchedulerConfig(updatedConfig);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void testCreateConfigConditionalCheckFailedThrows() {
        doThrow(new ConditionalCheckFailedException("message"))
            .when(mockMapper).save(any(), any(DynamoDBSaveExpression.class));
        
        MasterSchedulerConfig config = TestUtils.getMasterSchedulerConfig();
        
        dao.createSchedulerConfig(config);
    }
    
    @Test
    public void testUpdateSchedulerConfig() {
        MasterSchedulerConfig updatedConfig = TestUtils.getMasterSchedulerConfig();
        
        MasterSchedulerConfig result = dao.updateSchedulerConfig(updatedConfig);
        
        // verify mapper call
        verify(mockMapper).save(configCaptor.capture(), saveExpressionCaptor.capture());
        
        DynamoMasterSchedulerConfig mapperConfig = configCaptor.getValue();
        assertEquals(mapperConfig.getScheduleId(), SCHEDULE_ID);
        assertEquals(mapperConfig.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(mapperConfig.getRequestTemplate().get("a").booleanValue());
        assertEquals(mapperConfig.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(mapperConfig.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(mapperConfig.getVersion(), new Long(1));
        
        DynamoDBSaveExpression expr = saveExpressionCaptor.getValue();
        Map<String,ExpectedAttributeValue> map = expr.getExpected();
        assertEquals(map.get("scheduleId").getValue().getS(), mapperConfig.getScheduleId());
        
        // schema returned by dao is the same one that was sent to the mapper
        assertSame(result, mapperConfig);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUpdateInvalidConfig() {
        MasterSchedulerConfig updatedConfig = MasterSchedulerConfig.create();
        
        dao.updateSchedulerConfig(updatedConfig);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testUpdateConfigConditionalCheckFailedThrows() {
        doThrow(new ConditionalCheckFailedException("message"))
            .when(mockMapper).save(any(), any(DynamoDBSaveExpression.class));
        
        MasterSchedulerConfig updatedConfig = TestUtils.getMasterSchedulerConfig();
        
        dao.updateSchedulerConfig(updatedConfig);
    }
    
    @Test
    public void testDeleteSchedulerConfig() {
        MasterSchedulerConfig config = MasterSchedulerConfig.create();
        config.setScheduleId(SCHEDULE_ID);
        when(mockMapper.load(any())).thenReturn(config);
        
        dao.deleteSchedulerConfig(config.getScheduleId());
        
        verify(mockMapper).load(configCaptor.capture());
        DynamoMasterSchedulerConfig key = configCaptor.getValue();
        assertEquals(key.getScheduleId(), SCHEDULE_ID);
        
        verify(mockMapper).delete(config);
    }
    
    @Test
    public void testDeleteInvalidConfig() {
        mockMapper.load(any());
        
        dao.deleteSchedulerConfig(SCHEDULE_ID);
        verify(mockMapper, never()).delete(any());
    }
    
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void testDeleteConfigConditionalCheckFailedThrows() {
        doThrow(new ConditionalCheckFailedException("message")).when(mockMapper).delete(any());
        
        MasterSchedulerConfig config = MasterSchedulerConfig.create();
        when(mockMapper.load(any())).thenReturn(config);
        
        dao.deleteSchedulerConfig(SCHEDULE_ID);
    }
}
