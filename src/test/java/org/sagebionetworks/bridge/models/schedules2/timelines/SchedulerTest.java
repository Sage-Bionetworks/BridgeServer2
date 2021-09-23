package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_2_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_3_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_4_GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_3;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_4;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_3;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_4;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.SEQUENTIAL;
import static org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SchedulerTest extends Mockito {

    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void langKeyWhenCallerHasNoLanguages() {
        Schedule2 schedule = createSchedule(null);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getLang(), "en");
    }
    
    @Test
    public void langKeyWhenCallerHasNoLanguages2() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of()).build());
        Schedule2 schedule = createSchedule(null);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getLang(), "en");
    }
    
    @Test
    public void langKeyWhenCallerHasOneLanguage() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("ja")).build());
        Schedule2 schedule = createSchedule(null);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getLang(), "ja");
    }
    
    @Test
    public void langKeyWhenCallerHasMultipleLanguages() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("JA", "EN", "ES")).build());
        Schedule2 schedule = createSchedule(null);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getLang(), "ja,en,es");
    }
    
    @Test
    public void timelineLocalizedtoCallerSecondLanguage() {
        // This user should retrieve spanish since it consistently exists
        // in the schedule
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("ja", "es")).build());
        
        Schedule2 schedule = createSchedule(null);
        
        Session session = createOneTimeSession(null);
        session.setLabels(createLabels("de", "German", "es", "Spanish"));
        schedule.setSessions(ImmutableList.of(session));
        
        AssessmentReference asmt = createAssessmentRef(ASSESSMENT_1_GUID);
        asmt.setLabels(createLabels("de", "German", "es", "Spanish"));
        session.setAssessments(ImmutableList.of(asmt));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        // We know that given any user with these languages, the selection process
        // will yield the same results, even if mixed. So this timeline could be
        // cached under a key including this language combination.
        assertEquals(timeline.getLang(), "ja,es");
        assertEquals(timeline.getAssessments().get(0).getLabel(), "Spanish");
        assertEquals(timeline.getSessions().get(0).getLabel(), "Spanish");
    }
    
    @Test
    public void timelineUsesMixedLangWhenCallerLanguagesDoNotFullyMatch() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("ja", "es")).build());
        
        Schedule2 schedule = createSchedule(null);
        
        Session session = createOneTimeSession(null);
        session.setLabels(createLabels("de", "German", "es", "Spanish"));
        schedule.setSessions(ImmutableList.of(session));
        
        AssessmentReference asmt = createAssessmentRef(ASSESSMENT_1_GUID);
        asmt.setLabels(createLabels("ja", "Japanese", "es", "Spanish"));
        session.setAssessments(ImmutableList.of(asmt));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        // Three different languages, but each the best option available for this user.
        // (In reality, consistent localization would lead to consistent results.)
        assertEquals(timeline.getLang(), "ja,es");
        assertEquals(timeline.getAssessments().get(0).getLabel(), "Japanese");
        assertEquals(timeline.getSessions().get(0).getLabel(), "Spanish");
    }
    
    @Test
    public void timelineUsesEnglishWhenCallerHasNoLangauges() {
        Schedule2 schedule = createSchedule(null);
        
        Session session = createOneTimeSession(null);
        session.setLabels(createLabels("de", "German", "en", "English"));
        schedule.setSessions(ImmutableList.of(session));
        
        AssessmentReference asmt = createAssessmentRef(ASSESSMENT_1_GUID);
        asmt.setLabels(createLabels("en", "English", "es", "Spanish"));
        session.setAssessments(ImmutableList.of(asmt));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        // Here we fall back to the assessment title, the required English-language message and the 
        // session name, since Chinese values don’t exist.
        assertEquals(timeline.getLang(), "en");
        assertEquals(timeline.getAssessments().get(0).getLabel(), "English");
        assertEquals(timeline.getSessions().get(0).getLabel(), "English");
    }
    
    @Test
    public void timelineUsesFallbackValuesWhenLabelsMissing() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("zh")).build());
        
        Schedule2 schedule = createSchedule(null);
        
        Session session = createOneTimeSession(null);
        session.setLabels(createLabels("de", "German", "es", "Spanish"));
        schedule.setSessions(ImmutableList.of(session));
        
        AssessmentReference asmt = createAssessmentRef(ASSESSMENT_1_GUID);
        asmt.setLabels(createLabels("ja", "Japanese", "es", "Spanish"));
        session.setAssessments(ImmutableList.of(asmt));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        // Similar to above test of mixed values, but note that nothing breaks if there
        // is no suitable message (validators prevent this from happening however).
        assertEquals(timeline.getLang(), "zh");
        assertEquals(timeline.getAssessments().get(0).getLabel(), "Assessment 1");
        assertEquals(timeline.getSessions().get(0).getLabel(), "One Time Session");
    }
    
    @Test
    public void noSessions() throws Exception {
        Schedule2 schedule = createSchedule(null);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertTrue(timeline.getSessions().isEmpty());
        assertTrue(timeline.getAssessments().isEmpty());
        assertTrue(timeline.getSchedule().isEmpty());
    }
    
    @Test
    public void noAssessments() throws Exception {
        Schedule2 schedule = createComplexSchedule();
        for (Session session : schedule.getSessions()) {
            session.setAssessments(ImmutableList.of());
        }
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertTrue(timeline.getSessions().isEmpty());
        assertTrue(timeline.getAssessments().isEmpty());
        assertTrue(timeline.getSchedule().isEmpty());
    }
    
    @Test
    public void oneOneTimeSession() {
        Schedule2 schedule = createSchedule(null);
        
        Session session = createOneTimeSession(null);
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getDuration(), Period.parse("P10D"));
        assertEquals(timeline.getSchedule().size(), 1);
        assertEquals(timeline.getSessions().size(), 1);
        assertEquals(timeline.getAssessments().size(), 1);
        
        // The schedule proper with the instance GUIDs
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getRefGuid(), SESSION_GUID_3);
        // instance guid
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 0);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("PT8H"));
        assertNull(schSession.isPersistent());
        
        assertEquals(schSession.getAssessments().size(), 1);
        ScheduledAssessment schAsmt = schSession.getAssessments().get(0);
        assertEquals(schAsmt.getRefKey(), String.valueOf(schSession.getAssessments().get(0).getRefKey()));
        // instance guid
        
        SessionInfo sessionInfo = timeline.getSessions().get(0);
        assertEquals(sessionInfo.getGuid(), SESSION_GUID_3);
        assertEquals(sessionInfo.getLabel(), "One Time Session");
        assertEquals(sessionInfo.getMinutesToComplete(), Integer.valueOf(10));
        
        AssessmentInfo asmtInfo = timeline.getAssessments().get(0);
        assertEquals(asmtInfo.getGuid(), ASSESSMENT_1_GUID);
        assertEquals(asmtInfo.getAppId(), TEST_APP_ID);
        assertEquals(asmtInfo.getIdentifier(), "assessment-1");
        assertEquals(asmtInfo.getLabel(), "Assessment 1");
        assertEquals(asmtInfo.getKey(), "932e4de6932e4de6");
    }
    
    @Test
    public void oneRepeatingSession() throws Exception {
        Schedule2 schedule = createSchedule("P7W");
        schedule.getSessions().add(createRepeatingSession(null, "P1W"));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        AssessmentInfo info = timeline.getAssessments().get(0);
        
        assertEquals(timeline.getDuration(), Period.parse("P7W"));
        assertEquals(timeline.getSchedule().size(), 7);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("PT8H"));
        
        ScheduledAssessment schAsmt = schSession.getAssessments().get(0);
        assertEquals(schAsmt.getRefKey(), info.getKey());
        
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 7, 7);
        assertDayRange(timeline, 2, 14, 14);
        assertDayRange(timeline, 3, 21, 21);
        assertDayRange(timeline, 4, 28, 28);
        assertDayRange(timeline, 5, 35, 35);
        assertDayRange(timeline, 6, 42, 42);
    }
    
    @Test
    public void repeatingSessionWithLongWindowTruncatesAtStudyDuration() {
        Schedule2 schedule = createSchedule("P1W");
        
        Session session = createRepeatingSession(null, "P3D");
        session.getTimeWindows().get(0).setExpiration(Period.parse("P2D"));
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getSchedule().size(), 2);
        assertDayRange(timeline, 0, 0, 2);
        assertDayRange(timeline, 1, 3, 5);
        // but 6-8 would exceed one week, which is days 0-6
    }
    
    @Test
    public void twoOneTimeSessions() {
        Schedule2 schedule = createSchedule("P2W");
        Session session1 = createOneTimeSession(null);
        Session session2 = createOneTimeSession("P2D");
        session2.setGuid(SESSION_GUID_4);
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);

        assertEquals(timeline.getSchedule().size(), 2);
        assertEquals(timeline.getAssessments().size(), 1);
        assertEquals(timeline.getSessions().size(), 2);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 2, 2);
    }
    
    @Test
    public void twoSessionsOneRepeatsOneDoesNot() {
        Schedule2 schedule = createSchedule("P2W");
        Session session1 = createOneTimeSession(null);
        Session session2 = createRepeatingSession(null, "P2D");
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getSchedule().get(0).getRefGuid(), SESSION_GUID_3);
        assertEquals(timeline.getSchedule().get(1).getRefGuid(), SESSION_GUID_1);
        
        assertEquals(timeline.getSchedule().size(), 8);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 0, 0);
        assertDayRange(timeline, 2, 2, 2);
        assertDayRange(timeline, 3, 4, 4);
        assertDayRange(timeline, 4, 6, 6);
        assertDayRange(timeline, 5, 8, 8);
        assertDayRange(timeline, 6, 10, 10);
        assertDayRange(timeline, 7, 12, 12);
    }
    
    @Test
    public void twoRepeatingSessions() {
        Schedule2 schedule = createSchedule("P4D");
        Session session1 = createRepeatingSession(null, "P1D");
        Session session2 = createRepeatingSession(null, "P2D");
        session2.setGuid(SESSION_GUID_4);
        
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getSchedule().size(), 6);
        assertDayRange(timeline, 0, SESSION_GUID_1, 0, 0);
        assertDayRange(timeline, 1, SESSION_GUID_4, 0, 0);
        assertDayRange(timeline, 2, SESSION_GUID_1, 1, 1);
        assertDayRange(timeline, 3, SESSION_GUID_1, 2, 2);
        assertDayRange(timeline, 4, SESSION_GUID_4, 2, 2);
        assertDayRange(timeline, 5, SESSION_GUID_1, 3, 3);
    }
    
    @Test
    public void assessmentsCopiedToTimeline() throws Exception {
        // must respect configuration differences and create separate entries in the timeline, 
        // and line up the $ref values between ScheduledAssessments and AssessmentInfo
        // stanzas. We don't have to test the copy factory methods for ScheduledAssessment as 
        // this is tested separately.
        Schedule2 schedule = createSchedule("P1W");
        
        AssessmentReference ref1 = createAssessmentRef(ASSESSMENT_1_GUID);
        AssessmentReference ref2 = createAssessmentRef(ASSESSMENT_2_GUID);
        AssessmentReference ref3 = createAssessmentRef(ASSESSMENT_2_GUID);
        ref3.setColorScheme(new ColorScheme("#111111", "#222222", "#333333", "#444444"));
        AssessmentReference ref4 = createAssessmentRef(ASSESSMENT_3_GUID);
        AssessmentReference ref5 = createAssessmentRef(ASSESSMENT_3_GUID);
        ref5.setLabels(ImmutableList.of(new Label("en", "English")));
        AssessmentReference ref6 = createAssessmentRef(ASSESSMENT_4_GUID);
        
        Session session1 = createOneTimeSession(null);
        session1.setAssessments(ImmutableList.of(ref1, ref2, ref3, ref4));
        
        Session session2 = createRepeatingSession(null, "P10W");
        session2.setAssessments(ImmutableList.of(ref3, ref4, ref5, ref6));
        
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getAssessments().size(), 6);
        
        AssessmentInfo info1 = getByKey(timeline.getAssessments(), "932e4de6932e4de6");
        assertEquals(info1.getGuid(), ASSESSMENT_1_GUID);
        
        AssessmentInfo info2 = getByKey(timeline.getAssessments(), "5fa66c675fa66c67");
        assertEquals(info2.getGuid(), ASSESSMENT_2_GUID);
        assertNotNull(info2.getColorScheme());

        AssessmentInfo info3 = getByKey(timeline.getAssessments(), "24df134324df1343");
        assertEquals(info3.getGuid(), ASSESSMENT_3_GUID);
        assertEquals(info3.getLabel(), "English");

        AssessmentInfo info4 = getByKey(timeline.getAssessments(), "fa3150eafa3150ea");
        assertEquals(info4.getGuid(), ASSESSMENT_2_GUID);
        assertNull(info4.getColorScheme());
        
        AssessmentInfo info5 = getByKey(timeline.getAssessments(), "75705eb175705eb1");
        assertEquals(info5.getGuid(), ASSESSMENT_4_GUID);
        
        AssessmentInfo info6 = getByKey(timeline.getAssessments(), "ae40875fae40875f");
        assertEquals(info6.getGuid(), ASSESSMENT_3_GUID);
        assertEquals(info6.getLabel(), "Assessment 3");
        
        Set<String> infoRefs = timeline.getAssessments().stream()
                .map(AssessmentInfo::getKey).collect(toSet());
        
        Set<String> schAsmtRefs = new HashSet<>();
        timeline.getSchedule().stream().forEach(schSession -> {
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                schAsmtRefs.add(schAsmt.getRefKey());
            }
        });
        assertEquals(infoRefs, schAsmtRefs);
    }
    
    private AssessmentInfo getByKey(List<AssessmentInfo> assessments, String key) {
        for (AssessmentInfo info : assessments) {
            if (info.getKey().equals(key)) {
                return info;
            }
        }
        fail("Did not find assessmentInfo object");
        return null;
    }
    
    @Test
    public void sessionsCopiedToTimeline() {
        // Sessions aren't configurable and can be referenced by GUID.
        Schedule2 schedule = createSchedule("P1W");
        Session session1 = createOneTimeSession(null);
        Session session2 = createRepeatingSession(null, null);
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSessions().size(), 2);
        
        Set<String> guids = timeline.getSessions().stream()
            .map(SessionInfo::getGuid).collect(toSet());
        assertTrue(guids.contains(session1.getGuid()));
        assertTrue(guids.contains(session2.getGuid()));
    }
    
    @Test
    public void eachTimeWindowCreatesAScheduledSession() {
        Schedule2 schedule = createSchedule("P1W");
        
        Session session = createOneTimeSession(null);
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_GUID_2);
        window.setStartTime(LocalTime.parse("14:00"));
        session.getTimeWindows().add(window);
        schedule.getSessions().add(session);

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 2);
        
        assertEquals(timeline.getSchedule().get(0).getStartTime(), LocalTime.parse("08:00"));
        assertEquals(timeline.getSchedule().get(1).getStartTime(), LocalTime.parse("14:00"));
        assertEquals(timeline.getSessions().size(), 1);
        assertEquals(timeline.getAssessments().size(), 1);
    }
    
    @Test
    public void oneTimeSessionDelays() {
        Schedule2 schedule = createSchedule("P2W");
        Session session = createOneTimeSession("P3D");
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        assertDayRange(timeline, 0, 3, 3);
    }
    
    @Test
    public void delayUnderOneDayChangesProperties() {
        Schedule2 schedule = createSchedule("P2W");
        Session session = createOneTimeSession("PT6H");
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        assertDayRange(timeline, 0, 0, 0);
        assertEquals(timeline.getSchedule().get(0).getStartTime(), LocalTime.parse("08:00"));
        assertEquals(timeline.getSchedule().get(0).getDelayTime(), Period.parse("PT6H"));
    }
    
    @Test
    public void delayLongerThanDurationReturnsNoScheduledSession() { 
        Schedule2 schedule = createSchedule("P2W");
        Session session = createOneTimeSession("PT6H");
        session.setDelay(Period.parse("P2W1D"));
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertTrue(timeline.getSchedule().isEmpty());
        assertTrue(timeline.getSessions().isEmpty());
        assertTrue(timeline.getAssessments().isEmpty());
    }
    
    @Test
    public void delayAndExpirationLongerThanDurationReturnsNoScheduledSession() { 
        Schedule2 schedule = createSchedule("P2W");
        Session session = createRepeatingSession("P1W", "P1W");
        session.getTimeWindows().get(0).setExpiration(Period.parse("P1W1D"));
        schedule.getSessions().add(session);
        
        // Although it starts in the window and there’s plenty of time left in
        // the study, it extends past the end of the study and right now, we 
        // don’t include it because we don’t know if truncation would leave 
        // enough time or not.
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertTrue(timeline.getSchedule().isEmpty());
        assertTrue(timeline.getSessions().isEmpty());
        assertTrue(timeline.getAssessments().isEmpty());
    }
    
    @Test
    public void repeatingSessionDelays() {
        Schedule2 schedule = createSchedule("P2W");
        Session session = createRepeatingSession("P3D", "P4D");
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 3);
        assertDayRange(timeline, 0, 3, 3);
        assertDayRange(timeline, 1, 7, 7);
        assertDayRange(timeline, 2, 11, 11);
    }
    
    @Test
    public void sessionRepeatsUsingDayValue() {
        Schedule2 schedule = createSchedule("P4W");
        Session session = createRepeatingSession(null, "P6D");
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 5);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 6, 6);
        assertDayRange(timeline, 2, 12, 12);
        assertDayRange(timeline, 3, 18, 18);
        assertDayRange(timeline, 4, 24, 24);
        
    }
    
    @Test
    public void sessionRepeatsUsingWeekValue() {
        Schedule2 schedule = createSchedule("P4W");
        Session session = createRepeatingSession(null, "P1W");
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 4);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 7, 7);
        assertDayRange(timeline, 2, 14, 14);
        assertDayRange(timeline, 3, 21, 21);
    }
    
    @Test
    public void sessionStopsOnOccurrences() {
        Schedule2 schedule = createSchedule("P4W");
        Session session = createRepeatingSession(null, "P1W");
        session.setOccurrences(2);
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 2);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 7, 7);
    }
    
    @Test
    public void sessionStopsOnScheduleDurationBeforeOccurrenceMax() {
        Schedule2 schedule = createSchedule("P4W");
        Session session = createRepeatingSession(null, "P1W");
        session.setOccurrences(20);
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 4);
        assertDayRange(timeline, 0, 0, 0);
        assertDayRange(timeline, 1, 7, 7);
        assertDayRange(timeline, 2, 14, 14);
        assertDayRange(timeline, 3, 21, 21);
    }
    
    @Test
    public void sessionStopsOnScheduleDurationBeforeOccurrenceMaxWithDelay() {
        Schedule2 schedule = createSchedule("P4W");
        Session session = createRepeatingSession("P1W", "P1W");
        session.setOccurrences(20);
        schedule.getSessions().add(session);
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 3);
        assertDayRange(timeline, 0, 7, 7);
        assertDayRange(timeline, 1, 14, 14);
        assertDayRange(timeline, 2, 21, 21);
    }
    
    @Test
    public void timeWindowStartExpiresWithinDay() {
        Schedule2 schedule = createSchedule("P2D");
        // This is the default configuration of the test time window as we expect it to be typical
        Session session = createOneTimeSession(null);
        schedule.setSessions(ImmutableList.of(session));

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 0);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("PT8H"));
    }
    
    @Test
    public void timeWindowStartExpiresAfterDayUsingMinutes() {
        Schedule2 schedule = createSchedule("P2D");
        Session session = createOneTimeSession(null);
        // starts at 8:00, add 16 hours + 1 minute to the expiration, it 
        // goes into the next day (16 hours does not).
        session.getTimeWindows().get(0).setExpiration(Period.parse("PT961M"));
        schedule.setSessions(ImmutableList.of(session));

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 1);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("PT961M"));
    }

    @Test
    public void timeWindowStartExpiresAfterDayUsingHours() {
        Schedule2 schedule = createSchedule("P2D");
        Session session = createOneTimeSession(null);
        // starts at 8:00, add 17 hours
        session.getTimeWindows().get(0).setExpiration(Period.parse("PT17H"));
        schedule.setSessions(ImmutableList.of(session));

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 1);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("PT17H"));
    }

    @Test
    public void timeWindowStartExpiresAfterDayUsingDays() {
        Schedule2 schedule = createSchedule("P5D");
        Session session = createOneTimeSession(null);
        session.getTimeWindows().get(0).setExpiration(Period.parse("P4D"));
        schedule.setSessions(ImmutableList.of(session));

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 4);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("P4D"));        
    }

    @Test
    public void timeWindowStartExpiresAfterDayUsingWeeks() {
        Schedule2 schedule = createSchedule("P2W");
        Session session = createOneTimeSession(null);
        session.getTimeWindows().get(0).setExpiration(Period.parse("P1W"));
        schedule.setSessions(ImmutableList.of(session));

        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getStartDay(), 0);
        assertEquals(schSession.getEndDay(), 7);
        assertEquals(schSession.getStartTime(), LocalTime.parse("08:00"));
        assertEquals(schSession.getExpiration(), Period.parse("P1W"));        
    }
    
    @Test
    public void sessionInstanceGuidsAreCorrect() {
        String guid = INSTANCE.generateSessionInstanceGuid(SCHEDULE_GUID, SESSION_GUID_1, "event1", 3, SESSION_WINDOW_GUID_1);
        // window guid, window occurrence, session guid, schedule guid
        assertEquals(guid, "ciAzJI2y3i2RpN0EF9WOZQ");
    }
    
    @Test
    public void assessmentInstanceGuidsAreCorrect() {
        String guid = INSTANCE.generateAssessmentInstanceGuid(SCHEDULE_GUID, SESSION_GUID_1, "oneEventId", 3, SESSION_WINDOW_GUID_1, ASSESSMENT_1_GUID, 5);
        assertEquals(guid, "3eK3LXt_5NAc7qhVjhN-ag");
    }
    
    @Test
    public void sessionInstanceGuidsAreUnique() {
        Schedule2 schedule = createComplexSchedule();
        Set<String> sessionInstanceGuids = new HashSet<>();
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            sessionInstanceGuids.add(schSession.getInstanceGuid());
        }
        // There are 1,278 scheduled sessions and all must have a unique guid.
        assertEquals(sessionInstanceGuids.size(), 1274);
        
        // Just changing the schedule GUID will create 1,278 more unique GUIDs
        schedule.setGuid(ASSESSMENT_4_GUID);
        timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            sessionInstanceGuids.add(schSession.getInstanceGuid());
        }
        assertEquals(sessionInstanceGuids.size(), 2548);
    }
    
    @Test
    public void assessmentInstanceGuidsAreUnique() {
        Schedule2 schedule = createComplexSchedule();
        Set<String> asmtInstanceGuids = new HashSet<>();
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                asmtInstanceGuids.add(schAsmt.getInstanceGuid());    
            }
        }
        // There are 2,184 scheduled assessments and all must have a unique guid.
        assertEquals(asmtInstanceGuids.size(), 2366);
        
        // Just changing the schedule GUID will create 2,184 more unique GUIDs
        schedule.setGuid(ASSESSMENT_4_GUID);
        timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                asmtInstanceGuids.add(schAsmt.getInstanceGuid());    
            }
        }
        assertEquals(asmtInstanceGuids.size(), 4732);
    }
    
    @Test
    public void allGuidsAreUnique() {
        Schedule2 schedule = createComplexSchedule();
        
        Set<String> allGuids = new HashSet<>();
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            allGuids.add(schSession.getInstanceGuid());
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                allGuids.add(schAsmt.getInstanceGuid());
            }
        }
        
        Set<String> allGuidsAgain = new HashSet<>();
        timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            allGuidsAgain.add(schSession.getInstanceGuid());
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                allGuidsAgain.add(schAsmt.getInstanceGuid());
            }
        }
        assertEquals(allGuids, allGuidsAgain);
        assertEquals(allGuids.size(), 3640);
    }
    
    @Test
    public void sessionInstanceGuidsAreIdemptotent() {
        Schedule2 schedule = createComplexSchedule();
        Set<String> sessionInstanceGuids = new HashSet<>();
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            sessionInstanceGuids.add(schSession.getInstanceGuid());
        }
        
        // Re-running gets you the same set of GUIDs
        Set<String> secondSessionInstanceGuids = new HashSet<>();
        timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            secondSessionInstanceGuids.add(schSession.getInstanceGuid());
        }
        assertEquals(secondSessionInstanceGuids, sessionInstanceGuids);
    }
    
    @Test
    public void assessmentInstanceGuidsAreIdempotent() {
        Schedule2 schedule = createComplexSchedule();
        Set<String> asmtInstanceGuids = new HashSet<>();
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                asmtInstanceGuids.add(schAsmt.getInstanceGuid());    
            }
        }
        
        // Re-running gets you the same set of GUIDs
        Set<String> secondAsmtInstanceGuids = new HashSet<>();
        timeline = INSTANCE.calculateTimeline(schedule);
        for (ScheduledSession schSession : timeline.getSchedule()) {
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                secondAsmtInstanceGuids.add(schAsmt.getInstanceGuid());    
            }
        }
        assertEquals(secondAsmtInstanceGuids, asmtInstanceGuids);
    }
    
    @Test
    public void metadataRecordsAreCalculated() {
        Schedule2 schedule = createComplexSchedule();
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        
        List<TimelineMetadata> metadata = timeline.getMetadata();
        
        assertEquals(metadata.size(), 3640);
        
        List<TimelineMetadata> sessionMetadata = metadata.stream()
                .filter(m -> m.getAssessmentGuid() == null).collect(toList());
        assertEquals(sessionMetadata.size(), 1274);
        
        TimelineMetadata sm = sessionMetadata.get(0);
        assertEquals(sm.getGuid(), sm.getSessionInstanceGuid());
        assertNull(sm.getAssessmentInstanceGuid());
        assertNull(sm.getAssessmentGuid());
        assertNull(sm.getAssessmentId());
        assertNotNull(sm.getSessionInstanceGuid());
        assertEquals(sm.getSessionGuid(), SESSION_GUID_2);
        assertEquals(sm.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(sm.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(sm.isSchedulePublished());
        
        List<TimelineMetadata> asmtMetadata = metadata.stream()
                .filter(m -> m.getAssessmentGuid() != null).collect(toList());
        assertEquals(asmtMetadata.size(), 2366);
        
        TimelineMetadata am = asmtMetadata.get(0);
        assertEquals(am.getGuid(), am.getAssessmentInstanceGuid());
        assertNotNull(am.getAssessmentInstanceGuid());
        assertEquals(am.getAssessmentGuid(), ASSESSMENT_1_GUID);
        assertEquals(am.getAssessmentId(), "shared-assessment");
        assertNotNull(am.getSessionInstanceGuid());
        assertEquals(am.getSessionGuid(), SESSION_GUID_2);
        assertEquals(am.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(am.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(am.isSchedulePublished());
    }
    
    /* ============================================================ */
    /* The most significant calculation is endDay, many tests for it */
    /* ============================================================ */
    
    @Test
    public void startTimeMidnightStartDayZeroExpMinutesUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("PT180M"));
        assertEquals(endDay, 0);
    }
    @Test
    public void startTimeMidnightStartDayZeroExpMinutesOnDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("PT1440M"));
        // Why zero? Because no time has passed in day one and the client doesn't want to load this
        // edge case. It's done on day one.
        assertEquals(endDay, 0);
    }
    // Test this with some different values...it still is correct 
    @Test
    public void startTimeMidnightStartDayZeroExpMinutesOnDay2() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("22:00"), 0, Period.parse("PT2H"));
        assertEquals(endDay, 0);
    }
    @Test
    public void startTimeMidnightStartDayZeroExpMinutesOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("PT1442M"));
        assertEquals(endDay, 1);
    }
    @Test
    public void startTimeMidnightStartDayZeroExpHoursUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("PT3H"));
        assertEquals(endDay, 0);
    }
    @Test
    public void startTimeMidnightStartDayZeroExpHoursOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("PT49H"));
        assertEquals(endDay, 2);
    }
    @Test
    public void startTimeMidnightStartDayZeroExpDays() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 0, Period.parse("P3D"));
        assertEquals(endDay, 2); // again this is truncated by one day
    }

    @Test
    public void startTimeMidnightStartDayTenExpMinutesUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 10, Period.parse("PT30M"));
        assertEquals(endDay, 10);
    }
    @Test
    public void startTimeMidnightStartDayTenExpMinutesOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 10, Period.parse("PT1442M"));
        assertEquals(endDay, 11);
    }
    @Test
    public void startTimeMidnightStartDayTenExpHoursUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 10, Period.parse("PT3H"));
        assertEquals(endDay, 10);
    }
    @Test
    public void startTimeMidnightStartDayTenExpHoursOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 10, Period.parse("PT25H"));
        assertEquals(endDay, 11);
    }
    @Test
    public void startTimeMidnightStartDayTenExpDays() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("00:00"), 10, Period.parse("P10D"));
        assertEquals(endDay, 19);
    }

    @Test
    public void startTimeMiddayStartDayZeroExpMinutesUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 0, Period.parse("PT40M"));
        assertEquals(endDay, 0);
    }
    @Test
    public void startTimeMiddayStartDayZeroExpMinutesOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 0, Period.parse("PT2880M"));
        assertEquals(endDay, 2);
    }
    @Test
    public void startTimeMiddayStartDayZeroExpHoursUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 0, Period.parse("PT4H"));
        assertEquals(endDay, 0);
    }
    @Test
    public void startTimeMiddayStartDayZeroExpHoursOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 0, Period.parse("PT12H"));
        assertEquals(endDay, 1);
    }
    @Test
    public void startTimeMiddayStartDayZeroExpDays() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 0, Period.parse("P3D"));
        assertEquals(endDay, 3);
    }

    @Test
    public void startTimeMiddayStartDayTenExpMinutesUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 10, Period.parse("PT30M"));
        assertEquals(endDay, 10);
    }
    @Test
    public void startTimeMiddayStartDayTenExpMinutesOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 10, Period.parse("PT2880M"));
        assertEquals(endDay, 12);
    }
    @Test
    public void startTimeMiddayStartDayTenExpHoursUnderDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 10, Period.parse("PT3H"));
        assertEquals(endDay, 10);
    }
    @Test
    public void startTimeMiddayStartDayTenExpHoursOverDay() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 10, Period.parse("PT48H"));
        assertEquals(endDay, 12);
    }
    @Test
    public void startTimeMiddayStartDayTenExpDays() {
        int endDay = INSTANCE.calculateEndDay(20, LocalTime.parse("13:00"), 10, Period.parse("P13D"));
        assertEquals(endDay, 23);
    }
    
    // This verifies that we set an expiration for scheduled sessions based on windows without
    // an expiration, and (since these had an off-by-one error) verifies that we are calculating
    // the endDay for these sessions correctly.
    @Test
    public void expirationDefaultsToLastDayOfStudy() {
        int endDay = INSTANCE.calculateEndDay(21, LocalTime.parse("10:00"), 7, null);
        assertEquals(endDay, 20);
        
        Schedule2 schedule = createSchedule("P21D");
        Session session = createOneTimeSession("P2D");
        session.getTimeWindows().get(0).setExpiration(null); // no expiration
        schedule.setSessions(ImmutableList.of(session));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 1);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        assertEquals(schSession.getEndDay(), 20);
        assertEquals(schSession.getExpiration(), Period.parse("P19D"));
    }
    
    // This behavior was not what I expected 
    @Test
    public void weeklyAppearsAt28Days() {
        TimeWindow timeWindow = new TimeWindow();
        timeWindow.setGuid(SESSION_WINDOW_GUID_1);
        timeWindow.setStartTime(LocalTime.parse("08:00"));
        timeWindow.setExpiration(Period.parse("PT12H"));
        
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        session.setStartEventIds(ImmutableList.of("enrollment"));
        session.setName("Sessions repeating weekly");
        session.setInterval(Period.parse("P1W"));
        session.setPerformanceOrder(SEQUENTIAL);
        session.setTimeWindows(ImmutableList.of(timeWindow));
        session.setAssessments(ImmutableList.of(createAssessmentRef("AAA")));
        
        Schedule2 schedule = new Schedule2();
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P21D"));
        schedule.setSessions(ImmutableList.of(session));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 3);
        
        // but change the expiration past the last day, and it'll be 3
        timeWindow.setExpiration(Period.parse("PT24H"));        
        timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 3);
    }
    
    @Test
    public void multipleEventIdsGenerateSeparateScheduledSessions() {
        TimeWindow timeWindow = new TimeWindow();
        timeWindow.setGuid(SESSION_WINDOW_GUID_1);
        timeWindow.setStartTime(LocalTime.parse("08:00"));
        timeWindow.setExpiration(Period.parse("PT12H"));
        
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        session.setStartEventIds(ImmutableList.of("enrollment", "timeline_retrieved"));
        session.setName("Sessions repeating weekly");
        session.setInterval(Period.parse("P1D"));
        session.setPerformanceOrder(SEQUENTIAL);
        session.setTimeWindows(ImmutableList.of(timeWindow));
        session.setAssessments(ImmutableList.of(createAssessmentRef("AAA")));
        
        Schedule2 schedule = new Schedule2();
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P2D"));
        schedule.setSessions(ImmutableList.of(session));
        
        Timeline timeline = INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 4);
        
        assertEquals(timeline.getSchedule().stream()
                .map(ScheduledSession::getInstanceGuid).collect(toSet()).size(), 4);
        
        ScheduledSession schSession = timeline.getSchedule().get(0); 
        assertEquals(schSession.getStartEventId(), "enrollment");
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getStartDay(), 0);
        
        schSession = timeline.getSchedule().get(1); 
        assertEquals(schSession.getStartEventId(), "timeline_retrieved");
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getStartDay(), 0);

        schSession = timeline.getSchedule().get(2); 
        assertEquals(schSession.getStartEventId(), "enrollment");
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getStartDay(), 1);
        
        schSession = timeline.getSchedule().get(3); 
        assertEquals(schSession.getStartEventId(), "timeline_retrieved");
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getStartDay(), 1);
    }
    
    @Test
    public void studyBurstsAreResolvedInTimeline() throws Exception {
        Schedule2 schedule = createSchedule("P3D");
        
        StudyBurst burst1 = new StudyBurst();
        burst1.setIdentifier("burst1");
        burst1.setOriginEventId("timeline_retrieved");
        burst1.setInterval(Period.parse("P1D"));
        burst1.setOccurrences(3);
        burst1.setUpdateType(MUTABLE);
        
        StudyBurst burst2 = new StudyBurst();
        burst2.setIdentifier("burst2");
        burst2.setOriginEventId("timeline_retrieved");
        burst2.setInterval(Period.parse("P1D"));
        burst2.setOccurrences(1);
        burst2.setUpdateType(MUTABLE);
        
        schedule.setStudyBursts(ImmutableList.of(burst1, burst2));
        
        Session session = createOneTimeSession("P1D");
        session.setStudyBurstIds(ImmutableList.of("burst1", "burst2"));
        schedule.setSessions(ImmutableList.of(session));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSchedule().size(), 5);
        
        Set<String> triggerEventIds = timeline.getSchedule().stream()
                .map(ScheduledSession::getStartEventId)
                .collect(toSet());
        assertEquals(triggerEventIds, ImmutableSet.of("enrollment", "study_burst:burst1:01", "study_burst:burst1:02",
                "study_burst:burst1:03", "study_burst:burst2:01"));
    }
    
    /* ============================================================ */
    /* Many helper methods to construct a schedule */
    /* ============================================================ */
    
    private Schedule2 createSchedule(String duration) {
        if (duration == null) {
            duration = "P10D";
        }
        Schedule2 schedule = new Schedule2();
        schedule.setName("Test Schedule");
        schedule.setDuration(Period.parse(duration));
        schedule.setGuid(SCHEDULE_GUID);
        return schedule;
    }
    
    private Session createOneTimeSession(String delay) {
        Session session = new Session();
        session.setStartEventIds(ImmutableList.of("enrollment"));
        if (delay != null) {
            session.setDelay(Period.parse(delay));    
        }
        session.setAssessments(ImmutableList.of(createAssessmentRef(ASSESSMENT_1_GUID)));
        session.setGuid(SESSION_GUID_3);
        session.setName("One Time Session");
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_2);
        window.setStartTime(LocalTime.parse("08:00"));
        window.setExpiration(Period.parse("PT8H"));
        session.getTimeWindows().add(window);
        
        return session;
    }
    
    private Session createRepeatingSession(String delay, String interval) {
        if (interval == null) {
            interval = "P1D";
        }
        Session session = new Session();
        session.setStartEventIds(ImmutableList.of("enrollment"));
        if (delay != null) {
            session.setDelay(Period.parse(delay));    
        }
        session.setInterval(Period.parse(interval));
        session.setAssessments(ImmutableList.of(createAssessmentRef(ASSESSMENT_2_GUID)));
        session.setGuid(SESSION_GUID_1);
        session.setName("Repeating Session");
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_1);
                
        window.setStartTime(LocalTime.parse("08:00"));
        window.setExpiration(Period.parse("PT8H"));
        session.getTimeWindows().add(window);

        
        return session;
    }
    
    private AssessmentReference createAssessmentRef(String guid) {
        AssessmentReference ref = new AssessmentReference();
        ref.setAppId(TEST_APP_ID);
        ref.setIdentifier("assessment-" + guid.substring(0,1));
        ref.setTitle("Assessment " + guid.substring(0,1));
        ref.setMinutesToComplete(10);
        ref.setGuid(guid);
        return ref;
    }
    
    private List<Label> createLabels(String lang1, String value1, String lang2, String value2) {
        Label label1 = new Label(lang1, value1);
        Label label2 = new Label(lang2, value2);
        return ImmutableList.of(label1, label2);
    }
    
    private void assertDayRange(Timeline timeline, int schSessionIndex, int startDay, int endDay) {
        ScheduledSession schSession = timeline.getSchedule().get(schSessionIndex);
        assertEquals(schSession.getStartDay(), startDay);
        assertEquals(schSession.getEndDay(), endDay);
    }
    
    private void assertDayRange(Timeline timeline, int schSessionIndex, String guid, int startDay, int endDay) {
        ScheduledSession schSession = timeline.getSchedule().get(schSessionIndex);
        assertEquals(schSession.getRefGuid(), guid);
        assertEquals(schSession.getStartDay(), startDay);
        assertEquals(schSession.getEndDay(), endDay);
    }
    
    private Schedule2 createComplexSchedule() {
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setName("Test");
        schedule.setPublished(true);
        schedule.setDuration(Period.parse("P52W"));
        
        Session session1 = new Session();
        session1.setGuid(SESSION_GUID_1);
        session1.setName("Session #1");
        session1.setStartEventIds(ImmutableList.of("activities_retrieved"));
        session1.setInterval(Period.parse("P2D"));
        session1.setPerformanceOrder(SEQUENTIAL);
        session1.setAssessments(ImmutableList.of(createAssessmentRef(ASSESSMENT_1_GUID)));
        
        TimeWindow tw = new TimeWindow();
        tw.setGuid(SESSION_WINDOW_GUID_1);
        tw.setStartTime(LocalTime.parse("08:00"));
        tw.setExpiration(Period.parse("PT8H"));
        session1.setTimeWindows(ImmutableList.of(tw));
        
        Session session2 = new Session();
        session2.setGuid(SESSION_GUID_2);
        session2.setName("Session #2");
        session2.setStartEventIds(ImmutableList.of("enrollment"));
        session2.setInterval(Period.parse("P1D"));
        session2.setPerformanceOrder(SEQUENTIAL);
        session2.setAssessments(ImmutableList.of(createAssessmentRef(ASSESSMENT_2_GUID)));
        
        TimeWindow tw1 = new TimeWindow();
        tw1.setGuid(SESSION_WINDOW_GUID_2);
        tw1.setStartTime(LocalTime.parse("08:00"));
        tw1.setExpiration(Period.parse("PT4H"));
        
        TimeWindow tw2 = new TimeWindow();
        tw2.setGuid(SESSION_WINDOW_GUID_3);
        tw2.setStartTime(LocalTime.parse("12:00"));
        tw2.setExpiration(Period.parse("PT4H"));
        tw2.setPersistent(true);
        
        TimeWindow tw3 = new TimeWindow();
        tw3.setGuid(SESSION_WINDOW_GUID_4);
        tw3.setStartTime(LocalTime.parse("16:00"));
        tw3.setExpiration(Period.parse("PT4H"));
        session2.setTimeWindows(ImmutableList.of(tw1, tw2, tw3));
        
        AssessmentReference as1 = new AssessmentReference();
        as1.setGuid(ASSESSMENT_1_GUID);
        as1.setAppId("shared");
        as1.setIdentifier("shared-assessment");
        as1.setTitle("A shared assessment");
        as1.setLabels(ImmutableList.of(new Label("en", "This is a test")));
        as1.setMinutesToComplete(3);
        as1.setColorScheme(new ColorScheme("#ff33ff", null, null, null));
        
        AssessmentReference as2 = new AssessmentReference();
        as2.setGuid(ASSESSMENT_2_GUID);
        as2.setAppId("local");
        as2.setIdentifier("local-assessment");
        as2.setTitle("A local assessment");
        as2.setMinutesToComplete(3);
        session2.setAssessments(ImmutableList.of(as1, as2));
        
        schedule.setSessions(ImmutableList.of(session2, session1));
        return schedule;
    }
}
