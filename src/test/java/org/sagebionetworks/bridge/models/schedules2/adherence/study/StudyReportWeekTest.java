package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class StudyReportWeekTest {

    @Test
    public void canSerialze() throws Exception {
        WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow();
        row.setLabel("labelRow");
        
        EventStreamDay day = new EventStreamDay();
        day.setLabel("dayLabel");
        
        StudyReportWeek week = new StudyReportWeek();
        week.setWeekInStudy(7);
        week.setStartDate(LocalDate.parse("2022-02-10"));
        week.setAdherencePercent(36);
        week.getByDayEntries().putAll(ImmutableMap.of(5, ImmutableList.of(day)));
        week.getSearchableLabels().addAll(ImmutableSet.of("label"));
        week.getRows().addAll(ImmutableList.of(row));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(week);
        assertEquals(node.size(), 6);
        assertEquals(node.get("weekInStudy").intValue(), 7);
        assertEquals(node.get("startDate").textValue(), "2022-02-10");
        assertEquals(node.get("adherencePercent").intValue(), 36);
        assertEquals(node.get("rows").get(0).get("label").textValue(), "labelRow");
        assertEquals(node.get("rows").get(0).get("type").textValue(), "WeeklyAdherenceReportRow");
        assertEquals(node.get("byDayEntries").get("5").get(0).get("label").textValue(), "dayLabel");
        assertEquals(node.get("byDayEntries").get("5").get(0).get("type").textValue(), "EventStreamDay");
        assertEquals(node.get("type").textValue(), "StudyReportWeek");
    }
    
    @Test
    public void visitDays() {
        StudyReportWeek week = new StudyReportWeek();
        
        EventStreamDay day1 = new EventStreamDay();
        EventStreamDay day2 = new EventStreamDay();

        week.getByDayEntries().putAll(ImmutableMap.of(2, ImmutableList.of(day1)));
        week.getByDayEntries().putAll(ImmutableMap.of(5, ImmutableList.of(day1, day2)));
        
        List<EventStreamDay> days = new ArrayList<>();
        week.visitDays((day, count) -> {
            days.add(day);
            day.setLabel(count+"");
        });
        assertEquals(days.size(), 3);
        assertEquals(days.get(0).getLabel(), "0"); // zero position
        assertEquals(days.get(1).getLabel(), "0"); // day1 again in zero position
        assertEquals(days.get(2).getLabel(), "1"); // day2 in position 1
    }
    
    @Test
    public void copy() {
        WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow();
        row.setLabel("labelRow");
        row.setSearchableLabel("searchableLabel");
        row.setSessionGuid("sessionGuid");
        row.setStartEventId("startEventId");
        row.setSessionName("sessionName");
        row.setSessionSymbol("sessionSymbol");
        row.setWeekInStudy(3);
        row.setStudyBurstId("studyBurstId");
        row.setStudyBurstNum(6);
        
        EventStreamDay day = new EventStreamDay();
        day.setLabel("dayLabel");
        
        StudyReportWeek week = new StudyReportWeek();
        week.setWeekInStudy(7);
        week.setStartDate(LocalDate.parse("2022-02-10"));
        week.setAdherencePercent(36);
        week.getByDayEntries().putAll(ImmutableMap.of(5, ImmutableList.of(day)));
        week.getSearchableLabels().addAll(ImmutableSet.of("label"));
        week.getRows().addAll(ImmutableList.of(row));
        
        StudyReportWeek copy = week.copy();
        assertEquals(copy.getWeekInStudy(), 7);
        assertEquals(copy.getAdherencePercent(), Integer.valueOf(36));
        assertEquals(copy.getByDayEntries().size(), 7);
        assertTrue(copy.getByDayEntries().get(0).isEmpty());
        assertTrue(copy.getByDayEntries().get(1).isEmpty());
        assertTrue(copy.getByDayEntries().get(2).isEmpty());
        assertTrue(copy.getByDayEntries().get(3).isEmpty());
        assertTrue(copy.getByDayEntries().get(4).isEmpty());
        assertTrue(copy.getByDayEntries().get(6).isEmpty());
        
        assertEquals(copy.getByDayEntries().get(5).size(), 1);
        List<EventStreamDay> days = copy.getByDayEntries().get(5);
        assertNotSame(copy.getByDayEntries(), week.getByDayEntries());
        
        EventStreamDay copyDay = days.get(0);
        assertEquals(copyDay.getLabel(), "dayLabel");
        assertNotSame(copyDay, day);
        
        List<WeeklyAdherenceReportRow> copyRows = copy.getRows();
        assertNotSame(copyRows, week.getRows());
        
        WeeklyAdherenceReportRow copyRow = copyRows.get(0);
        assertEquals(copyRow.getLabel(), row.getLabel());
        assertEquals(copyRow.getSearchableLabel(), row.getSearchableLabel());
        assertEquals(copyRow.getSessionGuid(), row.getSessionGuid());
        assertEquals(copyRow.getStartEventId(), row.getStartEventId());
        assertEquals(copyRow.getSessionName(), row.getSessionName());
        assertEquals(copyRow.getSessionSymbol(), row.getSessionSymbol());
        assertEquals(copyRow.getWeekInStudy(), row.getWeekInStudy());
        assertEquals(copyRow.getStudyBurstId(), row.getStudyBurstId());
        assertEquals(copyRow.getStudyBurstNum(), row.getStudyBurstNum());
    }
}
