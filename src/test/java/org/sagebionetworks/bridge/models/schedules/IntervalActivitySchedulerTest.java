package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asLong;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

public class IntervalActivitySchedulerTest {

    private static final DateTime NOW = DateTime.parse("2015-03-26T14:40:00-07:00");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    private Map<String, DateTime> events;
    private List<ScheduledActivity> scheduledActivities;
    private SchedulePlan plan = new DynamoSchedulePlan();
    
    @BeforeMethod
    public void before() {
        plan.setGuid("BBB");

        // Day of tests is 2015-04-06T10:10:10.000-07:00 for purpose of calculating expiration
        DateTimeUtils.setCurrentMillisFixed(1428340210000L);
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void canSpecifyASequence() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.setInterval("P1D");
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P3D");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusWeeks(2))
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // three days of activities from enrollment
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00", "2015-03-25 14:00");
        
        // delay one day, then one day period, you get two (the first and the second which is in the day
        schedule.setSequencePeriod("P1D");
        schedule.setInterval("P1D");
        schedule.setDelay("P1D");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertDates(scheduledActivities, PST, "2015-03-24 14:00");
    }
    
    @Test
    public void sequenceCanBeOverriddenByMinCount() {
        // The min count should retrieve more than N days of scheduled activities, but not more
        // that would be specified in the sequence period.
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P6D");
        schedule.setInterval("P1D");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusDays(4))
            .withMinimumPerSchedule(8)
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // Period is 6 days, you ask for 4 days ahead, but insist on 8 tasks. You should get back
        // 6 tasks... all the tasks in the period. But not 8 activities.
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00", "2015-03-25 14:00",
                "2015-03-26 14:00", "2015-03-27 14:00", "2015-03-28 14:00");
    }
    
    @Test
    public void sequenceShorterThanDaysAhead() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.addTimes("14:00");
        schedule.setSequencePeriod("P2D");
        schedule.setInterval("P1D");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusDays(4))
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // 2 activities
        assertDates(scheduledActivities, PST, "2015-03-23 14:00", "2015-03-24 14:00");
    }
    
    @Test
    public void sequenceWithTwoTimesProducesDoubleTheActivities() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(RECURRING);
        schedule.addTimes("08:00", "14:00");
        schedule.setSequencePeriod("P3D");
        schedule.setInterval("P1D");
        schedule.addActivity(TestUtils.getActivity3());
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(PST)
            .withEndsOn(NOW.plusWeeks(2))
            .withEvents(events).build();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // We've asked for a period of 3 days, we get 6 tasks, 2 for each day on the two times.
        assertDates(scheduledActivities, PST, "2015-03-23 08:00", "2015-03-23 14:00", "2015-03-24 08:00",
                "2015-03-24 14:00", "2015-03-25 08:00", "2015-03-25 14:00");
    }
    
    @Test
    public void oneWeekAfterEnrollmentAt8amExpireAfter24hours() throws Exception {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay("P1W");
        schedule.addTimes("08:00");
        schedule.setExpires("PT24H");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
        
        schedule.setExpires("P1M");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-30 08:00");
    }
    
    @Test
    public void onceWithoutTimesUsesLocalTime() throws Exception {
        Schedule schedule = new Schedule();
        schedule.addActivity(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setExpires("P2M");
        
        ScheduleContext context= new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(DateTimeZone.forOffsetHours(-7))
                .withEndsOn(ENROLLMENT.plusDays(2)) // in UTC
                .withHealthCode("AAA")
                .withEvents(events).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        // Date is expressed in local time UTC, because that's the endsOn time zone.
        assertDates(scheduledActivities, DateTimeZone.UTC, "2015-03-23T03:00");
    }
    
    @Test
    public void onceScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertDates(scheduledActivities, "2015-03-23 09:40");
    }
    @Test
    public void onceStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn("2015-04-10T09:00:00Z");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEndsOn("2015-03-21T10:00:00Z");
        
        // In this case the endsOn date is before the enrollment. No activities
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
        
        schedule.setEndsOn("2015-04-23T13:40:00Z");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-23 09:40");
    }
    @Test
    public void onceStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn("2015-03-23T10:00:00Z");
        schedule.setEndsOn("2015-03-26T10:00:00Z");
        
        // Should get the second of the activities scheduled on day of enrollment
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-23 13:40");
    }
    @Test
    public void onceDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(3)));
        assertDates(scheduledActivities, "2015-03-25 09:40");
    }
    @Test
    public void onceDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        schedule.setStartsOn("2015-03-27T00:00:00Z");
        
        // Again, it happens before the start date, so it doesn't happen.
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(3)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        schedule.setEndsOn(asDT("2015-04-04 10:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(3)));
        assertDates(scheduledActivities, "2015-03-25 09:40");
        
        // With a delay *after* the end date, nothing happens
        schedule.setDelay("P2M");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2M");
        schedule.setStartsOn(asDT("2015-03-20 00:00"));
        schedule.setEndsOn(asDT("2015-06-01 10:00"));
        
        // Schedules in the window without any issue
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        assertDates(scheduledActivities, "2015-05-23 09:40");
        
        schedule.setDelay("P6M");
        schedule.setStartsOn(asDT("2015-05-01 00:00"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(9)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceDelayExpiresScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setExpires("P1W");
        schedule.setDelay("P1M");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        assertEquals(scheduledActivities.get(0).getScheduledOn().getMillis(), asLong("2015-04-23 09:40"));
        assertEquals(scheduledActivities.get(0).getExpiresOn().getMillis(), asLong("2015-04-30 09:40"));
    }
    @Test
    public void onceExpiresScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setExpires("P1W");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        // No activities. Created on 3/23, it expired by 3/30, today is 4/6
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceEventScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        events.put("survey:AAA:completedOn", asDT("2015-04-10 11:40"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-10 09:40");
        
        schedule.getTimes().clear();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-10 11:40");
    }
    @Test
    public void onceEventStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-03-29 11:40"));

        // Goes to the event window day and takes the afternoon slot, after 10am
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceEventEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setEndsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        // No activity, the event happened after the end of the window
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceEventStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-01 10:00"));
        schedule.setEndsOn(asDT("2015-05-01 10:00"));

        // No event... select the startsOn window
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
        
        events.put("survey:AAA:completedOn", asDT("2015-04-10 00:00"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-10 09:40");
    }
    @Test
    public void onceEventDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-06 09:40");
        
        // This delays after the event by ~2 days, but then uses the supplied times
        schedule.setDelay("PT50H");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-04 09:40");
        
        // If we delete the times, it delays exactly 50 hours. (2 days, 2 hours)
        schedule.getTimes().clear();
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-04 11:22");
    }
    @Test
    public void onceEventDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));

        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-06 09:40");
        
        // This should not return a activity.
        schedule.setStartsOn(asDT("2015-04-15 00:00"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void onceEventDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-06 09:40");
    }
    @Test
    public void onceEventDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("PT3H");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        schedule.getTimes().clear();
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(6)));
        // Pulled back to yesterday midnight to avoid TZ changes causing activity to be unavailable
        assertDates(scheduledActivities, "2015-04-02 12:22");
    }

    @Test
    public void onceScheduleWithMultipleEventsOnlyReturnsOneActivity() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setTimes(ImmutableList.of(LocalTime.parse("06:00")));
        schedule.setInterval("P1D");
        schedule.setEventId("two_weeks_before_enrollment,enrollment");

        events.put("two_weeks_before_enrollment", ENROLLMENT.minusWeeks(2));

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan,
                getContext(ENROLLMENT.plusDays(14)));
        assertDates(scheduledActivities, "2015-03-09 06:00");
    }

    @Test
    public void onceEventDelayExpiresStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("PT3H");
        schedule.setStartsOn(asDT("2015-03-29 00:00"));
        schedule.setEndsOn(asDT("2015-04-29 00:00"));
        schedule.setExpires("P3D");
        schedule.getTimes().clear();
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        // Given even, it would be on 4/2 at 12:22, expiring 4/5, today is 4/6
        // It's in the window but still doesn't appear
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(6)));
        assertEquals(scheduledActivities.size(), 0);
        
        events.put("survey:AAA:completedOn", asDT("2015-04-06 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(6)));
        
        assertEquals(scheduledActivities.get(0).getScheduledOn().getMillis(), asLong("2015-04-06 12:22"));
        assertEquals(scheduledActivities.get(0).getExpiresOn().getMillis(), asLong("2015-04-09 12:22"));
    }
    @Test
    public void recurringScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(3)));
        
        assertDates(scheduledActivities, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40");
    }
    @Test
    public void recurringStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setStartsOn("2015-03-20T09:00:00Z");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(4)));
        
        assertDates(scheduledActivities, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40");
    }
    @Test
    public void recurringEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEndsOn("2015-03-25T10:00:00Z"); // between the two times
        
        // In this case the endsOn date is before the enrollment. No activities
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(11)));
        assertDates(scheduledActivities, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40");
        
        schedule.setEndsOn("2015-03-27T13:50:00Z");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(11)));
        assertDates(scheduledActivities, "2015-03-23 09:40", "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40",
                        "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setStartsOn("2015-03-23T10:00:00Z");
        schedule.setEndsOn("2015-03-27T20:00:00Z");
        
        // Should get the second of the activities scheduled on day of enrollment
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-03-23 13:40", "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(5)));
        assertDates(scheduledActivities, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");
        schedule.setStartsOn("2015-03-22T23:49:00Z");
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(5)));
        assertDates(scheduledActivities, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40");
    }
    @Test
    public void recurringDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P2D");
        schedule.setEndsOn(asDT("2015-04-05 10:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertDates(scheduledActivities, "2015-03-25 09:40", "2015-03-25 13:40", "2015-03-27 09:40", "2015-03-27 13:40",
                        "2015-03-29 09:40", "2015-03-29 13:40");
        
        // With a delay *after* the end date, nothing happens
        schedule.setDelay("P2M");
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void recurringDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setDelay("P1D");
        schedule.setStartsOn(asDT("2015-03-22 00:00"));
        schedule.setEndsOn(asDT("2015-03-30 10:00"));
        
        // Schedules in the window without any issue
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(9)));
        assertDates(scheduledActivities, "2015-03-24 09:40", "2015-03-24 13:40", "2015-03-26 09:40", "2015-03-26 13:40",
                        "2015-03-28 09:40", "2015-03-28 13:40", "2015-03-30 09:40");
        
        // Schedule before, rolls forward
        schedule.setDelay("P1M");
        schedule.setStartsOn(asDT("2015-03-30 09:00"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(3)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void recurringEventScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        events.put("survey:AAA:completedOn", asDT("2015-04-10 11:40"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        assertDates(scheduledActivities, "2015-04-10 09:40", "2015-04-10 13:40", "2015-04-12 09:40", "2015-04-12 13:40");
    }
    @Test
    public void recurringEventStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-04-11 00:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-12 11:40"));

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        assertDates(scheduledActivities, "2015-04-12 09:40", "2015-04-12 13:40");
    }
    @Test
    public void recurringEventEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setEndsOn(asDT("2015-04-01 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        // No activity, the event happened after the end of the window
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertEquals(scheduledActivities.size(), 0);
    }
    @Test
    public void recurringEventStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setStartsOn(asDT("2015-03-30 10:00"));
        schedule.setEndsOn(asDT("2015-04-05 10:00"));
        events.put("survey:AAA:completedOn", asDT("2015-04-02 00:00"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        
        assertDates(scheduledActivities, "2015-04-02 09:40", "2015-04-02 13:40", "2015-04-04 09:40", "2015-04-04 13:40");
    }
    @Test
    public void recurringEventDelayScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(16)));
        assertDates(scheduledActivities, "2015-04-06 09:40", "2015-04-06 13:40", "2015-04-08 09:40", "2015-04-08 13:40");
    }
    @Test
    public void recurringEventDelayStartsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setStartsOn(asDT("2015-04-10 00:00"));

        // The delay doesn't mean the schedule fires on this event
        events.put("survey:AAA:completedOn", asDT("2015-04-01 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        assertDates(scheduledActivities, "2015-04-11 09:40", "2015-04-11 13:40", "2015-04-13 09:40", "2015-04-13 13:40");
    }
    @Test
    public void recurringEventDelayEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P4D");
        schedule.setEndsOn(asDT("2015-04-08 00:00"));
        
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusMonths(1)));
        assertDates(scheduledActivities, "2015-04-06 09:40", "2015-04-06 13:40");
    }
    @Test
    public void recurringEventDelayStartEndsOnScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P1D");
        schedule.setStartsOn(asDT("2015-04-07 00:00"));
        schedule.setEndsOn(asDT("2015-04-10 00:00"));
        
        // This is outside the window, so when this happens, even if it recurs, it shouldn't fire
        events.put("survey:AAA:completedOn", asDT("2015-04-02 09:22"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        
        assertDates(scheduledActivities, 
                "2015-04-07 09:40", "2015-04-07 13:40", "2015-04-09 09:40", "2015-04-09 13:40");
    }

    @Test
    public void recurringScheduleWithNoEvents() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setInterval("P1D");
        schedule.setEventId("non-existent-event");

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusDays(4)));
        assertTrue(scheduledActivities.isEmpty());
    }

    @Test
    public void recurringScheduleWithMultipleEvents() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setTimes(ImmutableList.of(LocalTime.parse("06:00")));
        schedule.setInterval("P1D");
        schedule.setSequencePeriod("P3D");
        schedule.setEventId("two_weeks_before_enrollment,enrollment");

        events.put("two_weeks_before_enrollment", ENROLLMENT.minusWeeks(2));

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan,
                getContext(ENROLLMENT.plusDays(14)));
        assertDates(scheduledActivities, "2015-03-09 06:00", "2015-03-10 06:00", "2015-03-11 06:00",
                "2015-03-23 06:00", "2015-03-24 06:00", "2015-03-25 06:00");
    }

    // This is a specific scenario in one of our studies and I wanted to have a test specifically to verify this works.
    @Test
    public void oneDayDelayWithTimesSchedulesTheNextDayAfterAnEvent() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity3());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setEventId("survey:AAA:completedOn");
        schedule.setDelay("P1D");
        schedule.setExpires("PT2H");
        schedule.addTimes("08:00", "14:00", "20:00");

        // This event happens late in the day on 4/6, we want tasks for the next day.
        events.put("survey:AAA:completedOn", asDT("2015-04-06 22:32"));
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, getContext(ENROLLMENT.plusWeeks(3)));
        
        assertDates(scheduledActivities, 
                "2015-04-07 08:00", "2015-04-07 14:00", "2015-04-07 20:00");
    }
    
    // This verifies that the minimum tasks per schedule setting will work correctly even if 
    // a time window has been set for the schedule... that we don't fall into an infinite loop, 
    // but the window still limits tasks (because that's the only thing it's really good for is 
    // placing a hard stop on an app's scheduling).
    @Test
    public void recurringWithWindowAndMinTasksWorks() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P5D");
        schedule.setInterval("P1W");
        schedule.addTimes("08:00");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withContext(getContext(ENROLLMENT))
                .withMinimumPerSchedule(2).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertEquals(scheduledActivities.size(), 2);
        
        // But this will lock out tasks. Scheduling window takes precedence.
        schedule.setEndsOn(ENROLLMENT.plusDays(3)); 
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertTrue(scheduledActivities.isEmpty());
    }

    // In these next two tasks, the different times of day should not alter the fact that there 
    // are 4 tasks that are returned. However they do, and this will be fixed in a later 
    // reworking of the scheduler.
    
    @Test
    public void eventIsEarlyUTC() {
        DateTime enrollment = DateTime.parse("2015-04-04T04:00:00.000Z");
        events.clear();
        events.put("enrollment", enrollment);
        
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        schedule.setInterval("P1D");
        schedule.addTimes("10:00");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withContext(getContext(DateTime.parse("2015-04-10T13:00:00.000Z")))
                .withInitialTimeZone(DateTimeZone.forOffsetHours(-7)).build();

        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        
        assertEquals(scheduledActivities.size(), 5);
    }
    
    @Test
    public void eventIsLateUTC() {
        DateTime enrollment = DateTime.parse("2015-04-04T22:00:00.000Z");
        events.clear();
        events.put("enrollment", enrollment);
        
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setExpires("P1D");
        schedule.setInterval("P1D");
        schedule.addTimes("10:00");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withContext(getContext(DateTime.parse("2015-04-10T13:00:00.000Z")))
                .withInitialTimeZone(DateTimeZone.forOffsetHours(-7)).build();
        
        scheduledActivities = schedule.getScheduler().getScheduledActivities(plan, context);
        assertEquals(scheduledActivities.size(), 4);
    }

    private ScheduleContext getContext(DateTime endsOn) {
        return getContext(DateTimeZone.UTC, endsOn);
    }
    
    private ScheduleContext getContext(DateTimeZone timeZone, DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withInitialTimeZone(timeZone)
            .withEndsOn(endsOn)
            .withHealthCode("AAA")
            .withEvents(events).build();
    }
    
    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        schedule.addTimes("09:40", "13:40");
        schedule.getActivities().add(TestUtils.getActivity3());
        schedule.setScheduleType(type);
        if (type == RECURRING) {
            schedule.setInterval("P2D");
        }
        return schedule;
    }
    
}
