package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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
import org.sagebionetworks.bridge.models.DateTimeHolder;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

public class MasterSchedulerServiceTest {
    private static final DateTime LAST_PROCESSED_TIME = DateTime.parse("2017-06-03T20:50:21.650-08:00");
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String UPDATED_SCHEDULE_ID = "updated-schedule-id";
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
    
    @Test
    public void testCreateSchedulerConfigValidates() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        mockConfig.setScheduleId(null);
        
        try {
            schedulerService.createSchedulerConfig(mockConfig);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }
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
        when(configDao.getSchedulerConfig(SCHEDULE_ID)).thenReturn(null);
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
        updatedConfig.setScheduleId(UPDATED_SCHEDULE_ID);
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
        
        when(configDao.getSchedulerConfig(anyString())).thenReturn(null);
        schedulerService.updateSchedulerConfig(SCHEDULE_ID, mockConfig);
        
        verify(configDao, never()).updateSchedulerConfig(any());
    }
    
    @Test
    public void testDeleteSchedulerConfig() {
        MasterSchedulerConfig mockConfig = TestUtils.getMasterSchedulerConfig();
        
        when(configDao.getSchedulerConfig(SCHEDULE_ID)).thenReturn(mockConfig);
        
        schedulerService.deleteSchedulerConfig(SCHEDULE_ID);
        
        verify(configDao).getSchedulerConfig(SCHEDULE_ID);
        verify(configDao).deleteSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test
    public void testDeleteSchedulerConfigThatDoesNotExist() {
        when(configDao.getSchedulerConfig(anyString())).thenReturn(null);
        
        try {
            schedulerService.deleteSchedulerConfig(SCHEDULE_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }
        verify(configDao, never()).deleteSchedulerConfig(anyString());
    }
    
    @Test
    public void testGetSchedulerStatus() {
        DateTimeHolder timestamp = new DateTimeHolder(LAST_PROCESSED_TIME);
        when(statusDao.getLastProcessedTime()).thenReturn(timestamp);
        
        DateTimeHolder result = schedulerService.getSchedulerStatus();
        
        assertEquals(timestamp.getDateTime(), LAST_PROCESSED_TIME);
        assertSame(result, timestamp);
        verify(statusDao).getLastProcessedTime();
    }
}
