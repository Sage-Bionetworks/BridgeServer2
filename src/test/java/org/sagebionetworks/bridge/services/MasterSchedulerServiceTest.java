package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.MasterSchedulerConfigDao;
import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.TimestampHolder;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

public class MasterSchedulerServiceTest {
    private static final long TIME_STAMP = DateTime.parse("2018-03-27T18:30-07:00").getMillis();
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String UPDATED_CRON_SCHEDULE = "updatedCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    private static final long VERSION = 1L;
    
    @Spy
    private MasterSchedulerService schedulerService;
    
    @Mock
    private MasterSchedulerConfigDao configDao;
    
    @Mock
    private MasterSchedulerStatusDao statusDao;
    
    @Captor
    private ArgumentCaptor<MasterSchedulerConfig> configCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        schedulerService.setSchedulerConfigDao(configDao);
        schedulerService.setSchedulerStatusDao(statusDao);
    }
    
    @Test
    public void testGetAllSchedulerConfigs() {
        List<MasterSchedulerConfig> mockConfigs = ImmutableList.of(MasterSchedulerConfig.create(), MasterSchedulerConfig.create());
        when(configDao.getAllSchedulerConfig()).thenReturn(mockConfigs);
        
        List<MasterSchedulerConfig> configs = schedulerService.getAllSchedulerConfigs();
        assertEquals(configs.size(), 2);
        
        verify(configDao).getAllSchedulerConfig();
    }
    
    @Test
    public void testCreateSchedulerConfig() {
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        
        when(configDao.createSchedulerConfig(configCaptor.capture())).thenReturn(mockConfig);
        
        schedulerService.createSchedulerConfig(mockConfig);
        MasterSchedulerConfig config = configCaptor.getValue();
        
        assertEquals(config.getScheduleId(), SCHEDULE_ID);
        assertEquals(config.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(config.getRequestTemplate().get("a").booleanValue());
        assertEquals(config.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(config.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(config.getVersion(), new Long(VERSION));
        assertSame(mockConfig, config);
        
        verify(configDao).createSchedulerConfig(configCaptor.capture());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void testCreateSchedulerConfigValidates() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        mockConfig.setScheduleId(null);
        
        schedulerService.createSchedulerConfig(mockConfig);
        verify(configDao, never()).createSchedulerConfig(any());
    }
    
    @Test
    public void testGetSchedulerConfig() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        
        when(configDao.getSchedulerConfig(SCHEDULE_ID)).thenReturn(mockConfig);
        
        MasterSchedulerConfig config = schedulerService.getSchedulerConfig(SCHEDULE_ID);
        
        assertEquals(config.getScheduleId(), SCHEDULE_ID);
        assertEquals(config.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(config.getRequestTemplate().get("a").booleanValue());
        assertEquals(config.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(config.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(config.getVersion(), new Long(VERSION));
        assertSame(mockConfig, config);
        verify(configDao).getSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testGetSchedulerConfigThatDoesNotExist() {
        when(configDao.getSchedulerConfig(SCHEDULE_ID)).thenReturn(any());
        schedulerService.getSchedulerConfig(SCHEDULE_ID);
        
        verify(configDao).getSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test
    public void testUpdateSchedulerConfig() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        when(configDao.getSchedulerConfig(SCHEDULE_ID)).thenReturn(mockConfig);
        
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string2");
        
        MasterSchedulerConfig updatedConfig = MasterSchedulerConfig.create();
        updatedConfig.setScheduleId(SCHEDULE_ID);
        updatedConfig.setCronSchedule(UPDATED_CRON_SCHEDULE);
        updatedConfig.setRequestTemplate(objNode);
        updatedConfig.setSqsQueueUrl(SQS_QUEUE_URL);
        updatedConfig.setVersion(2L);
        when(configDao.updateSchedulerConfig(configCaptor.capture())).thenReturn(updatedConfig);
        
        schedulerService.updateSchedulerConfig(SCHEDULE_ID, updatedConfig);
        
        MasterSchedulerConfig config = configCaptor.getValue();
        assertEquals(config.getScheduleId(), SCHEDULE_ID);
        assertEquals(config.getCronSchedule(), UPDATED_CRON_SCHEDULE);
        assertTrue(config.getRequestTemplate().get("a").booleanValue());
        assertEquals(config.getRequestTemplate().get("b").textValue(), "string2");
        assertEquals(config.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(config.getVersion(), new Long(2));
        
        verify(configDao).getSchedulerConfig(SCHEDULE_ID);
        verify(configDao).updateSchedulerConfig(configCaptor.capture());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void testUpdateSchedulerConfigValidates() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        mockConfig.setCronSchedule(null);
        
        schedulerService.updateSchedulerConfig(SCHEDULE_ID, mockConfig);
        verify(configDao, never()).updateSchedulerConfig(any());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testUpdateSchedulerConfigThatDoesNotExist() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        
        when(configDao.getSchedulerConfig(anyString())).thenReturn(any());
        schedulerService.updateSchedulerConfig(anyString(), mockConfig);
        
        verify(configDao, never()).updateSchedulerConfig(any());
    }
    
    @Test
    public void testDeleteSchedulerConfig() {
        schedulerService.deleteSchedulerConfig(SCHEDULE_ID);
        
        verify(configDao).deleteSchedulerConfig(SCHEDULE_ID);
        verifyNoMoreInteractions(configDao);
    }
    
    @Test
    public void testGetSchedulerStatus() {
        TimestampHolder mockTimestamp = new TimestampHolder(TIME_STAMP);
        when(statusDao.getLastProcessedTime()).thenReturn(mockTimestamp);
        
        TimestampHolder timestamp = schedulerService.getSchedulerStatus();
        
        assertEquals(timestamp.getTimestamp(), new Long(TIME_STAMP));
        assertSame(mockTimestamp, timestamp);
        verify(statusDao).getLastProcessedTime();
    }
}
