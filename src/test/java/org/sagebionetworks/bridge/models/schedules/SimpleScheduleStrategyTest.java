package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.springframework.validation.Errors;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.time.DateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Further tests for these strategy objects are in ScheduleStrategyTest.
 */
public class SimpleScheduleStrategyTest extends Mockito {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private App app;

    @BeforeMethod
    public void before() {
        app = TestUtils.getValidApp(ScheduleStrategyTest.class);
    }

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SimpleScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }

    @Test
    public void canRountripSimplePlan() throws Exception {
        Schedule schedule = TestUtils.getSchedule("AAA");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);

        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setAppId(app.getIdentifier());
        plan.setStrategy(strategy);

        String output = MAPPER.writeValueAsString(plan);
        JsonNode node = MAPPER.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);

        newPlan.setAppId(plan.getAppId()); // not serialized
        assertEquals(newPlan, plan, "Plan with simple strategy was serialized/deserialized");

        SimpleScheduleStrategy newStrategy = (SimpleScheduleStrategy) newPlan.getStrategy();
        assertEquals(newStrategy.getSchedule(), strategy.getSchedule(),
                "Deserialized simple testing strategy is complete");
    }
    
    @Test
    public void validates() {
        SchedulePlan plan = new DynamoSchedulePlan();
        
        Set<String> taskIdentifiers = Sets.newHashSet("taskIdentifierA");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(TestUtils.getSchedule("A Schedule"));
        
        Errors mockErrors = mock(Errors.class);
        strategy.validate(TestConstants.USER_DATA_GROUPS, TestConstants.USER_STUDY_IDS, taskIdentifiers, mockErrors);
        
        verify(mockErrors).pushNestedPath("schedule");
        verify(mockErrors).rejectValue("expires", "must be set if schedule repeats");
    }
    
    @Test
    public void testScheduleCollector() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_APP_ID);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(schedules.size(), 1);
        assertEquals(schedules.get(0).getLabel(), "Test label for the user");
        assertTrue(schedules instanceof ImmutableList);
    }
}
