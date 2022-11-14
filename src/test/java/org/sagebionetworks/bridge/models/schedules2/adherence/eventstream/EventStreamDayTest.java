package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import java.util.List;

public class EventStreamDayTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(EventStreamDay.class)
            .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        EventStreamDay day = new EventStreamDay();
        day.setLabel("Label");
        day.setSessionGuid("sessionGuid");
        day.setSessionName("sessionName");
        day.setSessionSymbol("sessionSymbol");
        day.setStartEventId("event1");
        day.setStartDay(3);
        day.setStartDate(LocalDate.parse("2021-10-01"));
        day.setWeek(5);
        day.setStudyBurstId("studyBurstId");
        day.setStudyBurstNum(2);
        day.addTimeWindow(createWindow("guid1"));
        day.setTimeWindows(ImmutableList.of(createWindow("guid2"), createWindow("guid1")));
        day.setToday(true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(day);
        assertEquals(node.size(), 13);
        assertEquals(node.get("label").textValue(), "Label");
        assertEquals(node.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(node.get("sessionName").textValue(), "sessionName");
        assertEquals(node.get("sessionSymbol").textValue(), "sessionSymbol");
        assertEquals(node.get("startEventId").textValue(), "event1");
        assertEquals(node.get("week").intValue(), 5);
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("studyBurstNum").intValue(), 2);
        assertEquals(node.get("startDay").intValue(), 3);
        assertEquals(node.get("startDate").textValue(), "2021-10-01");
        assertEquals(node.get("timeWindows").size(), 2);
        assertTrue(node.get("today").booleanValue());
        assertEquals(node.get("type").textValue(), "EventStreamDay");
        
        EventStreamDay deser = BridgeObjectMapper.get().readValue(node.toString(), EventStreamDay.class);
        assertEquals(deser.getSessionGuid(), "sessionGuid");
        assertEquals(deser.getSessionName(), "sessionName");
        assertEquals(deser.getSessionSymbol(), "sessionSymbol");
        assertEquals(deser.getStartEventId(), "event1");
        assertEquals(deser.getWeek(), Integer.valueOf(5));
        assertEquals(deser.getStudyBurstId(), "studyBurstId");
        assertEquals(deser.getStudyBurstNum(), Integer.valueOf(2));
        assertEquals(deser.getStartDay(), Integer.valueOf(3));
        assertEquals(deser.getStartDate(), LocalDate.parse("2021-10-01"));
        assertEquals(deser.getTimeWindows().size(), 2);
        assertEquals(deser.getTimeWindows().stream()
                .map(EventStreamWindow::getTimeWindowGuid)
                .collect(toSet()), ImmutableSet.of("guid1", "guid2"));
        assertTrue(deser.isToday());
    }
    
    @Test
    public void setTimeWindowsHandlesNulls() { 
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(null);
        assertEquals(day.getTimeWindows().size(), 0);
    }
    
    @Test
    public void copy() {
        EventStreamDay day = new EventStreamDay();
        day.setLabel("Label");
        day.setSessionGuid("sessionGuid");
        day.setSessionName("sessionName");
        day.setSessionSymbol("sessionSymbol");
        day.setStartEventId("event1");
        day.setStartDay(3);
        day.setStartDate(LocalDate.parse("2021-10-01"));
        day.setWeek(5);
        day.setStudyBurstId("studyBurstId");
        day.setStudyBurstNum(2);
        day.addTimeWindow(createWindow("guid1"));
        day.setTimeWindows(ImmutableList.of(createWindow("guid2"), createWindow("guid1")));
        day.setToday(true);

        EventStreamDay copy = day.copy();
        assertEquals(copy.getLabel(), day.getLabel());
        assertEquals(copy.getSessionGuid(), day.getSessionGuid());
        assertEquals(copy.getSessionName(), day.getSessionName());
        assertEquals(copy.getSessionSymbol(), day.getSessionSymbol());
        assertEquals(copy.getStartEventId(), day.getStartEventId());
        assertEquals(copy.getStartDay(), day.getStartDay());
        assertEquals(copy.getStartDate(), day.getStartDate());
        assertEquals(copy.getWeek(), day.getWeek());
        assertEquals(copy.getStudyBurstId(), day.getStudyBurstId());
        assertEquals(copy.getStudyBurstNum(), day.getStudyBurstNum());
        assertEquals(copy.isToday(), day.isToday());
        
        assertEquals(copy.getTimeWindows().size(), day.getTimeWindows().size());
        compareWindows(copy.getTimeWindows().get(0), day.getTimeWindows().get(0));
        compareWindows(copy.getTimeWindows().get(1), day.getTimeWindows().get(1));
    }
    
    @Test
    public void getTimeWindows_SortByStartDate() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-03"), LocalTime.parse("18:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-02"), LocalTime.parse("19:00"),
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win2, win1);
    }
    
    @Test
    public void getTimeWindows_SortByStartDateNullsLast() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-03"), LocalTime.parse("18:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", null, LocalTime.parse("19:00"),
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win1, win2);
    }
    
    @Test
    public void getTimeWindows_SortByStartTime() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("19:00"),
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("18:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win2, win1);
    }
    
    @Test
    public void getTimeWindows_SortByStartTimeNullsLast() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), null,
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("18:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win1, win2);
    }
    
    @Test
    public void getTimeWindows_SortByEndDate() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win1, win2, win3);
    }
    
    @Test
    public void getTimeWindows_SortByEndDateNullsLast() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                null, LocalTime.parse("18:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-05"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-06"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win2, win3, win1);
    }
    
    @Test
    public void getTimeWindows_SortByEndTime() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("20:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("19:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win2, win1);
    }
    
    @Test
    public void getTimeWindows_SortByEndTimeNullsLast() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("20:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), null);
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("18:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win3, win1, win2);
    }
    
    @Test
    public void getTimeWindows_SortByGuid() {
        EventStreamWindow win1 = createWindow("win1", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("20:00"));
        EventStreamWindow win2 = createWindow("win2", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("20:00"));
        EventStreamWindow win3 = createWindow("win3", LocalDate.parse("2020-01-01"), LocalTime.parse("20:00"),
                LocalDate.parse("2020-01-04"), LocalTime.parse("20:00"));
        
        EventStreamDay day = new EventStreamDay();
        day.setTimeWindows(ImmutableList.of(win1, win2, win3));
        assertWindowSortOrder(day, win1, win2, win3);
    }
    
    private void compareWindows(EventStreamWindow win1, EventStreamWindow win2) {
        assertEquals(win1.getTimeWindowGuid(), win2.getTimeWindowGuid());
        assertEquals(win1.getState(), win2.getState());
        assertEquals(win1.getSessionInstanceGuid(), win2.getSessionInstanceGuid());
        assertEquals(win1.getEndDay(), win2.getEndDay());
        assertEquals(win1.getEndDate(), win2.getEndDate());
        assertEquals(win1.getEndTime(), win2.getEndTime());
        assertEquals(win1.getStartTime(), win2.getStartTime());
        assertEquals(win1.getStartDate(), win2.getStartDate());
    }
    
    private EventStreamWindow createWindow(String guid) {
        EventStreamWindow window = new EventStreamWindow();
        window.setTimeWindowGuid(guid);
        return window;
    }
    
    private EventStreamWindow createWindow(String guid, LocalDate startDate, LocalTime startTime,
                                           LocalDate endDate, LocalTime endTime) {
        EventStreamWindow window = new EventStreamWindow();
        window.setTimeWindowGuid(guid);
        window.setStartDate(startDate);
        window.setStartTime(startTime);
        window.setEndDate(endDate);
        window.setEndTime(endTime);
        return window;
    }
    
    private void assertWindowSortOrder(EventStreamDay day, EventStreamWindow win1, EventStreamWindow win2,
                                 EventStreamWindow win3) {
        List<EventStreamWindow> windows = day.getTimeWindows();
        compareWindows(windows.get(0), win1);
        compareWindows(windows.get(1), win2);
        compareWindows(windows.get(2), win3);
    }

}
