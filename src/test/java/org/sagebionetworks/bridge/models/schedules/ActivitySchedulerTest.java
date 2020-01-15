package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asLong;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.models.RangeTuple;
import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * These tests cover other aspects of the scheduler besides the accuracy of its scheduling.
 *
 */
public class ActivitySchedulerTest {

    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    private static final DateTime NOW = DateTime.parse("2015-03-26T14:40:00-07:00");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone IST = DateTimeZone.forOffsetHoursMinutes(5, 30);
    
    private List<ScheduledActivity> scheduledActivities;
    private Map<String,DateTime> events;
    private DynamoSchedulePlan plan = new DynamoSchedulePlan();
    
    @BeforeMethod
    public void before() {
        plan.setGuid("BBB");
        
        // Day of tests is 2015-03-26T14:40:00-07:00 for purpose of calculating expiration
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());

        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
        events.put("survey:event", ENROLLMENT.plusDays(2));
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void schedulerIsPassedNoEvents() {
        // Shouldn't happen, but then again, the events table starts empty.
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1D");
        schedule.addActivity(TestUtils.getActivity3());
        
        Map<String,DateTime> empty = Maps.newHashMap();
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusWeeks(1))
            .withEvents(empty).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertEquals(scheduledActivities.size(), 0);
        
