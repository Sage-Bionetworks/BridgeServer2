package org.sagebionetworks.bridge.spring.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.TimestampHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.MasterSchedulerService;
import org.sagebionetworks.bridge.services.StudyService;

public class MasterSchedulerControllerTest extends Mockito {
    private static final long TIME_STAMP = DateTime.parse("2018-03-27T18:30-07:00").getMillis();
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String UPDATED_CRON_SCHEDULE = "updatedCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    private static final String UPDATED_SQS_QUEUE_URL = "updatedSysQueueUrl";
    private static final long VERSION = 1L;
    
    @Mock
    MasterSchedulerService mockSchedulerService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<MasterSchedulerConfig> configCaptor;
    
    @Spy
    @InjectMocks
    MasterSchedulerController controller = new MasterSchedulerController();
    
    MasterSchedulerConfig mockConfig;
    
    UserSession session; 
    
    Study study;
    
    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
        
        mockConfig = TestUtils.getMasterSchedulerConfig();
        
        study = new DynamoStudy();
        study.setUserProfileAttributes(Sets.newHashSet("foo", "baz"));
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
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
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        List<MasterSchedulerConfig> configs = ImmutableList.of(mockConfig, mockConfig, mockConfig);
        
        // Mock service.
        when(mockSchedulerService.getAllSchedulerConfigs()).thenReturn(configs);
        
        // Execute and validate.
        ResourceList<MasterSchedulerConfig> result = controller.getAllSchedulerConfigs();
        
        assertEquals(result.getItems().size(), 3);
        verify(mockSchedulerService).getAllSchedulerConfigs();
    }
    
    @Test
    public void createSchedulerConfig() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        mockRequestBody(mockRequest, mockConfig);
        
        // Mock service.
        when(mockSchedulerService.createSchedulerConfig(any())).thenReturn(mockConfig);
        
        // Execute and validate.
        MasterSchedulerConfig result = controller.createSchedulerConfig();
        
        assertEquals(result.getScheduleId(), SCHEDULE_ID);

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
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createSchedulerConfigAdminOnly() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        controller.createSchedulerConfig();
        
        verify(mockSchedulerService, never()).createSchedulerConfig(any());
    }
    
    @Test
    public void getSchedulerConfig() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
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
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        MasterSchedulerConfig updatedConfig = MasterSchedulerConfig.create();
        updatedConfig.setScheduleId("test-schedule-id");
        updatedConfig.setCronSchedule(UPDATED_CRON_SCHEDULE);
        updatedConfig.setRequestTemplate(mockConfig.getRequestTemplate());
        updatedConfig.setSqsQueueUrl(UPDATED_SQS_QUEUE_URL);
        updatedConfig.setVersion(2L);
        mockRequestBody(mockRequest, updatedConfig);
        
        // Mock service.
        when(mockSchedulerService.getSchedulerConfig(SCHEDULE_ID)).thenReturn(mockConfig);
        when(mockSchedulerService.updateSchedulerConfig(eq(SCHEDULE_ID), any())).thenReturn(updatedConfig);
        
        // Execute and validate.
        MasterSchedulerConfig result = controller.updateSchedulerConfig(SCHEDULE_ID);
        
        assertEquals(result.getScheduleId(), SCHEDULE_ID);
        
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
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage message = controller.deleteSchedulerConfig(SCHEDULE_ID);
        
        assertEquals(message.getMessage(), "Scheduler config deleted.");
        
        verify(mockSchedulerService).deleteSchedulerConfig(SCHEDULE_ID);
    }
    
    @Test
    public void getSchedulerStatus() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        TimestampHolder mockTimestamp = new TimestampHolder(TIME_STAMP);
        when(mockSchedulerService.getSchedulerStatus()).thenReturn(mockTimestamp);
        
        TimestampHolder result = controller.getSchedulerStatus();
        assertSame(result, mockTimestamp);
    }
}
