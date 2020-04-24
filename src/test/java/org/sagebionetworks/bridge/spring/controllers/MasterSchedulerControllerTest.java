package org.sagebionetworks.bridge.spring.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.DateTimeHolder;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.MasterSchedulerService;
import org.sagebionetworks.bridge.services.StudyService;

public class MasterSchedulerControllerTest extends Mockito {
    private static final DateTime LAST_PROCESSED_TIME = DateTime.parse("2017-06-03T20:50:21.650-08:00");
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String UPDATED_CRON_SCHEDULE = "updatedCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    private static final String UPDATED_SQS_QUEUE_URL = "updatedSysQueueUrl";
    private static final long VERSION = 1L;
    
    @Mock
    private MasterSchedulerService mockSchedulerService;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AccountService mockAccountService;
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Captor
    private ArgumentCaptor<MasterSchedulerConfig> configCaptor;
    
    @Spy
    @InjectMocks
    MasterSchedulerController controller = new MasterSchedulerController();
    
    MasterSchedulerConfig mockConfig;
    
    App app;
    
    UserSession mockSession; 
    
    @BeforeMethod
    private void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);
        mockSession.setAuthenticated(true);
        mockSession.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        
        mockConfig = TestUtils.getMasterSchedulerConfig();
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(MasterSchedulerController.class);
        assertGet(MasterSchedulerController.class, "getAllSchedulerConfigs");
        assertCreate(MasterSchedulerController.class, "createSchedulerConfig");
        assertGet(MasterSchedulerController.class, "getSchedulerConfig");
        assertPost(MasterSchedulerController.class, "updateSchedulerConfig");
        assertDelete(MasterSchedulerController.class, "deleteSchedulerConfig");
        assertGet(MasterSchedulerController.class, "getSchedulerStatus");
    }
    
    @Test
    public void getAllSchedulerConfigs() throws Exception {
        List<MasterSchedulerConfig> configs = ImmutableList.of(mockConfig, mockConfig, mockConfig);
        
        // Mock service.
        when(mockSchedulerService.getAllSchedulerConfigs()).thenReturn(configs);
        
        // Execute and validate.
        ResourceList<MasterSchedulerConfig> result = controller.getAllSchedulerConfigs();
        
        assertEquals(result.getItems().size(), 3);
        assertEquals(result.getItems().get(0).getScheduleId(), SCHEDULE_ID);
        assertEquals(result.getItems().get(1).getCronSchedule(), CRON_SCHEDULE);
        assertEquals(result.getItems().get(2).getSqsQueueUrl(), SQS_QUEUE_URL);
        verify(mockSchedulerService).getAllSchedulerConfigs();
    }
    
    @Test
    public void createSchedulerConfig() throws Exception {
        mockRequestBody(mockRequest, mockConfig);
        
        // Mock service.
        when(mockSchedulerService.createSchedulerConfig(any())).thenReturn(mockConfig);
        
        // Execute and validate.
        MasterSchedulerConfig result = controller.createSchedulerConfig();
        
        assertEquals(result.getScheduleId(), SCHEDULE_ID);
        assertSame(result, mockConfig);
        
        // Verify service.
        verify(mockSchedulerService).createSchedulerConfig(configCaptor.capture());
        
        MasterSchedulerConfig config = configCaptor.getValue();
        assertEquals(config.getScheduleId(), SCHEDULE_ID);
        assertEquals(config.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(config.getRequestTemplate().get("a").booleanValue());
        assertEquals(config.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(config.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(config.getVersion(), new Long(VERSION));
    }
    
    @Test
    public void getSchedulerConfig() throws Exception {
        // Mock service.
        when(mockSchedulerService.getSchedulerConfig(SCHEDULE_ID)).thenReturn(mockConfig);
        
        // Execute and validate.
        MasterSchedulerConfig result = controller.getSchedulerConfig(SCHEDULE_ID);
        assertEquals(result.getScheduleId(), SCHEDULE_ID);
        assertSame(mockConfig, result);
        verify(controller).getSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test
    public void updateSchedulerConfig() throws Exception {
        MasterSchedulerConfig updatedConfig = MasterSchedulerConfig.create();
        updatedConfig.setScheduleId("test-schedule-id");
        updatedConfig.setCronSchedule(UPDATED_CRON_SCHEDULE);
        updatedConfig.setRequestTemplate(mockConfig.getRequestTemplate());
        updatedConfig.setSqsQueueUrl(UPDATED_SQS_QUEUE_URL);
        updatedConfig.setVersion(2L);
        mockRequestBody(mockRequest, updatedConfig);
        
        // Mock service.
        when(mockSchedulerService.updateSchedulerConfig(eq(SCHEDULE_ID), any())).thenReturn(updatedConfig);
        
        // Execute and validate.
        MasterSchedulerConfig result = controller.updateSchedulerConfig(SCHEDULE_ID);
        assertEquals(result.getScheduleId(), SCHEDULE_ID);
        assertSame(result, updatedConfig);
        
        // Verify service.
        verify(mockSchedulerService).updateSchedulerConfig(anyString(), configCaptor.capture());
        
        MasterSchedulerConfig config = configCaptor.getValue();
        assertEquals(config.getScheduleId(), SCHEDULE_ID);
        assertEquals(config.getCronSchedule(), UPDATED_CRON_SCHEDULE);
        assertTrue(config.getRequestTemplate().get("a").booleanValue());
        assertEquals(config.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(config.getSqsQueueUrl(), UPDATED_SQS_QUEUE_URL);
        assertEquals(config.getVersion(), new Long(2));
    }
    
    @Test
    public void deleteSchedulerConfig() throws Exception {
        StatusMessage message = controller.deleteSchedulerConfig(SCHEDULE_ID);
        
        assertEquals(message.getMessage(), "Scheduler config deleted.");
        
        verify(mockSchedulerService).deleteSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test
    public void getSchedulerStatus() throws Exception {
        DateTimeHolder timestamp = new DateTimeHolder(LAST_PROCESSED_TIME);
        when(mockSchedulerService.getSchedulerStatus()).thenReturn(timestamp);
        
        DateTimeHolder result = controller.getSchedulerStatus();
        assertSame(result, timestamp);
    }
}
