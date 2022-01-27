package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.ADHERENCE_STATE_EVENT_TS1;
import static org.sagebionetworks.bridge.TestConstants.ADHERENCE_STATE_EVENT_TS2;
import static org.sagebionetworks.bridge.TestConstants.ADHERENCE_STATE_NOW;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportGenerator.ROW_COMPARATOR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class WeeklyAdherenceReportGeneratorTest extends Mockito {
    
    @Test
    public void canSerialize() throws Exception { 
        AdherenceState state = TestUtils.getAdherenceStateBuilder().build();
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);

        // Because these are different from what is serialized in the JSON, test them here.
        assertEquals(report.getSearchableLabels(), ImmutableSet.of(":session3:Week 1:", 
                ":session1:Week 2:", ":burst 2:Week 1:session2:"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        
        assertEquals(node.get("createdOn").textValue(), 
                ADHERENCE_STATE_NOW.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)).toString());
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("weeklyAdherencePercent").intValue(), 33);
        assertEquals(node.get("byDayEntries").size(), 7);
        
        ArrayNode entry1 = (ArrayNode)node.get("byDayEntries").get("0");
        assertEquals(entry1.size(), 3);
        JsonNode dayNode1 = entry1.get(1);
        assertNull(dayNode1.get("label"));
        assertEquals(dayNode1.get("sessionGuid").textValue(), "guid1");
        assertNull(dayNode1.get("sessionName"));
        assertNull(dayNode1.get("sessionSymbol"));
        assertNull(dayNode1.get("week"));
        assertEquals(dayNode1.get("startDay").intValue(), 1);
        assertEquals(dayNode1.get("startDate").textValue(), "2015-01-27");
        
        JsonNode win1 = dayNode1.get("timeWindows").get(0);
        assertEquals(win1.get("sessionInstanceGuid").textValue(), "instanceGuid1");
        assertEquals(win1.get("state").textValue(), "completed");
        assertEquals(win1.get("endDay").intValue(), 15);
        assertEquals(win1.get("endDate").textValue(), "2015-02-10");
        
        ArrayNode entry2 = (ArrayNode)node.get("byDayEntries").get("2");
        assertEquals(entry2.size(), 3);
        
        JsonNode dayNode2_0 = entry2.get(0);
        assertNull(dayNode2_0.get("label"));
        assertEquals(dayNode2_0.get("sessionGuid").textValue(), "guid2");
        assertNull(dayNode2_0.get("sessionName"));
        assertNull(dayNode2_0.get("sessionSymbol"));
        assertNull(dayNode2_0.get("week"));
        assertNull(dayNode2_0.get("studyBurstId"));
        assertNull(dayNode2_0.get("studyBurstNum"));
        assertEquals(dayNode2_0.get("startDay").intValue(), 2);
        assertEquals(dayNode2_0.get("startDate").textValue(), "2015-02-02");

        JsonNode win2 = dayNode2_0.get("timeWindows").get(0);
        assertEquals(win2.get("sessionInstanceGuid").textValue(), "instanceGuid2");
        assertEquals(win2.get("state").textValue(), "unstarted");
        assertEquals(win2.get("endDay").intValue(), 16);
        assertEquals(win2.get("endDate").textValue(), "2015-02-16");

        JsonNode dayNode2_1 = entry2.get(1);
        assertEquals(dayNode2_1.size(), 2);
        assertEquals(dayNode2_1.get("timeWindows").size(), 0);
        assertEquals(dayNode2_1.get("type").textValue(), "EventStreamDay");
        
        JsonNode dayNode2_2 = entry2.get(2);
        assertEquals(dayNode2_2.size(), 2);
        assertEquals(dayNode2_2.get("timeWindows").size(), 0);
        assertEquals(dayNode2_2.get("type").textValue(), "EventStreamDay");
        
        JsonNode entry3 = (ArrayNode)node.get("byDayEntries").get("3");
        assertEquals(entry3.size(), 3);
        JsonNode dayNode3 = entry3.get(2);
        assertEquals(dayNode3.get("sessionGuid").textValue(), "guid3");
        assertEquals(dayNode3.get("startDay").intValue(), 3);
        assertEquals(dayNode3.get("startDate").textValue(), "2015-02-03");
        
        JsonNode win3 = dayNode3.get("timeWindows").get(0);
        assertEquals(win3.get("sessionInstanceGuid").textValue(), "instanceGuid3");
        assertEquals(win3.get("state").textValue(), "expired");
        assertEquals(win3.get("endDay").intValue(), 4);
        assertEquals(win3.get("endDate").textValue(), "2015-02-04");
    }
    
    @Test
    public void testNulls() {
        AdherenceState state = new AdherenceState.Builder()
                .withNow(CREATED_ON)
                .build();
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);
        
        assertEquals(report.getCreatedOn(), CREATED_ON);
        assertEquals(report.getWeeklyAdherencePercent(), 100);
        assertNotNull(report.getByDayEntries());
        assertTrue(report.getByDayEntries().get(0).isEmpty());
        assertTrue(report.getByDayEntries().get(1).isEmpty());
        assertTrue(report.getByDayEntries().get(2).isEmpty());
        assertTrue(report.getByDayEntries().get(3).isEmpty());
        assertTrue(report.getByDayEntries().get(4).isEmpty());
        assertTrue(report.getByDayEntries().get(5).isEmpty());
        assertTrue(report.getByDayEntries().get(6).isEmpty());
    }
    
    @Test
    public void nextActivity() {
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        
        TimelineMetadata meta4 = new TimelineMetadata();
        //meta4.setSessionInstanceGuid("instanceGuid4");
        meta4.setSessionStartEventId("event1");
        meta4.setSessionGuid("guid4");
        meta4.setSessionInstanceStartDay(20);
        meta4.setSessionInstanceEndDay(25);
        meta4.setSessionName("session4");
        meta4.setSessionSymbol("4");
        // Use just this, which is in the future
        builder.withMetadata(ImmutableList.of(meta4));
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        assertEquals(report.getWeeklyAdherencePercent(), 100);
        
        NextActivity na = report.getNextActivity();
        assertEquals(na.getSessionGuid(), "guid4");
        assertEquals(na.getSessionName(), "session4");
        assertEquals(na.getSessionSymbol(), "4");
        assertEquals(na.getWeek(), Integer.valueOf(2));
        assertEquals(na.getStartDate().toString(), "2015-02-15");
    }
    
    // These are items in the event stream that don't apply to this user because the origin
    // events are missing from the user’s events. This should not generate an NPE and it 
    // should find the next activity that *does* apply to this user.
    @Test
    public void nextActivity_nullStartDates() throws Exception { 
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("event1")
                .withTimestamp(ADHERENCE_STATE_EVENT_TS1.plusDays(100))
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("event2")
                .withTimestamp(ADHERENCE_STATE_EVENT_TS2.plusDays(100))
                .build();
        builder.withEvents(ImmutableList.of(e1, e2));
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        // The next is from session 1
        assertEquals(report.getNextActivity().getSessionName(), "session1");
        
        builder.withEvents(ImmutableList.of(e2));
        report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        // Now it's from session 2
        assertEquals(report.getNextActivity().getSessionName(), "session2");
    }
    
    @Test
    public void participantHasNotTriggeredEventsYet() {
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        
        // Only include one event, which is in the future, and the other is null
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("event1")
                .withTimestamp(ADHERENCE_STATE_EVENT_TS1.plusDays(50))
                .build();
        builder.withEvents(ImmutableList.of(event));
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        
        NextActivity na = report.getNextActivity();
        assertEquals(na.getSessionGuid(), "guid1");
        assertEquals(na.getSessionName(), "session1");
        assertEquals(na.getSessionSymbol(), "1");
        assertEquals(na.getWeek(), Integer.valueOf(0));
        assertEquals(na.getStartDate().toString(), "2015-03-18");
    }

    @Test
    public void participantHasFinishedStudy() {
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        builder.withNow(ADHERENCE_STATE_NOW.plusDays(100));
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        
        assertEquals(report.getCreatedOn(), ADHERENCE_STATE_NOW.plusDays(100)
                .withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(report.getWeeklyAdherencePercent(), 100);
    }
    
    @Test
    public void sortRowsLabelsOnly() {
        WeeklyAdherenceReportRow r1 = new WeeklyAdherenceReportRow();
        r1.setLabel("A");
        WeeklyAdherenceReportRow r2 = new WeeklyAdherenceReportRow();
        r2.setLabel("b");
        WeeklyAdherenceReportRow r3 = new WeeklyAdherenceReportRow();
        r3.setLabel("C");
        
        List<WeeklyAdherenceReportRow> rows = Lists.newArrayList(r3, r2, r1);
        Collections.sort(rows, ROW_COMPARATOR);
        
        assertEquals(rows, ImmutableList.of(r1, r2, r3));
    }
    
    @Test
    public void sortRowsLabelsAndStudyBursts() {
        WeeklyAdherenceReportRow r1 = new WeeklyAdherenceReportRow();
        r1.setLabel("A");
        r1.setStudyBurstId("E");
        WeeklyAdherenceReportRow r2 = new WeeklyAdherenceReportRow();
        r2.setLabel("b");
        r2.setStudyBurstId("D");
        WeeklyAdherenceReportRow r3 = new WeeklyAdherenceReportRow();
        r3.setLabel("C");
        r3.setStudyBurstId("d");
        
        List<WeeklyAdherenceReportRow> rows = Lists.newArrayList(r3, r2, r1);
        Collections.sort(rows, ROW_COMPARATOR);
        
        assertEquals(rows, ImmutableList.of(r2, r3, r1));
    }
    
    @Test
    public void sortRowsStudyBurstsFirst() {
        WeeklyAdherenceReportRow r1 = new WeeklyAdherenceReportRow();
        r1.setLabel("B");
        r1.setStudyBurstId("E");
        WeeklyAdherenceReportRow r2 = new WeeklyAdherenceReportRow();
        r2.setLabel("C");
        r2.setStudyBurstId("D");
        WeeklyAdherenceReportRow r3 = new WeeklyAdherenceReportRow();
        r3.setLabel("A");
        
        List<WeeklyAdherenceReportRow> rows = Lists.newArrayList(r3, r2, r1);
        Collections.sort(rows, ROW_COMPARATOR);
        
        assertEquals(rows, ImmutableList.of(r2, r1, r3));
    }
    
    @Test
    public void sessionTriggeredByMultipleEventsReportedCorrectly() throws JsonProcessingException {
        // Two events triggered at the same time, the schedule will trigger one sesssion
        // off both of these...
        DateTime timestamp = DateTime.now().minusDays(10);
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("custom:Clinic Visit")
                .withTimestamp(timestamp).build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:Survey Returned")
                .withTimestamp(timestamp).build();
        List<StudyActivityEvent> events = ImmutableList.of(e1, e2);
        
        TimelineMetadata m1 = new TimelineMetadata();
        m1.setGuid("AAA");
        m1.setSessionGuid("sessionGuid");
        m1.setSessionInstanceStartDay(1);
        m1.setSessionInstanceEndDay(28);
        m1.setSessionStartEventId("custom:Clinic Visit");
        m1.setSessionName("Session #1");
        
        TimelineMetadata m2 = new TimelineMetadata();
        m2.setGuid("BBB");
        m2.setSessionGuid("sessionGuid");
        m2.setSessionInstanceStartDay(1);
        m2.setSessionInstanceEndDay(28);
        m2.setSessionStartEventId("custom:Survey Returned");
        m2.setSessionName("Session #1");
        List<TimelineMetadata> metadata = ImmutableList.of(m1, m2);
        
        AdherenceState state = new AdherenceState.Builder()
                .withClientTimeZone(TEST_CLIENT_TIME_ZONE)
                .withEvents(events)
                .withMetadata(metadata)
                .withNow(timestamp.plusDays(10))
                .withShowActive(false).build();
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);
        
        // These two rows are exactly the same in all respects and yet they were triggered
        // from different events, and so we are tracking that. And the entries will be aligned
        // correctly.
        WeeklyAdherenceReportRow row1 = report.getRows().get(0);
        assertEquals(row1.getLabel(), "Session #1 / Week 2");
        assertEquals(row1.getSearchableLabel(), ":Session #1:Week 2:");
        assertEquals(row1.getSessionGuid(), "sessionGuid");
        assertEquals(row1.getStartEventId(), "custom:Clinic Visit");
        assertEquals(row1.getSessionName(), "Session #1");
        assertEquals(row1.getWeek(), Integer.valueOf(2));
        
        WeeklyAdherenceReportRow row2 = report.getRows().get(1);
        assertEquals(row2.getLabel(), "Session #1 / Week 2");
        assertEquals(row2.getSearchableLabel(), ":Session #1:Week 2:");
        assertEquals(row2.getSessionGuid(), "sessionGuid");
        assertEquals(row2.getStartEventId(), "custom:Survey Returned");
        assertEquals(row2.getSessionName(), "Session #1");
        assertEquals(row2.getWeek(), Integer.valueOf(2));
        
        List<EventStreamDay> days = report.getByDayEntries().get(0);
        assertEquals(days.size(), 2);
        assertEquals(days.get(0).getStartEventId(), "custom:Clinic Visit");
        assertEquals(days.get(1).getStartEventId(), "custom:Survey Returned");
    }
    
}
