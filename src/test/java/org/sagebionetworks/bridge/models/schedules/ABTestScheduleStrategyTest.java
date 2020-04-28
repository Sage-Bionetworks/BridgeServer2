package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Further tests for these strategy objects are in ScheduleStrategyTest.
 */
public class ABTestScheduleStrategyTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private ArrayList<String> healthCodes;
    private App app;

    @BeforeMethod
    public void before() {
        app = TestUtils.getValidApp(ScheduleStrategyTest.class);
        healthCodes = Lists.newArrayList();
        for (int i = 0; i < 1000; i++) {
            healthCodes.add(BridgeUtils.generateGuid());
        }
    }
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ABTestScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void testScheduleCollector() {
        SchedulePlan plan = TestUtils.getABTestSchedulePlan(TEST_APP_ID);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(schedules.size(), 3);
        assertEquals(schedules.get(0).getLabel(), "Schedule 1");
        assertEquals(schedules.get(1).getLabel(), "Schedule 2");
        assertEquals(schedules.get(2).getLabel(), "Schedule 3");
        assertTrue(schedules instanceof ImmutableList);
    }
    
    @Test
    public void canRountripABTestingPlan() throws Exception {
        DynamoSchedulePlan plan = createABSchedulePlan();
        String output = MAPPER.writeValueAsString(plan);

        JsonNode node = MAPPER.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);
        newPlan.setStudyKey(plan.getStudyKey()); // not serialized.

        assertEquals(newPlan, plan, "Plan with AB testing strategy was serialized/deserialized");

        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy) plan.getStrategy();
        ABTestScheduleStrategy newStrategy = (ABTestScheduleStrategy) newPlan.getStrategy();
        assertEquals(newStrategy.getScheduleGroups().get(0).getSchedule(), strategy.getScheduleGroups().get(0).getSchedule(),
                "Deserialized AB testing strategy is complete");
    }

    @Test
    public void verifyABTestingStrategyWorks() {
        DynamoSchedulePlan plan = createABSchedulePlan();

        List<Schedule> schedules = Lists.newArrayList();
        for (String healthCode : healthCodes) {
            ScheduleContext context = new ScheduleContext.Builder()
                    .withAppId(app.getIdentifier())
                    .withHealthCode(healthCode).build();
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            schedules.add(schedule);
        }

        // We want 4 in A, 4 in B and 2 in C, and they should not be in order...
        Multiset<String> countsByLabel = HashMultiset.create();
        for (Schedule schedule : schedules) {
            countsByLabel.add(schedule.getLabel());
        }
        assertTrue(Math.abs(countsByLabel.count("A") - 400) < 50, "40% users assigned to A");
        assertTrue(Math.abs(countsByLabel.count("B") - 400) < 50, "40% users assigned to B");
        assertTrue(Math.abs(countsByLabel.count("C") - 200) < 50, "20% users assigned to C");
    }
    
    @Test
    public void validatesNewABTestingPlan() {
        SchedulePlan plan = new DynamoSchedulePlan();
        
        Set<String> taskIdentifiers = ImmutableSet.of("taskIdentifierA");
        
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(20, TestUtils.getSchedule("A Schedule"));
        
        Errors errors = Validate.getErrorsFor(plan);
        strategy.validate(TestConstants.USER_DATA_GROUPS, TestConstants.USER_SUBSTUDY_IDS, taskIdentifiers, errors);
        Map<String,List<String>> map = Validate.convertErrorsToSimpleMap(errors);
        
        List<String> errorMessages = map.get("scheduleGroups");
        assertEquals(errorMessages.get(0), "scheduleGroups groups must add up to 100%");
        errorMessages = map.get("scheduleGroups[0].schedule.expires");
        assertEquals(errorMessages.get(0), "scheduleGroups[0].schedule.expires must be set if schedule repeats");
    }
    
    private DynamoSchedulePlan createABSchedulePlan() {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        // plan.setGuid("a71eecc3-5e75-4a11-91f4-c587999cbb20");
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(app.getIdentifier());
        plan.setStrategy(createABTestStrategy());
        return plan;
    }

    private ABTestScheduleStrategy createABTestStrategy() {
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, TestUtils.getSchedule("A"));
        strategy.addGroup(40, TestUtils.getSchedule("B"));
        strategy.addGroup(20, TestUtils.getSchedule("C"));
        return strategy;
    }

}
