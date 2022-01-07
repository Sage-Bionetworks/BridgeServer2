package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.ADHERENCE_STATE_EVENT_TS1;
import static org.sagebionetworks.bridge.TestConstants.ADHERENCE_STATE_NOW;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

public class WeeklyAdherenceReportGeneratorTest extends Mockito {
    
    @Test
    public void canSerialize() throws Exception { 
        AdherenceState state = TestUtils.getAdherenceStateBuilder().build();
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(state);

        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        assertEquals(node.get("timestamp").textValue(), 
                ADHERENCE_STATE_NOW.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)).toString());
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("weeklyAdherencePercent").intValue(), 33);
        assertEquals(node.get("byDayEntries").size(), 3);
        
        ArrayNode entry1 = (ArrayNode)node.get("byDayEntries").get("0");
        assertEquals(entry1.size(), 1);
        JsonNode dayNode1 = entry1.get(0);
        assertEquals(dayNode1.get("label").textValue(), "session1 / Week 2");
        assertEquals(dayNode1.get("sessionGuid").textValue(), "guid1");
        assertEquals(dayNode1.get("sessionName").textValue(), "session1");
        assertEquals(dayNode1.get("sessionSymbol").textValue(), "1");
        assertEquals(dayNode1.get("week").intValue(), 2);
        assertEquals(dayNode1.get("startDay").intValue(), 1);
        assertEquals(dayNode1.get("startDate").textValue(), "2015-01-27");
        
        JsonNode win1 = dayNode1.get("timeWindows").get(0);
        assertEquals(win1.get("sessionInstanceGuid").textValue(), "instanceGuid1");
        assertEquals(win1.get("state").textValue(), "completed");
        assertEquals(win1.get("endDay").intValue(), 15);
        assertEquals(win1.get("endDate").textValue(), "2015-02-10");
        
        ArrayNode entry2 = (ArrayNode)node.get("byDayEntries").get("2");
        assertEquals(entry2.size(), 1);
        JsonNode dayNode2 = entry2.get(0);
        assertEquals(dayNode2.get("label").textValue(), "burst 2 / Week 1");
        assertEquals(dayNode2.get("sessionGuid").textValue(), "guid2");
        assertEquals(dayNode2.get("sessionName").textValue(), "session2");
        assertEquals(dayNode2.get("sessionSymbol").textValue(), "2");
        assertEquals(dayNode2.get("week").intValue(), 1);
        assertEquals(dayNode2.get("studyBurstId").textValue(), "burst");
        assertEquals(dayNode2.get("studyBurstNum").intValue(), 2);
        assertEquals(dayNode2.get("startDay").intValue(), 2);
        assertEquals(dayNode2.get("startDate").textValue(), "2015-02-02");
        
        JsonNode win2 = dayNode2.get("timeWindows").get(0);
        assertEquals(win2.get("sessionInstanceGuid").textValue(), "instanceGuid2");
        assertEquals(win2.get("state").textValue(), "unstarted");
        assertEquals(win2.get("endDay").intValue(), 16);
        assertEquals(win2.get("endDate").textValue(), "2015-02-16");
        
        JsonNode entry3 = (ArrayNode)node.get("byDayEntries").get("3");
        assertEquals(entry3.size(), 1);
        JsonNode dayNode3 = entry3.get(0);
        assertEquals(dayNode3.get("label").textValue(), "session3 / Week 1");
        assertEquals(dayNode3.get("sessionGuid").textValue(), "guid3");
        assertEquals(dayNode3.get("sessionName").textValue(), "session3");
        assertEquals(dayNode3.get("sessionSymbol").textValue(), "3");
        assertEquals(dayNode3.get("week").intValue(), 1);
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
        
        assertEquals(report.getTimestamp(), CREATED_ON);
        assertEquals(report.getWeeklyAdherencePercent(), 100);
        assertTrue(report.getByDayEntries().isEmpty());
    }
    
    @Test
    public void nextActivity() {
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        
        TimelineMetadata meta4 = new TimelineMetadata();
        meta4.setSessionInstanceGuid("instanceGuid4");
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
        assertTrue(report.getByDayEntries().isEmpty());
        
        NextActivity na = report.getNextActivity();
        assertEquals(na.getSessionGuid(), "guid4");
        assertEquals(na.getSessionName(), "session4");
        assertEquals(na.getSessionSymbol(), "4");
        assertEquals(na.getWeek(), Integer.valueOf(2));
        assertEquals(na.getStartDate().toString(), "2015-02-15");
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
        assertTrue(report.getByDayEntries().isEmpty());
        
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
        assertEquals(report.getTimestamp(), ADHERENCE_STATE_NOW.plusDays(100)
                .withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(report.getWeeklyAdherencePercent(), 100);
        assertTrue(report.getByDayEntries().isEmpty());
    }
    
    // This edge case isn't covered as part of the other tests
    @Test
    public void dayInReportBeforeWeekWindow() {
        AdherenceState.Builder builder = TestUtils.getAdherenceStateBuilder();
        
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("event1")
                .withTimestamp(ADHERENCE_STATE_EVENT_TS1.plusDays(2))
                .build();
        builder.withEvents(ImmutableList.of(event));
        
        TimelineMetadata meta4 = new TimelineMetadata();
        meta4.setSessionInstanceGuid("instanceGuid4");
        meta4.setSessionStartEventId("event1");
        meta4.setSessionGuid("guid4");
        meta4.setSessionInstanceStartDay(0);
        meta4.setSessionInstanceEndDay(0);
        meta4.setSessionName("session4");
        meta4.setSessionSymbol("4");
        builder.withMetadata(ImmutableList.of(meta4));
        
        WeeklyAdherenceReport report = WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        fail("Incomplete");
    }
}
