package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.validation.Errors;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.services.SchedulePlanService;

public class ScheduleControllerTest extends Mockito {

    @InjectMocks
    @Spy
    ScheduleController controller;
    
    @Mock
    SchedulePlanService mockSchedulePlanService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    String appId;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        appId = TestUtils.randomName(ScheduleControllerTest.class);
        String userAgent = "app name/9";
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache(userAgent);
        
        List<SchedulePlan> plans = TestUtils.getSchedulePlans(appId);
        
        // Add a plan that will returns null for a schedule, this is not included in the final list.
        // This is now possible and should not cause an error or a gap in the returned array.
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setStrategy(new ScheduleStrategy() {
            @Override
            public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
                return null;
            }
            @Override
            public void validate(Set<String> dataGroups, Set<String> studyIds, Set<String> taskIdentifiers, Errors errors) {
            }
            @Override
            public List<Schedule> getAllPossibleSchedules() {
                return ImmutableList.of();
            }
        });
        plans.add(plan);
        
        when(mockSchedulePlanService.getSchedulePlans(clientInfo, appId, false)).thenReturn(plans);
        
        UserSession session = new UserSession();
        session.setAppId(appId);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        RequestContext.set(new RequestContext.Builder().withUserAgent(userAgent).build());
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ScheduleController.class);
        assertGet(ScheduleController.class, "getSchedulesV1");
        assertGet(ScheduleController.class, "getSchedulesV3");
        assertGet(ScheduleController.class, "getSchedules");
    }
    
    @Test
    public void getSchedules() throws Exception {
        ResourceList<Schedule> result = controller.getSchedules();
        
        assertEquals(result.getItems().size(), 3);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getSchedulesV3AdjustsScheduleTypes() throws Exception {
        List<SchedulePlan> plans = Lists.newArrayList();

        Schedule schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Label").withTask("foo").build());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 12 1/1 * ? *");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.setAppId(TEST_APP_ID);
        plan.setStrategy(strategy);
        plans.add(plan);

        schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Label").withTask("foo").build());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 12 1/1 * ? *");
        strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.setAppId(TEST_APP_ID);
        plan.setStrategy(strategy);
        plans.add(plan);
        
        when(mockSchedulePlanService.getSchedulePlans(any(), any(), anyBoolean())).thenReturn(plans);
        controller.setSchedulePlanService(mockSchedulePlanService);
        
        String result = controller.getSchedulesV3();

        JsonNode node = BridgeObjectMapper.get().readTree(result);
        ArrayNode array = (ArrayNode)node.get("items");
        
        // Verify that both objects have been adjusted so that despite the fact that they are 
        // marked as persistent, they are also marked as recurring.
        ObjectNode schedule1 = (ObjectNode)array.get(0);
        assertTrue(schedule1.get("persistent").asBoolean());
        assertEquals(schedule1.get("scheduleType").asText(), "recurring");
        
        ObjectNode schedule2 = (ObjectNode)array.get(1);
        assertTrue(schedule2.get("persistent").asBoolean());
        assertEquals(schedule2.get("scheduleType").asText(), "recurring");
    }
}
