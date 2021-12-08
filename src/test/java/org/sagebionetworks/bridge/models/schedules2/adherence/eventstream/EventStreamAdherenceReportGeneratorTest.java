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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
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
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, null, null);

        EventStreamAdherenceReport report = generator.generate();
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
    public void stateCalculation_notApplicable() throws Exception {
        // We will focus on calculation of the completion states in the report, but here let's 
        // verify the metadata is being copied over into the report.
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, null, null);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notApplicableWithAdherence() throws Exception {
        // This is a pathological case...the client shouldn't have generated an adherence record
        // for an assessment that the participant shouldn't do. 
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, null, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailable() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.minusDays(10), META1, event, null);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailableWithAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.minusDays(10), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_unstarted() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_started() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(STARTED));
    }
    
    @Test
    public void stateCalculation_completed() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED));
    }
    
    @Test
    public void stateCalculation_abandoned() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(ABANDONED));
    }
    
    @Test
    public void stateCalculation_expired() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }

    @Test
    public void stateCalculation_expiredNoRecord() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(2), META1, event, null);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void stateCalculation_declined() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", true);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(DECLINED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "otherSessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantEvents() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("otherStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_pastChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.minusDays(21), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }

    @Test
    public void stateCalculation_futureChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(28), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void persistentSessionWindowsAreIgnored() {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "JOQg4yz0lrif7V3HYYzACw", false);
        StudyActivityEvent event = createEvent("created_on", NOW);

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META_PERSISTENT, event, adherenceRecord);
        assertTrue(generator.generate().getStreams().isEmpty());
    }
    
    @Test
    public void showActiveShowsActive() { 
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }

    @Test
    public void showActiveFiltersNotYetAvailable() throws Exception { 
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.minusDays(10), META1, event, null, true);

        EventStreamAdherenceReport report = generator.generate();
        assertTrue(report.getStreams().isEmpty());        
    }

    @Test
    public void showActiveFiltersExpired() throws Exception { 
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW.plusDays(2), META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = generator.generate();
        assertTrue(report.getStreams().isEmpty());
    }
    
    @Test
    public void showActiveFiltersCompleted() throws Exception {
        // Note thought that we will return completed sessions if they are in their time window, and
        // we want users to see that they've finished it.
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(17));
        
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = generator.generate();
        assertTrue(report.getStreams().isEmpty());
    }
    
    @Test
    public void showActiveDoesNotFilterCompletedInWindow() throws Exception {
        // Note thought that we will return completed sessions if they are in their time window, and
        // we want users to see that they've finished it.
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META1, event, adherenceRecord, true);

        EventStreamAdherenceReport report = generator.generate();
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED));
    }
    
    @Test
    public void groupsUnderEventId() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "gnescr0HRz5T2JEjc0Ad6Q", false);        
        StudyActivityEvent event = createEvent("study_burst:Main Sequence:01", NOW.minusDays(3));

        EventStreamAdherenceReportGenerator generator = makeGenerator(NOW, META2_A, META2_B, event, adherenceRecord, false);
        
        EventStreamAdherenceReport report = generator.generate();
        assertNotNull(report.getStreams().get(0).getByDayEntries().get(2));
        assertNotNull(report.getStreams().get(0).getByDayEntries().get(4));
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED, NOT_YET_AVAILABLE));
    }
    
    @Test
    public void handleNulls() {
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder().build();
        
        EventStreamAdherenceReport report = generator.generate();
        assertEquals(100, report.getAdherencePercent());
        assertNull(report.getTimestamp());
        assertTrue(report.getStreams().isEmpty());
    }
    
    private EventStreamAdherenceReportGenerator makeGenerator(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord) {
        return makeGenerator(now, meta, event, adherenceRecord, false);
    }
    
    private EventStreamAdherenceReportGenerator makeGenerator(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        return makeGenerator(now, meta, null, event, adherenceRecord, showActive);
    }
    
    private EventStreamAdherenceReportGenerator makeGenerator(DateTime now, TimelineMetadata meta1,
            TimelineMetadata meta2, StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
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

//    private List<TimelineMetadata> getMetadata() {
//        List<TimelineMetadata> meta = new ArrayList<>();
//        meta.add(createMeta("2G-B57I2kgwlxePcfp9kyA","2G-B57I2kgwlxePcfp9kyA","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",13,13,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("4ahNsBcGxSs34xZVacXXQg","4ahNsBcGxSs34xZVacXXQg","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        meta.add(createMeta("5dg79dBgLTIX2MN5VhXEwA","5dg79dBgLTIX2MN5VhXEwA","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("6I8fHOj6DPMbr-CtISJiLg","6I8fHOj6DPMbr-CtISJiLg","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("AT1TuKMsZhQX5EW8C5r-7g","AT1TuKMsZhQX5EW8C5r-7g","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",7,7,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("bsUZ3oIgmXSAbiIOIs1QWg","bsUZ3oIgmXSAbiIOIs1QWg","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("D-GERcPdJofqgIQGpqt4fA","D-GERcPdJofqgIQGpqt4fA","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("Dl4HCj5YcS4ry9XJ_LgZdg","Dl4HCj5YcS4ry9XJ_LgZdg","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",19,19,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("e-9lNQC2y3lE5n-aqJ6AKQ","e-9lNQC2y3lE5n-aqJ6AKQ","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("EkIF2rZmKor9Bqrt-6X7OQ","EkIF2rZmKor9Bqrt-6X7OQ","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",4,4,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("fTnAnasmcs-UndPt60iX8w","fTnAnasmcs-UndPt60iX8w","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("gnescr0HRz5T2JEjc0Ad6Q","gnescr0HRz5T2JEjc0Ad6Q","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("gV8vn0AtRkiWzzpHlqpHQw","gV8vn0AtRkiWzzpHlqpHQw","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        meta.add(createMeta("JOQg4yz0lrif7V3HYYzACw","JOQg4yz0lrif7V3HYYzACw","Bw7z_QMiGeuQDVSk_Ndo-Gp-","yxSek2gkA5tHFQRTXafsqrzX",0,0,"created_on", null, null, null,"Session #3 - window is persistent", true));
//        meta.add(createMeta("jUSgc2bo9bgrfA_fdQ5s6Q","jUSgc2bo9bgrfA_fdQ5s6Q","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",1,1,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("KkpuXNUdtapDvmCwbfaV1A","KkpuXNUdtapDvmCwbfaV1A","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("LRCQgu855OC0W6sroFk17Q","LRCQgu855OC0W6sroFk17Q","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        meta.add(createMeta("MhG2IVYTO8RpqhcaXrlfzA","MhG2IVYTO8RpqhcaXrlfzA","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("misJxNwbXcYJ70OLLgMBgg","misJxNwbXcYJ70OLLgMBgg","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        meta.add(createMeta("njdPhdjaOog1NIf99H66hA","njdPhdjaOog1NIf99H66hA","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",10,10,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("PtrE61GIfb7TLxX-lQ6Y0A","PtrE61GIfb7TLxX-lQ6Y0A","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        meta.add(createMeta("q-oBOyB2f5mZSY_Ah873SA","q-oBOyB2f5mZSY_Ah873SA","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("QHkvVRI1KV7u1UNLLEKQrQ","QHkvVRI1KV7u1UNLLEKQrQ","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
//        meta.add(createMeta("u6q6QsakhsAvlHPOpf43dg","u6q6QsakhsAvlHPOpf43dg","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",16,16,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
//        meta.add(createMeta("whIGEWQGeEKHy2LfXN5X6w","whIGEWQGeEKHy2LfXN5X6w","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
//        meta.add(createMeta("yp-DPmxuNu-Hi2mpiGPTTQ","yp-DPmxuNu-Hi2mpiGPTTQ","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
//        return meta;
//    }
    
//    private List<StudyActivityEvent> getEvents() {
//        List<StudyActivityEvent> events = new ArrayList<>();
//        events.add(createEvent("created_on", "2021-10-10T16:54:59.688Z"));
//        events.add(createEvent("enrollment", "2021-10-10T16:55:29.653Z"));
//        events.add(createEvent("custom:Clinic Visit", "2021-12-07T23:56:25.179Z"));
//        events.add(createEvent("study_burst:Main Sequence:01", "2021-12-07T23:56:25.179Z"));
//        events.add(createEvent("study_burst:Main Sequence:02", "2021-12-14T23:56:25.179Z"));
//        events.add(createEvent("study_burst:Main Sequence:03", "2021-12-21T23:56:25.179Z"));
//        return events;
//    }

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
