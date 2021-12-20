package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class EventStreamDayTest {
    
    @Test
    public void canSerialize() throws Exception {
        EventStreamDay day = new EventStreamDay();
        day.setSessionGuid("sessionGuid");
        day.setSessionName("sessionName");
        day.setSessionSymbol("sessionSymbol");
        day.setStartDay(3);
        day.setStartDate(LocalDate.parse("2021-10-01"));
        day.setWeek(5);
        day.setStudyBurstId("studyBurstId");
        day.setStudyBurstNum(2);
        day.addTimeWindow(createWindow("guid1"));
        day.setTimeWindows(ImmutableList.of(createWindow("guid2"), createWindow("guid1")));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(day);
        assertEquals(node.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(node.get("sessionName").textValue(), "sessionName");
        assertEquals(node.get("sessionSymbol").textValue(), "sessionSymbol");
        assertEquals(node.get("week").intValue(), 5);
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("studyBurstNum").intValue(), 2);
        assertEquals(node.get("startDay").intValue(), 3);
        assertEquals(node.get("startDate").textValue(), "2021-10-01");
        assertEquals(node.get("timeWindows").size(), 2);
        
        EventStreamDay deser = BridgeObjectMapper.get().readValue(node.toString(), EventStreamDay.class);
        assertEquals(deser.getSessionGuid(), "sessionGuid");
        assertEquals(deser.getSessionName(), "sessionName");
        assertEquals(deser.getSessionSymbol(), "sessionSymbol");
        assertEquals(deser.getWeek(), Integer.valueOf(5));
        assertEquals(deser.getStudyBurstId(), "studyBurstId");
        assertEquals(deser.getStudyBurstNum(), Integer.valueOf(2));
        assertEquals(deser.getStartDay(), Integer.valueOf(3));
        assertEquals(deser.getStartDate(), LocalDate.parse("2021-10-01"));
        assertEquals(deser.getTimeWindows().size(), 2);
        assertEquals(deser.getTimeWindows().stream()
                .map(EventStreamWindow::getTimeWindowGuid)
                .collect(toSet()), ImmutableSet.of("guid1", "guid2"));
    }
    
    @Test
    public void setTimeWindowsHandlesNulls() { 
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(null);
        assertEquals(day.getTimeWindows().size(), 0);
    }
    
    private EventStreamWindow createWindow(String guid) {
        EventStreamWindow window = new EventStreamWindow();
        window.setTimeWindowGuid(guid);
        return window;
    }

}
