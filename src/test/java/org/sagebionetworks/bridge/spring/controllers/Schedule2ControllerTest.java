package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.RECRUITMENT;
import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.Schedule2Service;
import org.sagebionetworks.bridge.services.StudyService;

public class Schedule2ControllerTest extends Mockito {
    
    @Mock
    Schedule2Service mockService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    Schedule2Controller controller;
    
    @Captor
    ArgumentCaptor<Schedule2> scheduleCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        
        doReturn(session).when(controller).getAdministrativeSession();
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(Schedule2Controller.class);
        assertGet(Schedule2Controller.class, "getSchedule");
        assertPost(Schedule2Controller.class, "createOrUpdateSchedule");
        assertGet(Schedule2Controller.class, "getTimeline");
        assertDelete(Schedule2Controller.class, "deleteSchedule");
    }
    
    @Test
    public void deleteSchedule() {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage retValue = controller.deleteSchedule(SCHEDULE_GUID);
        assertEquals(retValue, Schedule2Controller.DELETED_MSG);
        
        verify(mockService).deleteSchedulePermanently(TEST_APP_ID, SCHEDULE_GUID);
    }
    
    @Test
    public void getSchedule() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        
        Study study = Study.create();
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        when(mockService.getScheduleForStudy(TEST_APP_ID, study)).thenReturn(schedule);
        
        Schedule2 retValue = controller.getSchedule(TEST_STUDY_ID);
        assertEquals(retValue, schedule);
    }
    
    @Test
    public void createOrUpdateSchedule_create() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);        

        Study study = Study.create();
        study.setPhase(DESIGN);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Test");
        TestUtils.mockRequestBody(mockRequest, schedule);
        
        when(mockService.createOrUpdateStudySchedule(any(), any())).thenReturn(schedule);
        
        ResponseEntity<Schedule2> retValue = controller.createOrUpdateSchedule(TEST_STUDY_ID);
        assertEquals(retValue.getBody().getName(), "Test");
        assertEquals(retValue.getStatusCodeValue(), 201);
        
        verify(mockService).createOrUpdateStudySchedule(any(), scheduleCaptor.capture());
        // This has been set from the session.
        assertEquals(scheduleCaptor.getValue().getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void createOrUpdateSchedule_update() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);        

        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        study.setPhase(DESIGN);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Test");
        TestUtils.mockRequestBody(mockRequest, schedule);
        
        when(mockService.createOrUpdateStudySchedule(any(), any())).thenReturn(schedule);
        
        ResponseEntity<Schedule2> retValue = controller.createOrUpdateSchedule(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*Study schedule cannot be changed.*")
    public void createOrUpdateSchedule_studyInWrongPhase() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);        

        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        study.setPhase(RECRUITMENT);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Test");
        TestUtils.mockRequestBody(mockRequest, schedule);
        
        when(mockService.createOrUpdateStudySchedule(any(), any())).thenReturn(schedule);
        
        ResponseEntity<Schedule2> retValue = controller.createOrUpdateSchedule(TEST_STUDY_ID);
        assertEquals(retValue.getStatusCodeValue(), 200);        
    }
    
    @Test
    public void getTimeline() {
        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        Timeline timeline = new Timeline.Builder().build();
        when(mockService.getTimelineForSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(timeline);
        
        Timeline retValue = controller.getTimeline(TEST_STUDY_ID);
        assertEquals(retValue, timeline);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTimeline_studyHasNoSchedule() {
        Study study = Study.create();
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        doReturn(session).when(controller).getAdministrativeSession();
        
        controller.getTimeline(TEST_STUDY_ID);
    }    
}
