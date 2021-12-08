package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class EventStreamAdherenceReportGeneratorTest {
    
    private static final DateTime NOW = DateTime.parse("2021-10-15T08:13:47.345-07:00");
    private static final DateTime STARTED_ON = CREATED_ON;
    private static final DateTime FINISHED_ON = MODIFIED_ON;
    
    @Test
    public void getEventDaysAdherenceReport_noInputs() throws Exception {
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder()
                .withMetadata(ImmutableList.of())
                .withEvents(ImmutableList.of())
                .withAdherenceRecords(ImmutableList.of())
                .withNow(NOW)
                .withShowActive(false).build();

        EventStreamAdherenceReport reports = generator.generate();
        assertTrue(reports.getStreams().isEmpty());
    }
    
    @Test
    public void getEventDaysAdherenceReport_noAdherence() throws Exception {
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder()
                .withMetadata(getMetadata())
                .withEvents(getEvents())
                .withAdherenceRecords(ImmutableList.of())
                .withNow(NOW)
                .withShowActive(false).build();
        
        EventStreamAdherenceReport report = generator.generate();
        
        Set<String> sessionNames = report.getStreams().stream()
                .flatMap(stream -> stream.getByDayEntries().values().stream())
                .flatMap(days -> days.stream())
                .map(EventStreamDay::getSessionName)
                .collect(Collectors.toSet());

        // Session #3 is persistent, so it does not exist in the output. 
        assertEquals(report.getAdherencePercent(), 100);
        assertEquals(sessionNames, ImmutableSet.of("Session #1", "Session #2"));
        assertEquals(getReportStates(report), ImmutableSet.of(NOT_APPLICABLE, NOT_YET_AVAILABLE));
    }

    @Test
    public void getEventDaysAdherenceReport_future() throws Exception {
        // going in to the future with no adherence records, the participant has expired sessions.
        DateTime now = NOW.plusWeeks(10);
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder()
                .withMetadata(getMetadata())
                .withEvents(getEvents())
                .withAdherenceRecords(ImmutableList.of())
                .withNow(now)
                .withShowActive(false).build();
        
        EventStreamAdherenceReport report = generator.generate();
        
        // Session #3 is persistent, so it does not exist in the output. 
        assertEquals(report.getAdherencePercent(), 0);
        assertEquals(getReportStates(report), ImmutableSet.of(NOT_APPLICABLE, NOT_YET_AVAILABLE, EXPIRED));
    }
    
    @Test
    public void getEventDaysAdherenceReport_past() throws Exception {
        // going in to the future with no adherence records, the participant has expired sessions.
        DateTime now = NOW.minusWeeks(10);
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder()
                .withMetadata(getMetadata())
                .withEvents(getEvents())
                .withAdherenceRecords(ImmutableList.of())
                .withNow(now)
                .withShowActive(false).build();
        
        EventStreamAdherenceReport report = generator.generate();
        
        // Session #3 is persistent, so it does not exist in the output. 
        assertEquals(report.getAdherencePercent(), 100);
        assertEquals(getReportStates(report), ImmutableSet.of(NOT_APPLICABLE, NOT_YET_AVAILABLE));
    }
    
    @Test
    public void getEventDaysAdherenceReport() throws Exception {
        List<AdherenceRecord> adherenceRecords = new ArrayList<>();
        adherenceRecords.add(ar(STARTED_ON, FINISHED_ON, "5dg79dBgLTIX2MN5VhXEwA", false));
        
        EventStreamAdherenceReportGenerator generator = new EventStreamAdherenceReportGenerator.Builder()
                .withMetadata(getMetadata())
                .withEvents(getEvents())
                .withAdherenceRecords(adherenceRecords)
                .withNow(NOW)
                .withShowActive(false).build();
        
        EventStreamAdherenceReport report = generator.generate();
        assertEquals(report.getStreams().get(0).getByDayEntries()
                .get(0).get(0).getTimeWindows().get(1).getState(), COMPLETED);
    }
    
    private TimelineMetadata tm(String guid, String sessionInstanceGuid, String sessionGuid, String timeWindowGuid, 
            Integer sessionInstanceStartDay, Integer sessionInstanceEndDay, String sessionStartEventId,
            String studyBurstId, Integer studyBurstNum, String sessionSymbol, String sessionName, boolean timeWindowPersistent) {
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

    private List<TimelineMetadata> getMetadata() {
        List<TimelineMetadata> meta = new ArrayList<>();
        meta.add(tm("2G-B57I2kgwlxePcfp9kyA","2G-B57I2kgwlxePcfp9kyA","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",13,13,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("4ahNsBcGxSs34xZVacXXQg","4ahNsBcGxSs34xZVacXXQg","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        meta.add(tm("5dg79dBgLTIX2MN5VhXEwA","5dg79dBgLTIX2MN5VhXEwA","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("6I8fHOj6DPMbr-CtISJiLg","6I8fHOj6DPMbr-CtISJiLg","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("AT1TuKMsZhQX5EW8C5r-7g","AT1TuKMsZhQX5EW8C5r-7g","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",7,7,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("bsUZ3oIgmXSAbiIOIs1QWg","bsUZ3oIgmXSAbiIOIs1QWg","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("D-GERcPdJofqgIQGpqt4fA","D-GERcPdJofqgIQGpqt4fA","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("Dl4HCj5YcS4ry9XJ_LgZdg","Dl4HCj5YcS4ry9XJ_LgZdg","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",19,19,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("e-9lNQC2y3lE5n-aqJ6AKQ","e-9lNQC2y3lE5n-aqJ6AKQ","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("EkIF2rZmKor9Bqrt-6X7OQ","EkIF2rZmKor9Bqrt-6X7OQ","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",4,4,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("fTnAnasmcs-UndPt60iX8w","fTnAnasmcs-UndPt60iX8w","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("gnescr0HRz5T2JEjc0Ad6Q","gnescr0HRz5T2JEjc0Ad6Q","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("gV8vn0AtRkiWzzpHlqpHQw","gV8vn0AtRkiWzzpHlqpHQw","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        meta.add(tm("JOQg4yz0lrif7V3HYYzACw","JOQg4yz0lrif7V3HYYzACw","Bw7z_QMiGeuQDVSk_Ndo-Gp-","yxSek2gkA5tHFQRTXafsqrzX",0,0,"created_on", null, null, null,"Session #3 - window is persistent", true));
        meta.add(tm("jUSgc2bo9bgrfA_fdQ5s6Q","jUSgc2bo9bgrfA_fdQ5s6Q","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",1,1,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("KkpuXNUdtapDvmCwbfaV1A","KkpuXNUdtapDvmCwbfaV1A","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("LRCQgu855OC0W6sroFk17Q","LRCQgu855OC0W6sroFk17Q","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",2,2,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        meta.add(tm("MhG2IVYTO8RpqhcaXrlfzA","MhG2IVYTO8RpqhcaXrlfzA","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",4,4,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("misJxNwbXcYJ70OLLgMBgg","misJxNwbXcYJ70OLLgMBgg","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",4,4,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        meta.add(tm("njdPhdjaOog1NIf99H66hA","njdPhdjaOog1NIf99H66hA","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",10,10,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("PtrE61GIfb7TLxX-lQ6Y0A","PtrE61GIfb7TLxX-lQ6Y0A","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        meta.add(tm("q-oBOyB2f5mZSY_Ah873SA","q-oBOyB2f5mZSY_Ah873SA","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",2,2,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("QHkvVRI1KV7u1UNLLEKQrQ","QHkvVRI1KV7u1UNLLEKQrQ","u90_okqrmPgKptcc9E8lORwC","ksuWqp17x3i9zjQBh0FHSDS2",0,0,"study_burst:Main Sequence:03","Main Sequence",3,"*","Session #1", false));
        meta.add(tm("u6q6QsakhsAvlHPOpf43dg","u6q6QsakhsAvlHPOpf43dg","tA4NqFZxRFhrD6mOX2252ixQ","Rg5d3MCyDR6O-i8XEeUo0_PT",16,16,"study_burst:Secondary Sequence:01","Secondary Sequence",1,null,"Session #2", false));
        meta.add(tm("whIGEWQGeEKHy2LfXN5X6w","whIGEWQGeEKHy2LfXN5X6w","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:01","Main Sequence",1,"*","Session #1", false));
        meta.add(tm("yp-DPmxuNu-Hi2mpiGPTTQ","yp-DPmxuNu-Hi2mpiGPTTQ","u90_okqrmPgKptcc9E8lORwC","yMujDPxJ_WO0XT412s4uB7W8",0,0,"study_burst:Main Sequence:02","Main Sequence",2,"*","Session #1", false));
        return meta;
    }
    
    private List<StudyActivityEvent> getEvents() {
        List<StudyActivityEvent> events = new ArrayList<>();
        events.add(createEvent("created_on", "2021-10-10T16:54:59.688Z"));
        events.add(createEvent("enrollment", "2021-10-10T16:55:29.653Z"));
        events.add(createEvent("custom:Clinic Visit", "2021-12-07T23:56:25.179Z"));
        events.add(createEvent("study_burst:Main Sequence:01", "2021-12-07T23:56:25.179Z"));
        events.add(createEvent("study_burst:Main Sequence:02", "2021-12-14T23:56:25.179Z"));
        events.add(createEvent("study_burst:Main Sequence:03", "2021-12-21T23:56:25.179Z"));
        return events;
    }
    
    private StudyActivityEvent createEvent(String eventId, String timestamp) {
        return new StudyActivityEvent.Builder().withEventId(eventId).withTimestamp(DateTime.parse(timestamp)).build();
    }
    
    private Set<SessionCompletionState> getReportStates(EventStreamAdherenceReport report) { 
        return report.getStreams().stream()
            .flatMap(stream -> stream.getByDayEntries().values().stream())
            .flatMap(days -> days.stream())
            .flatMap(day -> day.getTimeWindows().stream())
            .map(EventStreamWindow::getState)
            .collect(toSet());
    }
    
    private AdherenceRecord ar(DateTime startedOn, DateTime finishedOn, String guid, boolean declined) {
        AdherenceRecord rec = new AdherenceRecord();
        rec.setStartedOn(startedOn);
        rec.setFinishedOn(finishedOn);
        rec.setInstanceGuid(guid);
        rec.setDeclined(declined);
        return rec;
    }
}
