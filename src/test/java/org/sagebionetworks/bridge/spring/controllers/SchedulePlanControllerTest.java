package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.getSimpleSchedulePlan;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.testng.Assert.assertEquals;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.StudyService;

public class SchedulePlanControllerTest extends Mockito {
    
    private static final String GUID = "oneGuid";
    private static final Long VERSION = 3L;

    @Spy
    @InjectMocks
    SchedulePlanController controller;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    SchedulePlanService mockSchedulePlanService;
    
    @Mock
    UserSession mockUserSession;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<SchedulePlan> schedulePlanCaptor;
    
    Study study;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        study = new DynamoStudy();
        study.setIdentifier(TEST_APP_ID);
        
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        when(mockUserSession.getStudyIdentifier()).thenReturn(TEST_APP_ID);
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SchedulePlanController.class);
        assertGet(SchedulePlanController.class, "getSchedulePlansForWorker");
        assertGet(SchedulePlanController.class, "getSchedulePlans");
        assertCreate(SchedulePlanController.class, "createSchedulePlan");
        assertGet(SchedulePlanController.class, "getSchedulePlan");
        assertPost(SchedulePlanController.class, "updateSchedulePlan");
        assertDelete(SchedulePlanController.class, "deleteSchedulePlan");
    }
    
    @Test
    public void getSchedulePlansForWorker() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(WORKER);
        SchedulePlan plan = createSchedulePlan();
        when(mockSchedulePlanService.getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, false))
                .thenReturn(Lists.newArrayList(plan));
        
        ResourceList<SchedulePlan> result = controller.getSchedulePlansForWorker(TEST_APP_ID, false);
        assertEquals(result.getItems().get(0), plan);
        
        verify(mockSchedulePlanService).getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, false);
    }

    @Test
    public void testCreateSchedulePlan() throws Exception {
        SchedulePlan plan = createSchedulePlan();
        mockRequestBody(mockRequest, plan);
        
        when(mockSchedulePlanService.createSchedulePlan(eq(study), any())).thenReturn(plan);
        
        GuidVersionHolder holder = controller.createSchedulePlan();
        assertEquals(holder.getGuid(), GUID);
        assertEquals(holder.getVersion(), VERSION);
        
        verify(mockSchedulePlanService).createSchedulePlan(eq(study), schedulePlanCaptor.capture());
        SchedulePlan savedPlan = schedulePlanCaptor.getValue();
        assertEquals(plan, savedPlan);
    }

    @Test
    public void updateSchedulePlan() throws Exception {
        SchedulePlan plan = createSchedulePlan();
        mockRequestBody(mockRequest, plan);
        
        when(mockSchedulePlanService.updateSchedulePlan(eq(study), any())).thenReturn(plan);
        
        GuidVersionHolder holder = controller.updateSchedulePlan(plan.getGuid());
        assertEquals(holder.getGuid(), GUID);
        assertEquals(holder.getVersion(), VERSION);
        
        verify(mockSchedulePlanService).updateSchedulePlan(eq(study), schedulePlanCaptor.capture());
        
        SchedulePlan savedPlan = schedulePlanCaptor.getValue();
        assertEquals(plan, savedPlan);
    }
    
    @Test
    public void getSchedulePlansExcludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        List<SchedulePlan> plans = ImmutableList.of(getSimpleSchedulePlan(TEST_APP_ID));
        
        when(mockSchedulePlanService.getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, false)).thenReturn(plans);
        
        ResourceList<SchedulePlan> result = controller.getSchedulePlans(false);
        
        verify(mockSchedulePlanService).getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, false);
        
        assertEquals(1, result.getItems().size());
        assertEquals(plans.get(0).getGuid(), result.getItems().get(0).getGuid());
    }
    
    @Test
    public void getSchedulePlansIncludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        List<SchedulePlan> plans = ImmutableList.of(getSimpleSchedulePlan(TEST_APP_ID));
        when(mockSchedulePlanService.getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, true)).thenReturn(plans);
        
        ResourceList<SchedulePlan> result = controller.getSchedulePlans(true);
        
        verify(mockSchedulePlanService).getSchedulePlans(UNKNOWN_CLIENT, TEST_APP_ID, true);
        
        assertEquals(1, result.getItems().size());
        assertEquals(plans.get(0).getGuid(), result.getItems().get(0).getGuid());
    }
    
    @Test
    public void getSchedulePlan() throws Exception {
        SchedulePlan plan = getSimpleSchedulePlan(TEST_APP_ID);
        plan.setGuid("GGG");
        
        when(mockSchedulePlanService.getSchedulePlan(TEST_APP_ID, "GGG")).thenReturn(plan);
        
        SchedulePlan result = controller.getSchedulePlan("GGG");
        
        verify(mockSchedulePlanService).getSchedulePlan(TEST_APP_ID, "GGG");
        assertEquals(result.getGuid(), plan.getGuid());
    }
    
    @Test
    public void deleteSchedulePlan() throws Exception {
        StatusMessage result = controller.deleteSchedulePlan("GGG", false);
        assertEquals(result, SchedulePlanController.DELETE_MSG);
        
        verify(mockSchedulePlanService).deleteSchedulePlan(study.getIdentifier(), "GGG");
    }
    
    @Test
    public void deleteSchedulePlanPermanently() throws Exception {
        when(mockUserSession.isInRole(ADMIN)).thenReturn(true);
        
        StatusMessage result = controller.deleteSchedulePlan("GGG", true);
        assertEquals(result, SchedulePlanController.DELETE_MSG);
        
        verify(mockSchedulePlanService).deleteSchedulePlanPermanently(TEST_APP_ID, "GGG");
    }
    
    @Test
    public void deleteSchedulePlanPermanentlyOnlyLogicalForDeveloper() throws Exception {
        StatusMessage result = controller.deleteSchedulePlan("GGG", true);
        assertEquals(result, SchedulePlanController.DELETE_MSG);
        
        verify(mockSchedulePlanService).deleteSchedulePlan(TEST_APP_ID, "GGG");
    }
    
    private SchedulePlan createSchedulePlan() {
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        Schedule schedule = new Schedule();
        strategy.addGroup(50, schedule);

        schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Foo").build());
        strategy.addGroup(20, schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("schedulePlanGuid");
        plan.setStrategy(strategy);
        plan.setGuid(GUID);
        plan.setVersion(VERSION);
        return plan;
    }
}
