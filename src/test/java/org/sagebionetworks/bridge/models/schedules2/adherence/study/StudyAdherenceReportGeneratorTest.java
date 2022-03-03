package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.SEQUENTIAL;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.study.StudyAdherenceReportGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class StudyAdherenceReportGeneratorTest extends Mockito {
    
    public static StudyAdherenceReport createReport() throws Exception {
        AdherenceState state = createAdherenceState().build();
        return StudyAdherenceReportGenerator.INSTANCE.generate(state);
    }
    
    public static List<AdherenceRecord> createAdherenceRecords() {
        // initial survey
        AdherenceRecord rec1 = new AdherenceRecord();
        rec1.setInstanceGuid("pqVRM8cV-buumqQvUGwRsQ");
        rec1.setStartedOn(DateTime.now());
        rec1.setFinishedOn(DateTime.now());
        
        // baseline tapping test
        AdherenceRecord rec2 = new AdherenceRecord();
        rec2.setInstanceGuid("xyvAcmEYAVAzCMfGhf187g");
        rec2.setStartedOn(DateTime.now());
        rec2.setFinishedOn(DateTime.now());
        
        // study burst 1 tapping test: g0-ktzutxhFU9bWYXJfLJQ
        AdherenceRecord rec3 = new AdherenceRecord();
        rec3.setInstanceGuid("freUhgN8OBMQOuUJBY_b4Q");
        rec3.setDeclined(true);
        // rec3.setStartedOn(DateTime.now());
        // rec3.setFinishedOn(DateTime.now());
        
        // study burst 2 tapping test: ZWFzGqjQucjHS2YM6wLmSQ
        AdherenceRecord rec4 = new AdherenceRecord();
        rec4.setInstanceGuid("B01W5ru8Cjr8DAbODKcMKA");
        rec4.setStartedOn(DateTime.now());
        
        return ImmutableList.of(rec1, rec2, rec3, rec4);
    }
    
    public static List<TimelineMetadata> createTimelineMetadata() throws Exception {
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(createSchedule());
        return timeline.getMetadata();
    }
    
    public static List<StudyActivityEvent> createEvents() {
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(DateTime.parse("2022-03-01T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.TIMELINE_RETRIEVED)
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-03-10T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        
        // don't forget the study burst events! They are not calculated at the time
        // the schedule is calculated!
        StudyActivityEvent e3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:01")
                .withTimestamp(DateTime.parse("2022-03-08T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:02")
                .withTimestamp(DateTime.parse("2022-03-15T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e5 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:03")
                .withTimestamp(DateTime.parse("2022-03-22T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        return ImmutableList.of(e1, e2, e3, e4, e5);
    }
    
    public static AdherenceState.Builder createAdherenceState() throws Exception {
        return new AdherenceState.Builder()
                .withStudyStartEventId("timeline_retrieved")
                .withMetadata(createTimelineMetadata())
                .withEvents(createEvents())
                .withNow(DateTime.parse("2022-03-15T01:00:00.000-08:00"));
    }
    
    /**
     * This is not a super-complicated schedule, but I have mapped out the exact dates when
     * everything should occur for a timeline_retrieved event of 3/1, so I can verify the 
     * dates and such are correct after the generator executes.
     */
    public static Schedule2 createSchedule() {
        // survey
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        // tapping test
        AssessmentReference ref2 = new AssessmentReference();
        ref2.setGuid("tappingTest");
        ref2.setAppId(TEST_APP_ID);
        ref2.setIdentifier("tappingTest");
        
        // final survey
        AssessmentReference ref3 = new AssessmentReference();
        ref3.setGuid("finalSurvey");
        ref3.setAppId(TEST_APP_ID);
        ref3.setIdentifier("finalSurvey");
        
        Schedule2 schedule = new Schedule2();
        
        // Initial survey
        TimeWindow win1 = new TimeWindow();
        win1.setGuid("win1");
        win1.setStartTime(LocalTime.parse("00:00"));
        win1.setExpiration(Period.parse("P1D"));
        
        Session s1 = new Session();
        s1.setAssessments(ImmutableList.of(ref1));
        s1.setDelay(Period.parse("P1D"));
        s1.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s1.setTimeWindows(ImmutableList.of(win1));
        s1.setGuid("initialSurveyGuid");
        s1.setName("Initial Survey");
        s1.setPerformanceOrder(SEQUENTIAL);
        
        // Baseline tapping test
        TimeWindow win2 = new TimeWindow();
        win2.setGuid("win2");
        win2.setStartTime(LocalTime.parse("00:00"));
        win2.setExpiration(Period.parse("P1D"));
        
        Session s2 = new Session();
        s2.setAssessments(ImmutableList.of(ref2));
        s2.setDelay(Period.parse("P2D"));
        s2.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s2.setTimeWindows(ImmutableList.of(win2));
        s2.setGuid("baselineGuid");
        s2.setName("Baseline Tapping Test");
        s2.setPerformanceOrder(SEQUENTIAL);
        
        // Study Burst
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("Study Burst");
        burst.setOriginEventId("timeline_retrieved");
        burst.setUpdateType(IMMUTABLE);
        burst.setDelay(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setInterval(Period.parse("P1W"));

        TimeWindow win3 = new TimeWindow();
        win3.setGuid("win3");
        win3.setStartTime(LocalTime.parse("00:00"));
        win3.setExpiration(Period.parse("P1D"));

        Session s3 = new Session();
        s3.setAssessments(ImmutableList.of(ref2));
        s3.setStudyBurstIds(ImmutableList.of("Study Burst"));
        s3.setTimeWindows(ImmutableList.of(win3));
        s3.setGuid("burstTappingGuid");
        s3.setName("Study Burst Tapping Test");
        s3.setPerformanceOrder(SEQUENTIAL);
        
        // Final survey
        TimeWindow win4 = new TimeWindow();
        win4.setGuid("win4");
        win4.setStartTime(LocalTime.parse("00:00"));
        win4.setExpiration(Period.parse("P3D"));
        
        Session s4 = new Session();
        s4.setAssessments(ImmutableList.of(ref3));
        s4.setDelay(Period.parse("P24D"));
        s4.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s4.setTimeWindows(ImmutableList.of(win4));
        s4.setGuid("finalSurveyGuid");
        s4.setName("Final Survey");
        s4.setPerformanceOrder(SEQUENTIAL);
        
        // Supplemental survey that does not fire for our test user
        TimeWindow win5 = new TimeWindow();
        win5.setGuid("win5");
        win5.setStartTime(LocalTime.parse("00:00"));
        win5.setExpiration(Period.parse("PT12H"));

        Session s5 = new Session(); 
        s5.setAssessments(ImmutableList.of(ref1));
        s5.setStartEventIds(ImmutableList.of("custom:event1"));
        s5.setTimeWindows(ImmutableList.of(win5));
        s5.setGuid("session5");
        s5.setName("Supplemental Survey");
        s5.setPerformanceOrder(SEQUENTIAL);

        schedule.setSessions(ImmutableList.of(s1, s2, s3, s4, s5));
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setName("Test Schedule");
        schedule.setOwnerId("sage-bionetworks");
        schedule.setDuration(Period.parse("P4W"));
        schedule.setStudyBursts(ImmutableList.of(burst));
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
        
        return schedule;
    }
    
    @Test
    public void rowsSortedByBurstIdAndThenLabel() throws Exception {
        StudyAdherenceReport report = createReport();
        
        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        List<WeeklyAdherenceReportRow> rows = weeks.get(0).getRows();
        
        assertEquals(rows.get(0).getLabel(), "Baseline Tapping Test / Week 1");
        assertEquals(rows.get(0).getSessionName(), "Baseline Tapping Test");
        assertEquals(rows.get(1).getLabel(), "Initial Survey / Week 1");
        assertEquals(rows.get(1).getSessionName(), "Initial Survey");
        
        // elements are in the correct order:
        assertEquals(weeks.get(0).getByDayEntries().get(1).get(1).getSessionGuid(), "initialSurveyGuid");
        assertEquals(weeks.get(0).getByDayEntries().get(2).get(0).getSessionGuid(), "baselineGuid");
        
        // burst comes first here despite alphabetizing
        rows = weeks.get(3).getRows();
        assertEquals(rows.get(0).getLabel(), "Study Burst 3 / Week 4 / Study Burst Tapping Test");
        assertEquals(rows.get(0).getSessionName(), "Study Burst Tapping Test");
        assertEquals(rows.get(1).getLabel(), "Final Survey / Week 4");
        assertEquals(rows.get(1).getSessionName(), "Final Survey");
        
        assertEquals(weeks.get(3).getByDayEntries().get(0).get(0).getSessionGuid(), "burstTappingGuid");
        assertEquals(weeks.get(3).getByDayEntries().get(3).get(1).getSessionGuid(), "finalSurveyGuid");
    }
    
    @Test
    public void studyStartDateBasedOnStudyStartEventId() throws Exception {
        StudyAdherenceReport report = createReport();
        
        assertEquals(report.getDateRange().getStartDate().toString(), "2022-03-01");
    }
    
    @Test
    public void studyStartDateBasedOnEarliestEvent() throws Exception { 
        AdherenceState.Builder builder = createAdherenceState();
        builder.withStudyStartEventId(null);
        StudyAdherenceReport report = INSTANCE.generate(builder.build());
        
        assertEquals(report.getDateRange().getStartDate().toString(), "2022-03-01");
    }
    
    @Test
    public void emptyEventStreamReportGeneratesEmptyStudyAdherenceReport() throws Exception {
        AdherenceState state = new AdherenceState.Builder()
                .withNow(DateTime.parse("2022-03-01T00:00:00.000-08:00")).build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        // these fields are not set in the generator, they are set in the service.
        // participant, testAccount, createdOn, timestamp, clientTimeZone
        
        assertEquals(report.getProgression(), ParticipantStudyProgress.NO_SCHEDULE);
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of());
        assertEquals(report.getUnscheduledSessions(), ImmutableSet.of());
        assertTrue(report.getWeeks().isEmpty());
        assertNull(report.getCurrentWeek());
        assertNull(report.getNextActivity());
        assertTrue(report.getEventTimestamps().isEmpty());
    }

    @Test
    public void eventStreamsWithNoEventsTransferredToUnused() throws Exception {
        AdherenceState state = createAdherenceState().withEvents(null).build();
        
        StudyAdherenceReport report = INSTANCE.generate(state);
        assertTrue(report.getWeeks().isEmpty());
        assertTrue(report.getEventTimestamps().isEmpty());
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of("study_burst:Study Burst:02",
                "study_burst:Study Burst:01", "study_burst:Study Burst:03", "custom:event1", "timeline_retrieved"));
        assertEquals(report.getUnscheduledSessions(), ImmutableSet.of("Supplemental Survey", "Initial Survey",
                "Baseline Tapping Test", "Study Burst Tapping Test", "Final Survey"));
    }
    
    @Test
    public void todaySet() throws Exception {
        AdherenceState state = createAdherenceState().build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    if (oneDay.isToday()) {
                        assertEquals(oneDay.getStartDate().toString(), "2022-03-15");
                    } else if (oneDay.getStartDate() != null) {
                        assertNotEquals(oneDay.getStartDate().toString(), "2022-03-15");
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampAfterSchedule() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2022-05-01T00:00:00.000-08:00")).build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    for (EventStreamWindow win : oneDay.getTimeWindows()) {
                        assertEquals(win.getState(), EXPIRED);
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampBeforeSchedule() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2020-01-01T00:00:00.000-08:00")).build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    for (EventStreamWindow win : oneDay.getTimeWindows()) {
                        assertEquals(win.getState(), NOT_YET_AVAILABLE);
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampOnFallowWeek() throws Exception {
        // To make this work we have to create a fallow week, by delaying the
        // study burst for 4 weeks.
        Schedule2 schedule = createSchedule();
        schedule.getStudyBursts().get(0).setDelay(Period.parse("P4W"));
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(DateTime.parse("2022-03-01T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-03-10T16:23:15.999-08:00"))
                .build();
        
        // don't forget the study burst events! They are not calculated at the time
        // the schedule is calculated!
        StudyActivityEvent e3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:01")
                .withTimestamp(DateTime.parse("2022-03-29T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:02")
                .withTimestamp(DateTime.parse("2022-04-05T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e5 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:03")
                .withTimestamp(DateTime.parse("2022-04-12T16:23:15.999-08:00"))
                .build();
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        AdherenceState state = createAdherenceState()
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(e1, e2, e3, e4, e5))
                .build();
        
        // this now has a gap and the 15th (now) falls into it
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        assertEquals(weeks.get(0).getWeek(), 1);
        assertEquals(weeks.get(0).getStartDate().toString(), "2022-03-01");
        assertEquals(weeks.get(1).getWeek(), 4);
        assertEquals(weeks.get(1).getStartDate().toString(), "2022-03-22");
        
        // Nothing is "now" because that's in the gap.
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    assertFalse(oneDay.isToday());
                }
            }
        }
        assertEquals(report.getNextActivity().getSessionGuid(), "finalSurveyGuid");
        assertEquals(report.getNextActivity().getSessionName(), "Final Survey");
        assertEquals(report.getNextActivity().getWeek(), Integer.valueOf(4));
        assertEquals(report.getNextActivity().getStartDate().toString(), "2022-03-25");
    }
    
    @Test
    public void participantStudyProgress_unstarted() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2020-01-01T00:00:00.000-08:00")).build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
    }
    
    @Test
    public void participantStudyProgress_inProgress() throws Exception {
        StudyAdherenceReport report = createReport();
        
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
    }

    @Test
    public void participantStudyProgress_done() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2022-05-01T00:00:00.000-08:00")).build();
        StudyAdherenceReport report = INSTANCE.generate(state);

        assertEquals(report.getProgression(), ParticipantStudyProgress.DONE);
    }

    @Test
    public void participantStudyProgress_noSchedule() throws Exception {
        AdherenceState state = createAdherenceState()
                .withMetadata(null).build();
        StudyAdherenceReport report = INSTANCE.generate(state);

        assertEquals(report.getProgression(), ParticipantStudyProgress.NO_SCHEDULE);
    }
    
    @Test
    public void dayHasNoUnnecessaryFieldsFilledOut() throws Exception {
        StudyAdherenceReport report = createReport();
        
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    assertNull(oneDay.getStudyBurstId());
                    assertNull(oneDay.getStudyBurstNum());
                    assertNull(oneDay.getSessionName());
                    assertNull(oneDay.getWeek());
                    assertNull(oneDay.getStartDay());
                    for (EventStreamWindow window : oneDay.getTimeWindows()) {
                        assertNull(window.getEndDay());
                    }
                }
            }
        }
    }

    @Test
    public void dateRangeIsCorrect() throws Exception {
        StudyAdherenceReport report = createReport();
        
        assertEquals(report.getDateRange().getStartDate().toString(), "2022-03-01");
        assertEquals(report.getDateRange().getEndDate().toString(), "2022-03-27");
    }

    @Test
    public void currentWeekIsCorrect() throws Exception {
        StudyAdherenceReport report = createReport();
        assertSame(report.getCurrentWeek(), ImmutableList.copyOf(report.getWeeks()).get(2));
    }
    
    @Test
    public void eventTimestampsArePresent() throws Exception {
        StudyAdherenceReport report = createReport();
        
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:01").toString(), 
                "2022-03-08T16:23:15.999-08:00");
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:02").toString(), 
                "2022-03-15T16:23:15.999-08:00");
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:03").toString(), 
                "2022-03-22T16:23:15.999-08:00");
        assertEquals(report.getEventTimestamps().get("timeline_retrieved").toString(), 
                "2022-03-01T16:23:15.999-08:00");
    }
    
    @Test
    public void week_correct() throws Exception {
        AdherenceState state = createAdherenceState()
                .withAdherenceRecords(createAdherenceRecords()).build();
        StudyAdherenceReport report = INSTANCE.generate(state);
        
        StudyReportWeek week = report.getWeeks().iterator().next();
        assertEquals(week.getSearchableLabels(),
                ImmutableSet.of(":Initial Survey:Week 1:", ":Baseline Tapping Test:Week 1:"));
        assertEquals(week.getWeek(), 1);
        assertEquals(week.getStartDate().toString(), "2022-03-01");
        assertEquals(week.getAdherencePercent(), Integer.valueOf(100));
        assertEquals(week.getByDayEntries().get(1).get(1).getTimeWindows().get(0).getState(), COMPLETED);
        assertEquals(week.getByDayEntries().get(2).get(0).getTimeWindows().get(0).getState(), COMPLETED);
    }
    
    @Test
    public void week_daysAddedToCorrectDayInWeek() throws Exception {
        StudyAdherenceReport report = createReport();

        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        // we just know this from calculating the report on paper:
        assertFalse(hasActivities(weeks.get(0).getByDayEntries().get(0)));
        assertTrue(hasActivities(weeks.get(0).getByDayEntries().get(1)));
        assertTrue(hasActivities(weeks.get(0).getByDayEntries().get(2)));
        assertFalse(hasActivities(weeks.get(0).getByDayEntries().get(3)));
        assertFalse(hasActivities(weeks.get(0).getByDayEntries().get(4)));
        assertFalse(hasActivities(weeks.get(0).getByDayEntries().get(5)));
        assertFalse(hasActivities(weeks.get(0).getByDayEntries().get(6)));
        
        assertTrue(hasActivities(weeks.get(1).getByDayEntries().get(0)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(1)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(2)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(3)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(4)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(5)));
        assertFalse(hasActivities(weeks.get(1).getByDayEntries().get(6)));
        
        assertTrue(hasActivities(weeks.get(2).getByDayEntries().get(0)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(1)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(2)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(3)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(4)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(5)));
        assertFalse(hasActivities(weeks.get(2).getByDayEntries().get(6)));

        assertTrue(hasActivities(weeks.get(3).getByDayEntries().get(0)));
        assertFalse(hasActivities(weeks.get(3).getByDayEntries().get(1)));
        assertFalse(hasActivities(weeks.get(3).getByDayEntries().get(2)));
        assertTrue(hasActivities(weeks.get(3).getByDayEntries().get(3)));
        assertFalse(hasActivities(weeks.get(3).getByDayEntries().get(4)));
        assertFalse(hasActivities(weeks.get(3).getByDayEntries().get(5)));
        assertFalse(hasActivities(weeks.get(3).getByDayEntries().get(6)));
    }
    
    private boolean hasActivities(List<EventStreamDay> days) {
        for (EventStreamDay oneDay : days) {
            if (!oneDay.getTimeWindows().isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    @Test
    public void week_labelsAndRowsCorrect() throws Exception {
        StudyAdherenceReport report = createReport();
        
        StudyReportWeek week = report.getWeeks().iterator().next();
        WeeklyAdherenceReportRow row1 = week.getRows().get(0);
        assertEquals(row1.getLabel(), "Baseline Tapping Test / Week 1");
        assertEquals(row1.getSearchableLabel(), ":Baseline Tapping Test:Week 1:");
        assertEquals(row1.getSessionGuid(), "baselineGuid");
        assertEquals(row1.getStartEventId(), "timeline_retrieved");
        assertEquals(row1.getSessionName(), "Baseline Tapping Test");
        assertEquals(row1.getWeek(), Integer.valueOf(1));
        
        WeeklyAdherenceReportRow row2 = week.getRows().get(1);
        assertEquals(row2.getLabel(), "Initial Survey / Week 1");
        assertEquals(row2.getSearchableLabel(), ":Initial Survey:Week 1:");
        assertEquals(row2.getSessionGuid(), "initialSurveyGuid");
        assertEquals(row2.getStartEventId(), "timeline_retrieved");
        assertEquals(row2.getSessionName(), "Initial Survey");
        assertEquals(row2.getWeek(), Integer.valueOf(1));
    }
}
