package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.services.Schedule2Service;

public class Schedule2ControllerTest extends Mockito {
    
    @Mock
    Schedule2Service mockService;
    
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
        assertGet(Schedule2Controller.class, "getSchedules");
        assertGet(Schedule2Controller.class, "getSchedule");
        assertCreate(Schedule2Controller.class, "createSchedule");
        assertPost(Schedule2Controller.class, "updateSchedule");
        assertPost(Schedule2Controller.class, "publishSchedule");
        assertDelete(Schedule2Controller.class, "deleteSchedule");
    }
    
    private void permitAsDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(DEVELOPER)).build());
    }
    
    private void permitAsStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID)
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
    }
    
    @Test
    public void getSchedulesForDeveloper() {
        permitAsDeveloper();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<>(list, 10, true);
        
        when(mockService.getSchedules(any(), anyInt(), anyInt(), anyBoolean())).thenReturn(page);
        
        PagedResourceList<Schedule2> retValue = controller.getSchedules("100", "50", "true");
        assertEquals(retValue, page);
        
        verify(mockService).getSchedules(TEST_APP_ID, 100, 50, true);
    }
    
    @Test
    public void getSchedulesForStudyCoordinator() {
        permitAsStudyCoordinator();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<>(list, 10, true);
        when(mockService.getSchedulesForOrganization(any(), any(), anyInt(), anyInt(), anyBoolean())).thenReturn(page);
        
        PagedResourceList<Schedule2> retValue = controller.getSchedules("100", "50", "true");
        assertEquals(retValue, page);
        
        verify(mockService).getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 50, true);
    }
    
    @Test
    public void getSchedulesDefaultsValuesForDeveloper() {
        permitAsDeveloper();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<>(list, 10, true);
        when(mockService.getSchedules(any(), anyInt(), anyInt(), anyBoolean())).thenReturn(page);
        
        PagedResourceList<Schedule2> retValue = controller.getSchedules(null, null, null);
        assertSame(retValue, page);
        
        verify(mockService).getSchedules(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void getSchedulesDefaultsValuesForStudyCoordinator() {
        permitAsStudyCoordinator();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<>(list, 10, true);
        when(mockService.getSchedulesForOrganization(any(), any(), anyInt(), anyInt(), anyBoolean())).thenReturn(page);

        PagedResourceList<Schedule2> retValue = controller.getSchedules(null, null, null);
        assertSame(retValue, page);
        
        verify(mockService).getSchedulesForOrganization(
                TEST_APP_ID, TEST_ORG_ID, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void getSchedulesWithMultipleRolesBehavesAsDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER, STUDY_COORDINATOR))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(DEVELOPER, STUDY_COORDINATOR))
                .build());
        
        // This caller will be treated as a developer
        controller.getSchedules(null, null, null);
        
        verify(mockService).getSchedules(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void createSchedule() throws Exception {
        Schedule2 schedule = new Schedule2();
        mockRequestBody(mockRequest, schedule);
        
        Schedule2 created = new Schedule2();
        created.setGuid(GUID);
        when(mockService.createSchedule(any())).thenReturn(created);
        
        Schedule2 retValue = controller.createSchedule();
        assertEquals(retValue.getGuid(), GUID);
        
        verify(mockService).createSchedule(scheduleCaptor.capture());
        assertEquals(scheduleCaptor.getValue().getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void getSchedule() {
        Schedule2 schedule = new Schedule2();
        when(mockService.getSchedule(TEST_APP_ID, GUID)).thenReturn(schedule);
        
        Schedule2 retValue = controller.getSchedule(GUID);
        assertEquals(retValue, schedule);
        
        verify(mockService).getSchedule(TEST_APP_ID, GUID);
    }
    
    @Test
    public void updateSchedule() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());

        Schedule2 existing = new Schedule2();
        existing.setVersion(100L);
        when(mockService.updateSchedule(any(), any())).thenReturn(existing);
        
        when(mockService.getSchedule(TEST_APP_ID, GUID)).thenReturn(existing);
        
        Schedule2 schedule = new Schedule2();
        mockRequestBody(mockRequest, schedule);
        
        Schedule2 retValue = controller.updateSchedule(GUID);
        assertEquals(retValue, existing);
        
        verify(mockService).updateSchedule(eq(existing), scheduleCaptor.capture());
        
        Schedule2 persisted = scheduleCaptor.getValue();
        assertEquals(persisted.getGuid(), GUID);
        assertEquals(persisted.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void publishSchedule() {
        StatusMessage retValue = controller.publishSchedule(GUID);
        assertSame(retValue, Schedule2Controller.PUBLISHED_MSG);
        
        verify(mockService).publishSchedule(TEST_APP_ID, GUID);
    }
    
    @Test
    public void deleteSchedule() {
        StatusMessage retValue = controller.deleteSchedule(GUID);
        assertSame(retValue, Schedule2Controller.DELETED_MSG);
        
        verify(mockService).deleteSchedulePermanently(TEST_APP_ID, GUID);
    }
    
    @Test
    public void getTimelineForSchedule() { 
        controller.getTimelineForSchedule(GUID);
        verify(mockService).getTimelineForSchedule(TEST_APP_ID, GUID);
    }
    
}
