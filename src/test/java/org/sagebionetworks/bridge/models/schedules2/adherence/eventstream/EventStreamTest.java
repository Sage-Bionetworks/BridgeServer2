package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
    
    @Test
    public void visitDays() {
        EventStream stream = new EventStream();
        
        EventStreamDay day1 = new EventStreamDay();
        EventStreamDay day2 = new EventStreamDay();

        stream.getByDayEntries().putAll(ImmutableMap.of(2, ImmutableList.of(day1)));
        stream.getByDayEntries().putAll(ImmutableMap.of(5, ImmutableList.of(day1, day2)));
        
        List<EventStreamDay> days = new ArrayList<>();
        stream.visitDays((day, count) -> {
            days.add(day);
            day.setLabel(count+"");
        });
        assertEquals(days.size(), 3);
        assertEquals(days.get(0).getLabel(), "0"); // zero position
        assertEquals(days.get(1).getLabel(), "0"); // day1 again in zero position
        assertEquals(days.get(2).getLabel(), "1"); // day2 in position 1
    }
    
    private EventStreamDay makeDay(String guid) {
        EventStreamDay day = new EventStreamDay();
        day.setSessionGuid(guid);
        return day;
    }
}
