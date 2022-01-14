package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class EventStreamAdherenceReportGeneratorTest {
    
    private static final DateTime NOW = DateTime.parse("2021-10-15T08:13:47.345-07:00");
    private static final DateTime STARTED_ON = CREATED_ON;
    private static final DateTime FINISHED_ON = MODIFIED_ON;
    private static final TimelineMetadata META1 = createMeta("sessionInstanceGuid", "sessionInstanceGuid",
            "sessionGuid", "timeWindowGuid", 13, 15, "sessionStartEventId", "studyBurstId", 1, "sessionSymbol",
            "sessionName", false);
    private static final TimelineMetadata META_PERSISTENT = createMeta("JOQg4yz0lrif7V3HYYzACw",
            "JOQg4yz0lrif7V3HYYzACw", "Bw7z_QMiGeuQDVSk_Ndo-Gp-", "yxSek2gkA5tHFQRTXafsqrzX", 0, 0, "created_on", null,
            null, null, "Session #3 - window is persistent", true);
    private static final TimelineMetadata META2_A = createMeta("fTnAnasmcs-UndPt60iX8w", "fTnAnasmcs-UndPt60iX8w",
            "u90_okqrmPgKptcc9E8lORwC", "ksuWqp17x3i9zjQBh0FHSDS2", 4, 4, "study_burst:Main Sequence:01",
            "Main Sequence", 1, "*", "Session #1", false);
    private static final TimelineMetadata META2_B = createMeta("gnescr0HRz5T2JEjc0Ad6Q", "gnescr0HRz5T2JEjc0Ad6Q",
            "u90_okqrmPgKptcc9E8lORwC", "ksuWqp17x3i9zjQBh0FHSDS2", 2, 2, "study_burst:Main Sequence:01",
            "Main Sequence", 1, "*", "Session #1", false);
    
    @Test
    public void generate_metadataCopiedToReport() throws Exception {
        // We will focus on calculation of the completion states in the report, but here let's 
        // verify the metadata is being copied over into the report.
        AdherenceState state = createState(NOW, META1, null, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(report.getTimestamp(), NOW);
        assertEquals(report.getAdherencePercent(), 100);
        assertFalse(report.isActiveOnly());
        assertEquals(report.getStreams().size(), 1);
        
        EventStream stream = report.getStreams().get(0);
        assertEquals(stream.getStartEventId(), "sessionStartEventId");
        assertEquals(stream.getStudyBurstId(), "studyBurstId");
        assertEquals(stream.getStudyBurstNum(), Integer.valueOf(1));
        assertEquals(stream.getByDayEntries().size(), 1);
        assertEquals(stream.getByDayEntries().get(Integer.valueOf(13)).size(), 1); 
        
        EventStreamDay day = stream.getByDayEntries().get(Integer.valueOf(13)).get(0);
        assertEquals(day.getSessionGuid(), "sessionGuid");
        assertEquals(day.getSessionGuid(), "sessionGuid");
        assertEquals(day.getStartDay(), (Integer)13);
        assertEquals(day.getTimeWindows().get(0).getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(day.getTimeWindows().get(0).getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(day.getTimeWindows().get(0).getEndDay(), (Integer)15);
        assertEquals(day.getTimeWindows().get(0).getState(), NOT_APPLICABLE);
    }
    
    @Test
    public void constructorUsesParticipantTimeZone() { 
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("enrollment")
                .withTimestamp(MODIFIED_ON).build();
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withClientTimeZone("America/Denver");
        builder.withMetadata(ImmutableList.of());
        builder.withEvents(ImmutableList.of(event));
        builder.withAdherenceRecords(ImmutableList.of());
        builder.withNow(NOW);
        
        AdherenceState state = builder.build();
        assertEquals(state.getTimeZone().toString(), "America/Denver");
    }
    
    @Test
    public void constructorUsesNowTimestampTimeZone() { 
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("enrollment")
                .withTimestamp(MODIFIED_ON).build();
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withMetadata(ImmutableList.of());
        builder.withEvents(ImmutableList.of(event));
        builder.withAdherenceRecords(ImmutableList.of());
        builder.withNow(NOW);
        
        AdherenceState state = builder.build();
        assertEquals(state.getTimeZone().toString(), "-07:00");
    }
    
    @Test
    public void stateCalculation_notApplicable() throws Exception {
        // We will focus on calculation of the completion states in the report, but here let's 
        // verify the metadata is being copied over into the report.
        AdherenceState state = createState(NOW, META1, null, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notApplicableWithAdherence() throws Exception {
        // This is a pathological case...the client shouldn't have generated an adherence record
        // for an assessment that the participant shouldn't do. 
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        
        AdherenceState state = createState(NOW, META1, null, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailable() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.minusDays(10), META1, event, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailableWithAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.minusDays(10), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_unstarted() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_started() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(STARTED));
    }
    
    @Test
    public void stateCalculation_completed() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED));
    }
    
    @Test
    public void stateCalculation_abandoned() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(ABANDONED));
    }
    
    @Test
    public void stateCalculation_expired() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }

    @Test
    public void stateCalculation_expiredNoRecord() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void stateCalculation_declined() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", true);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(DECLINED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "otherSessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantEvents() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("otherStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_pastChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        AdherenceState state = createState(NOW.minusDays(21), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }

    @Test
    public void stateCalculation_futureChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        AdherenceState state = createState(NOW.plusDays(28), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void persistentSessionWindowsAreIgnored() {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "JOQg4yz0lrif7V3HYYzACw", false);
        StudyActivityEvent event = createEvent("created_on", NOW);

        AdherenceState state = createState(NOW, META_PERSISTENT, event, adherenceRecord);
        assertTrue(INSTANCE.generate(state).getStreams().isEmpty());
    }
    
    @Test
    public void showActiveShowsActive() { 
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }

    @Test
    public void showActiveFiltersNotYetAvailable() throws Exception { 
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.minusDays(10), META1, event, null, true);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertTrue(report.getStreams().isEmpty());        
    }

    @Test
    public void showActiveFiltersNotApplicable() throws Exception { 
        AdherenceState state = createState(NOW.minusDays(10), META1, null, null, true);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertTrue(report.getStreams().isEmpty());        
    }
    
    @Test
    public void showActiveFiltersExpired() throws Exception { 
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertTrue(report.getStreams().isEmpty());
    }
    
    @Test
    public void showActiveFiltersCompleted() throws Exception {
        // Note thought that we will return completed sessions if they are in their time window, and
        // we want users to see that they've finished it.
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(17));
        
        AdherenceState state = createState(NOW, META1, event, adherenceRecord, true);
        
        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertTrue(report.getStreams().isEmpty());
    }
    
    @Test
    public void showActiveDoesNotFilterCompletedInWindow() throws Exception {
        // Note thought that we will return completed sessions if they are in their time window, and
        // we want users to see that they've finished it.
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        AdherenceState state = createState(NOW, META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED));
    }
    
    @Test
    public void groupsUnderEventId() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "gnescr0HRz5T2JEjc0Ad6Q", false);        
        StudyActivityEvent event = createEvent("study_burst:Main Sequence:01", NOW.minusDays(3));

        AdherenceState state = createState(NOW, META2_A, META2_B, event, adherenceRecord, false);

        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertNotNull(report.getStreams().get(0).getByDayEntries().get(2));
        assertNotNull(report.getStreams().get(0).getByDayEntries().get(4));
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED, NOT_YET_AVAILABLE));
    }
    
    @Test
    public void handleNulls() {
        AdherenceState state = new AdherenceState.Builder().withNow(NOW).build();
        
        EventStreamAdherenceReport report = INSTANCE.generate(state);
        assertEquals(100, report.getAdherencePercent());
        assertEquals(report.getTimestamp(), NOW); // no time zone adjustment
        assertTrue(report.getStreams().isEmpty());
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord) {
        return createState(now, meta, event, adherenceRecord, false);
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        return createState(now, meta, null, event, adherenceRecord, showActive);
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta1,
            TimelineMetadata meta2, StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        AdherenceState.Builder builder = new AdherenceState.Builder();
        List<TimelineMetadata> metas = new ArrayList<>();
        if (meta1 != null) {
            metas.add(meta1);       
        }
        if (meta2 != null) {
            metas.add(meta2);       
        }
        builder.withMetadata(metas);
        if (event != null) {
            builder.withEvents(ImmutableList.of(event));
        }
        if (adherenceRecord != null) {
            builder.withAdherenceRecords(ImmutableList.of(adherenceRecord));
        }
        builder.withShowActive(showActive);
        builder.withNow(now);
        return builder.build();
    }
    
    private static TimelineMetadata createMeta(String guid, String sessionInstanceGuid, String sessionGuid,
            String timeWindowGuid, Integer sessionInstanceStartDay, Integer sessionInstanceEndDay,
            String sessionStartEventId, String studyBurstId, Integer studyBurstNum, String sessionSymbol,
            String sessionName, boolean timeWindowPersistent) {
        TimelineMetadata meta = new TimelineMetadata();
        meta.setGuid(guid);
        meta.setSessionInstanceGuid(sessionInstanceGuid);
        meta.setSessionGuid(sessionGuid);
        meta.setTimeWindowGuid(timeWindowGuid);
        meta.setSessionInstanceStartDay(sessionInstanceStartDay);
        meta.setSessionInstanceEndDay(sessionInstanceEndDay);
        meta.setSessionStartEventId(sessionStartEventId);
        meta.setStudyBurstId(studyBurstId);
        meta.setStudyBurstNum(studyBurstNum);
        meta.setSessionSymbol(sessionSymbol);
        meta.setSessionName(sessionName);
        meta.setTimeWindowPersistent(timeWindowPersistent);
        return meta;
    }

    private StudyActivityEvent createEvent(String eventId, DateTime timestamp) {
        return new StudyActivityEvent.Builder().withEventId(eventId).withTimestamp(timestamp).build();
    }
    
    private AdherenceRecord createRecord(DateTime startedOn, DateTime finishedOn, String instanceGuid, boolean declined) {
        AdherenceRecord rec = new AdherenceRecord();
        rec.setStartedOn(startedOn);
        rec.setFinishedOn(finishedOn);
        rec.setInstanceGuid(instanceGuid);
        rec.setDeclined(declined);
        return rec;
    }
    
    private List<SessionCompletionState> getReportStates(EventStreamAdherenceReport report) {
        return report.getStreams().stream()
            .flatMap(stream -> stream.getByDayEntries().values().stream())
            .flatMap(days -> days.stream())
            .flatMap(day -> day.getTimeWindows().stream())
            .map(EventStreamWindow::getState)
            .collect(toList());
    }
}
