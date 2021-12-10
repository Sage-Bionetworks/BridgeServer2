package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class EventStreamTest {
    
    @Test
    public void canSerialize() throws Exception {
        EventStream stream = new EventStream();
        stream.setStartEventId("startEventId");
        stream.setEventTimestamp(CREATED_ON);
        stream.setDaysSinceEvent(5);
        stream.setStudyBurstId("studyBurstId");
        stream.setStudyBurstNum(2);
        stream.addEntry(6, makeDay("aaa"));
        stream.addEntry(6, makeDay("bbb"));
        stream.addEntry(8, makeDay("aaa"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(stream);
        assertEquals(node.size(), 8);
        assertEquals(node.get("startEventId").textValue(), "startEventId");
        assertEquals(node.get("eventTimestamp").textValue(), CREATED_ON.toString());
        assertEquals(node.get("daysSinceEvent").intValue(), 5);
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("studyBurstNum").intValue(), 2);
        assertEquals(node.get("sessionGuids").size(), 2);
        assertEquals(node.get("sessionGuids").get(0).textValue(), "aaa");
        assertEquals(node.get("sessionGuids").get(1).textValue(), "bbb");
        assertEquals(node.get("byDayEntries").get("6").get(0).get("type").textValue(), "EventStreamDay");
        assertEquals(node.get("byDayEntries").get("6").get(1).get("type").textValue(), "EventStreamDay");
        assertEquals(node.get("byDayEntries").get("8").get(0).get("type").textValue(), "EventStreamDay");
        
        EventStream deser = BridgeObjectMapper.get().readValue(node.toString(), EventStream.class);
        assertEquals(deser.getStartEventId(), "startEventId");
        assertEquals(deser.getDaysSinceEvent(), Integer.valueOf(5));
        assertEquals(deser.getStudyBurstId(), "studyBurstId");
        assertEquals(deser.getStudyBurstNum(), Integer.valueOf(2));
        assertEquals(deser.getByDayEntries().size(), 2);
        assertEquals(deser.getByDayEntries().get(6).size(), 2);
        assertEquals(deser.getByDayEntries().get(8).size(), 1);
    }
    
    @Test
    public void retrieveEventStream() {
        EventStreamDay day = new EventStreamDay();
        day.setSessionGuid("sessionGuid");
        day.setSessionName("sessionName");
        day.setSessionSymbol("sessionSymbol");
        day.setStartDay(3);
        day.setStartDate(LocalDate.parse("2021-10-01"));
        day.setWeek(5);
        day.setStudyBurstId("studyBurstId");
        day.setStudyBurstNum(2);
        day.addTimeWindow(new EventStreamWindow());
        
        EventStream stream = new EventStream();
        stream.setStartEventId("startEventId");
        stream.setDaysSinceEvent(5);
        stream.setStudyBurstId("studyBurstId");
        stream.setStudyBurstNum(2);
        stream.addEntry(6, day);
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setGuid("sessionInstanceGuid");
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        meta.setAssessmentGuid("assessmentGuid");
        meta.setAssessmentId("assessmentId");
        meta.setAssessmentRevision(10);
        meta.setSessionInstanceGuid("sessionInstanceGuid");
        meta.setSessionGuid("sessionGuid");
        meta.setSessionInstanceStartDay(3);
        meta.setSessionInstanceEndDay(4);
        meta.setScheduleGuid("scheduleGuid");
        meta.setSessionStartEventId("sessionStartEventId");
        meta.setTimeWindowGuid("timeWindowGuid");
        meta.setAppId("appId");
        meta.setStudyBurstId("studyBurstId");
        meta.setStudyBurstNum(2);
        meta.setSessionName("sessionName");
        meta.setSessionSymbol("sessionSymbol");
    }
    
    @Test
    public void daysSinceEventHandlesNulls() {
        EventStream stream = new EventStream();
        assertNull(stream.getDaysSinceEvent());
    }

    @Test
    public void daysSinceEventHandlesNegative() {
        EventStream stream = new EventStream();
        stream.setDaysSinceEvent(-1);
        assertNull(stream.getDaysSinceEvent());
    }
    
    private EventStreamDay makeDay(String guid) {
        EventStreamDay day = new EventStreamDay();
        day.setSessionGuid(guid);
        return day;
    }
}
