package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.SEQUENTIAL;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.PerformanceOrder;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.validators.Schedule2Validator;
import org.sagebionetworks.bridge.validators.Validate;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class StudyAdherenceReportGeneratorTest extends Mockito {
    
    @Test
    public void scheduleValid() throws Exception {
        AdherenceState state = createAdherenceState();
        
        StudyAdherenceReport report = StudyAdherenceReportGenerator.INSTANCE.generate(state);
        
        System.out.println(BridgeObjectMapper.get().writeValueAsString(report));
    }
    
    @Test
    public void rowsSortedByBurstIdAndThenLabel() {
        
    }
    
    @Test
    public void studyStartDateBasedOnStudyStartEventId() {
        
    }
    
    @Test
    public void studyStartDateBasedOnDefault() { 
        
    }
    
    @Test
    public void studyStartDateBasedOnDateRangeStart() { 
        
    }
    
    @Test
    public void emptyEventStreamReportGeneratesEmptyStudyAdherenceReport() {
        
    }

    @Test
    public void eventStreamsMappedToSingleStudyStream() {
        
    }
    
    @Test
    public void eventStreamsWithNoEventsTransferredToUnused() {
        
    }
    
    @Test
    public void todaySet() {
        
    }
    
    @Test
    public void timestampAfterSchedule() {
        
    }
    
    @Test
    public void timestampBeforeSchedule() {
        
    }
    
    @Test
    public void notimestampForSchedule() {
        
    }
    
    @Test
    public void timestampOnFallowWeek() {
        
    }
    
    @Test
    public void participantStudyProgress_unstarted() {
        
    }
    
    @Test
    public void participantStudyProgress_inProgress() {
        
    }

    @Test
    public void participantStudyProgress_done() {
        
    }

    @Test
    public void participantStudyProgress_noSchedule() {
        
    }
    
    @Test
    public void dayHasNoUnnecessaryFieldsFilledOut() {
        
    }

    @Test
    public void dateRangeIsCorrect() {
        
    }

    @Test
    public void currentWeekIsCorrect() {
        
    }
    
    @Test
    public void eventTimestampsArePresent() {
        
    }
    
    @Test
    public void studyStreamBrokenIntoWeeks() {
        
    }
    
    @Test
    public void week_correct() {
        
    }
    
    @Test
    public void week_startDateInWeekCorrect() {
        
    }
    
    @Test
    public void week_daysAddedToCorrectDayInWeek() {
        
    }
    
    @Test
    public void week_adherenceCorrect() {
        
    }
    
    @Test
    public void week_labelsAndRowsCorrect() {
        
    }
    
    private AdherenceState createAdherenceState() throws Exception {
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(createSchedule());
        
        // initial survey: QHNwIgndsm23mhOBCKOung
        AdherenceRecord rec1 = new AdherenceRecord();
        rec1.setInstanceGuid("QHNwIgndsm23mhOBCKOung");
        rec1.setStartedOn(DateTime.now());
        rec1.setFinishedOn(DateTime.now());
        
        // baseline tapping test: UEGEHjM-62JN1Hvf6VMxtQ
        AdherenceRecord rec2 = new AdherenceRecord();
        rec2.setInstanceGuid("UEGEHjM-62JN1Hvf6VMxtQ");
        rec2.setStartedOn(DateTime.now());
        rec2.setFinishedOn(DateTime.now());
        
        // study burst 1 tapping test: g0-ktzutxhFU9bWYXJfLJQ
        AdherenceRecord rec3 = new AdherenceRecord();
        rec3.setInstanceGuid("g0-ktzutxhFU9bWYXJfLJQ");
        rec3.setDeclined(true);
        // rec3.setStartedOn(DateTime.now());
        // rec3.setFinishedOn(DateTime.now());
        
        // study burst 2 tapping test: ZWFzGqjQucjHS2YM6wLmSQ
        AdherenceRecord rec4 = new AdherenceRecord();
        rec4.setInstanceGuid("ZWFzGqjQucjHS2YM6wLmSQ");
        rec4.setStartedOn(DateTime.now());
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(DateTime.parse("2022-03-01T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-03-10T16:23:15.999-08:00"))
                .build();
        AdherenceState state = new AdherenceState.Builder()
                .withAdherenceRecords(ImmutableList.of(rec1, rec2, rec3, rec4))
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(e1, e2))
                .withNow(DateTime.parse("2022-03-15T01:00:00.000-08:00"))
                .build();
        return state;
    }
    
    /**
     * This is not a super-complicated schedule, but I have mapped out the exact dates when
     * everything should occur for a timeline_retrieved event of 3/1, so I can verify the 
     * dates and such are correct after the generator executes.
     */
    private Schedule2 createSchedule() {
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
        s1.setGuid("session1");
        s1.setName("Session #1");
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
        s2.setGuid("session2");
        s2.setName("Session #2");
        s2.setPerformanceOrder(SEQUENTIAL);
        
        // Study Burst
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("burst");
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
        s3.setStudyBurstIds(ImmutableList.of("burst"));
        s3.setTimeWindows(ImmutableList.of(win3));
        s3.setGuid("session3");
        s3.setName("Session #3");
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
        s4.setGuid("session4");
        s4.setName("Session #4");
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
        s5.setName("Session #5");
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
}