        context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusWeeks(1)).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertEquals(scheduledActivities.size(), 0);
    }
    
    @Test
    public void activityIsComplete() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setLabel("This is a label");
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setExpires("P3Y");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusWeeks(1)));
        DynamoScheduledActivity schActivity = (DynamoScheduledActivity)scheduledActivities.get(0);

        assertNotNull(schActivity.getGuid());
        assertEquals(schActivity.getActivity().getLabel(), "Activity3");
        assertEquals(schActivity.getSchedulePlanGuid(), "BBB");
        assertNotNull(schActivity.getScheduledOn());
        assertNotNull(schActivity.getExpiresOn());
        assertEquals(schActivity.getHealthCode(), "healthCode");
        assertEquals(schActivity.getTimeZone(), PST);
        assertEquals(schActivity.getReferentGuid(), "tapTest:task:2015-03-23T03:00:00.000");
    }
    
    /**
     * Activity #1 starts from enrollment. Every time it is scheduled, schedule Activity#2 to 
     * happen once, at exactly the same time. This is not useful, but it should work, 
     * or something is wrong with our model vis-a-vis the implementation.
     */
    @Test
    public void activitiesCanBeChainedTogether() throws Exception {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity3());
        schedule.setScheduleType(ONCE);
        schedule.setDelay("P1M");
        
        Schedule schedule2 = new Schedule();
        schedule2.getActivities().add(TestUtils.getActivity3());
        schedule2.setScheduleType(ONCE);
        schedule2.setEventId("task:task1");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusMonths(2)));
        assertEquals(scheduledActivities.get(0).getScheduledOn().getMillis(), asLong("2015-04-23 10:00"));

        scheduledActivities = schedule2.getScheduler().getScheduledActivities(plan, getContext(NOW.plusMonths(2)));
        assertEquals(scheduledActivities.size(), 0);
        
        DateTime activity1Event = asDT("2015-04-25 15:32", PST);
        
        // Now say that activity was finished a couple of days after that:
        events.put("task:task1", activity1Event);
        
        // One-time tasks without times specified continue to schedule at midnight.
        scheduledActivities = schedule2.getScheduler().getScheduledActivities(plan, getContext(NOW.plusMonths(2)));
        assertEquals(scheduledActivities.get(0).getScheduledOn(), activity1Event);
    }
    
    @Test
    public void activitySchedulerWorksInDifferentTimezone() {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setEventId("foo");
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay("P2D");
        schedule.addTimes("07:00");

        // Event is recorded in PDT. And when we get the activity back, it is scheduled in PDT. 
        events.put("foo", DateTime.parse("2015-03-25T07:00:00.000-07:00"));
        DateTimeZone zone = DateTimeZone.forOffsetHours(-7);
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(zone, NOW.plusMonths(1)));
        assertEquals(scheduledActivities.get(0).getScheduledOn(), DateTime.parse("2015-03-27T07:00:00.000-07:00"));
        
        // Add an endsOn value in GMT, it shouldn't matter, it'll prevent event from firing
        schedule.setEndsOn("2015-03-25T13:00:00.000-07:00"); // one hour before the event
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    
    @Test
    public void activitySequencesGeneratedAtDifferentTimesAreTheSame() throws Exception {
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setInterval("P1D");
        schedule.addTimes("10:00");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(20)));
        assertDates(scheduledActivities, PST, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-13 08:00", PST));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(20)));
        assertDates(scheduledActivities, PST, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void cronActivitiesGeneratedAtDifferentTimesAreTheSame() throws Exception {
        events.put("anEvent", asDT("2015-04-12 08:31"));
        
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("anEvent");
        schedule.setCronTrigger("0 0 10 ? * MON,TUE,WED,THU,FRI,SAT,SUN *");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(20)));
        assertDates(scheduledActivities, PST, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
        
        events.put("now", asDT("2015-04-07 08:00", PST));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(20)));
        assertDates(scheduledActivities, PST, "2015-04-12 10:00", "2015-04-13 10:00", "2015-04-14 10:00", "2015-04-15 10:00");
    }
    
    @Test
    public void twoActivitiesWithSameTimestampCanBeDifferentiated() throws Exception {
        // Add second activity to same schedule
        Schedule schedule = new Schedule();
        schedule.getActivities().add(new Activity.Builder().withLabel("Label1").withTask("tapTest").build());
        schedule.getActivities().add(new Activity.Builder().withLabel("Label2").withTask("tapTest").build());
        schedule.setScheduleType(ONCE);

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(7)));
        assertEquals(scheduledActivities.size(), 2);
        assertNotEquals(scheduledActivities.get(1), scheduledActivities.get(0));
    }
    
    /**
     * You can submit activities that weren't derived from any scheduling information. Details TBD, but the 
     * scheduler needs to support a "submit activity, get activities, see activity under new GUID" scenario.
     */
    @Test
    public void activityThatIsAlwaysAvailable() throws Exception {
        // Just fire this each time it is itself completed, and it never expires.
        Schedule schedule = new Schedule();
        schedule.setEventId("scheduledOn:task:foo");
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ONCE);
        
        events.put("scheduledOn:task:foo", NOW.minusHours(3));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.get(0).getScheduledOn(), NOW.minusHours(3));

        events.put("scheduledOn:task:foo", NOW.plusHours(8));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.get(0).getScheduledOn(), NOW.plusHours(8));
    }
    
    @Test
    public void willSelectFirstEventIdWithARecord() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setEventId("survey:event, enrollment");
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ONCE);
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 1);
        assertEquals(scheduledActivities.get(0).getScheduledOn(), ENROLLMENT.plusDays(2).withZone(PST));

        events.remove("survey:event");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 1);
        assertEquals(scheduledActivities.get(0).getScheduledOn(), ENROLLMENT.withZone(PST));
        
        // BUT this produces nothing because the system doesn't fallback to enrollment if an event has been set
        schedule.setEventId("survey:event");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    
    @Test
    public void willSelectFirstEventIdWithARecordUsingTime() throws Exception {
        LocalTime localTime = LocalTime.parse("18:00:00.000");
        
        Schedule schedule = new Schedule();
        schedule.setEventId("survey:event, enrollment");
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ONCE);
        schedule.addTimes(localTime);
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 1);
        assertEquals(scheduledActivities.get(0).getScheduledOn(), ENROLLMENT.withTime(localTime).plusDays(2).withZoneRetainFields(PST));

        events.remove("survey:event");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 1);
        assertEquals(scheduledActivities.get(0).getScheduledOn(), ENROLLMENT.withZone(PST).withTime(localTime));
        
        // BUT this produces nothing because the system doesn't fallback to enrollment if an event has been set
        schedule.setEventId("survey:event");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertEquals(scheduledActivities.size(), 0);
    }

    // branch coverage
    @Test
    public void contextWithEmptyEventMap() {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ONCE);

        events.clear();

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(14)));
        assertTrue(scheduledActivities.isEmpty());
    }

    @Test
    public void activitiesMarkedPersistentUnderCorrectCircumstances() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("task:foo:finished,enrollment");
        schedule.addActivity(new Activity.Builder().withLabel("Foo").withTask("foo").build());
        schedule.addActivity(new Activity.Builder().withLabel("Bar").withTask("bar").build());
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        ScheduledActivity activity1 = scheduledActivities.get(0);
        assertEquals(activity1.getActivity().getLabel(), "Foo");
        assertTrue(activity1.getPersistent());
        assertTrue(activity1.getActivity().isPersistentlyRescheduledBy(schedule));
        
        ScheduledActivity activity2 = scheduledActivities.get(1);
        assertEquals(activity2.getActivity().getLabel(), "Bar");
        assertFalse(activity2.getPersistent());
        assertFalse(activity2.getActivity().isPersistentlyRescheduledBy(schedule));
        
        schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("activity:AAA:finished,enrollment");
        schedule.setLabel("Test");
        schedule.addActivity(new Activity.Builder().withGuid("BBB").withLabel("Bar").withTask("bar").build());
        schedule.addActivity(TestUtils.getActivity3());
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(NOW.plusDays(1)));
        assertFalse(scheduledActivities.get(0).getPersistent());
        assertTrue(scheduledActivities.get(1).getPersistent());
    }
    
    
    @Test
    public void scheduleIsTranslatedToTimeZoneWhenCreatedAndPersisted() {
        // 10am every morning from the time of enrollment
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withGuid("guid").withLabel("Foo").withTask("foo").build());
        Validate.entityThrowingException(new ScheduleValidator(Sets.newHashSet("foo")), schedule);
        
        // User is in Moscow, however.
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, getContext(zone, NOW.withZone(zone).plusDays(1)));
        assertEquals(activities.get(0).getScheduledOn().toString(), "2015-03-26T10:00:00.000+03:00");
        assertEquals(activities.get(1).getScheduledOn().toString(), "2015-03-27T10:00:00.000+03:00");
        
        // Now the user flies across the planet, and retrieves the tasks again, they are in the new timezone
        zone = DateTimeZone.forOffsetHours(-7);
        activities = schedule.getScheduler().getScheduledActivities(plan, getContext(zone, NOW.withZone(zone).plusDays(1)));
        assertEquals(activities.get(0).getScheduledOn().toString(), "2015-03-26T10:00:00.000-07:00");
        assertEquals(activities.get(1).getScheduledOn().toString(), "2015-03-27T10:00:00.000-07:00");
    }
    
    @Test
    public void activitiesContainActivityGuidButAllAreUnique() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(
                new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Foo").withTask("foo").build());
        Validate.entityThrowingException(new ScheduleValidator(Sets.newHashSet("foo")), schedule);
        
        List<ScheduledActivity> activities = schedule.getScheduler().getScheduledActivities(plan, getContext(PST, NOW.plusDays(4)));
        Set<String> allGuids = Sets.newHashSet();
        for (ScheduledActivity schActivity : activities) {
            String activityGuidInScheduledActivity = schActivity.getGuid().split(":")[0];
            assertEquals(activityGuidInScheduledActivity, schActivity.getActivity().getGuid());
            allGuids.add(schActivity.getGuid());
        }
        assertEquals(allGuids.size(), activities.size());
    }
    
    @Test
    public void minimumOverridesTimeBasedIntervalScheduling() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setLabel("Monthly Schedule");
        schedule.setInterval(Period.parse("P1M"));
        schedule.addTimes("14:00");
        schedule.setExpires("P1W");
        schedule.addActivity(new Activity.Builder().withLabel("An activity").withTask("taskId").build());
        
        verifyMinimumIsMet(schedule, Lists.newArrayList("2015-04-23T14:00:00.000-07:00", "2015-05-23T14:00:00.000-07:00",
                "2015-06-23T14:00:00.000-07:00", "2015-07-23T14:00:00.000-07:00"));
    }

    @Test
    public void minimumOverridesTimeBasedCronScheduling() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setLabel("Monthly Schedule");
        schedule.setCronTrigger("0 0 14 10 1/1 ? *");
        schedule.setExpires("P1W");
        schedule.addActivity(new Activity.Builder().withLabel("An activity").withTask("taskId").build());
        
        verifyMinimumIsMet(schedule, Lists.newArrayList("2015-04-10T14:00:00.000-07:00", "2015-05-10T14:00:00.000-07:00",
                "2015-06-10T14:00:00.000-07:00", "2015-07-10T14:00:00.000-07:00"));
    }
    
    private void verifyMinimumIsMet(Schedule schedule, List<String> dates) {
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        
        // Going out 1 week into the future, with a day day expiration winow, the old task will be 
        // expired and the new task will not be generated. Without a minimum, nothing will be returned
        ScheduleContext noMinContext = getContext(PST, NOW.withZone(PST).plusDays(10));
        noMinContext = new ScheduleContext.Builder().withContext(noMinContext).withStartsOn(NOW.plusDays(10)).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, noMinContext);

        // There are none on the monthly schedule
        assertEquals(scheduledActivities.size(), 0);
        
        // Adjust this to have a minimum of 4
        ScheduleContext context = new ScheduleContext.Builder()
                .withContext(noMinContext)
                .withMinimumPerSchedule(4).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertEquals(scheduledActivities.size(), 4);
        for (int i=0; i < 4; i++) {
            assertEquals(scheduledActivities.get(i).getScheduledOn().toString(), dates.get(i));    
        }
    }
    
    @Test
    public void greaterOfMinOrTimeBasedSchedulingReturned() {
        // In this test the N days values return more tasks than the minimum, the full set of 
        // tasks should be returned (not the minimum).
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setLabel("Daily Schedule");
        schedule.setInterval(Period.parse("P1D"));
        schedule.addTimes("14:00");
        schedule.setExpires("P1D");
        schedule.addActivity(new Activity.Builder().withLabel("An activity").withTask("taskId").build());
        
        // 2015-04-06T10:10:10.000-07:00
        ScheduleContext noMinContext = getContext(PST, NOW.plusWeeks(2));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, noMinContext);
        assertTrue(scheduledActivities.size() > 1);
        
        ScheduleContext minContext = new ScheduleContext.Builder()
                .withContext(noMinContext)
                .withMinimumPerSchedule(1).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, minContext);
        assertTrue(scheduledActivities.size() > 1);
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return getContext(PST, endsOn);
    }

    private ScheduleContext getContext(DateTimeZone zone, DateTime endsOn) {
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(zone).withEndsOn(endsOn).withHealthCode("healthCode").withEvents(events).build();
    }
    
}
