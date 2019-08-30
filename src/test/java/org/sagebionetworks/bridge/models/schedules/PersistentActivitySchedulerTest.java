package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

public class PersistentActivitySchedulerTest {
    
    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    private static final DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
    
    private Map<String, DateTime> events;
    private List<ScheduledActivity> scheduledActivities;
    private SchedulePlan plan;
    private Schedule schedule;
    
    @BeforeMethod
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(1428340210000L);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");

        events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        schedule = new Schedule();
        schedule.setEventId("enrollment");
        schedule.getActivities().add(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.PERSISTENT);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void scheduleWithMultipleActivitiesWorks() {
        schedule.getActivities().add(TestUtils.getActivity1());
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertDates(scheduledActivities, MSK, "2015-03-23 10:00", "2015-03-23 10:00");
    }
    
    @Test
    public void scheduleWorks() {
        // enrollment "2015-03-23T10:00:00Z"
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertDates(scheduledActivities, MSK, "2015-03-23 10:00");
    }
    @Test
    public void startsOnScheduleWorks() {
        schedule.setStartsOn("2015-04-10T09:00:00Z");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void endsOnScheduleWorks() {
        // enrollment "2015-03-23T10:00:00Z"
        schedule.setEndsOn("2015-03-21T10:00:00Z");
        
        // In this case the endsOn date is before the enrollment. No activities.
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
        
        schedule.setEndsOn("2015-04-23T13:40:00Z");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, MSK, "2015-03-23 10:00");
    }
    @Test
    public void startEndsOnScheduleWorks() {
        // Because we're shifting time to midnight, we need to change this time to midnight
        // or this test will not pass, and that's expected.
        schedule.setStartsOn("2015-03-23T00:00:00Z");
        schedule.setEndsOn("2015-03-26T10:00:00Z");
        
        // Should get one activity
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, MSK, "2015-03-23 10:00");
    }
    @Test
    public void sequenceOfEventsWorks() {
        // starts when a different ask is completed
        schedule.setEventId("survey:AAA:finished");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
        
        // Once that occurs, a task is issued for "right now"
        events.put("survey:AAA:finished", asDT("2015-04-10 11:40"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, MSK, "2015-04-10 11:40");
        
        // and it's reissued any time that task itself is completed.
        events.put("activity:"+schedule.getActivities().get(0).getGuid()+":finished", asDT("2015-04-12 09:40"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(2)));
        assertDates(scheduledActivities, MSK, "2015-04-12 09:40");
    }
    @Test
    public void originalPersistentScheduleStructureStillWorks() {
        schedule.setEventId("activity:AAA:finished,enrollment");
        schedule.setScheduleType(ScheduleType.ONCE);
        
        assertTrue(schedule.getPersistent());
        assertTrue(TestUtils.getActivity3().isPersistentlyRescheduledBy(schedule));
        assertTrue(schedule.schedulesImmediatelyAfterEvent());
        
        schedule.setEventId("activity:BBB:finished,enrollment");
        schedule.setDelay("P1D");
        assertFalse(schedule.getPersistent());
        assertFalse(TestUtils.getActivity3().isPersistentlyRescheduledBy(schedule));
        assertFalse(schedule.schedulesImmediatelyAfterEvent());
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn.withZone(MSK))
            .withHealthCode("AAA")
            .withEvents(events).build();
    }
}
