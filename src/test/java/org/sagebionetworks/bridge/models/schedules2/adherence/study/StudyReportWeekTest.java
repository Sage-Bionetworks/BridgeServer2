package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.testng.Assert.assertEquals;

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
        week.setByDayEntries(ImmutableMap.of(5, ImmutableList.of(day)));
        week.setSearchableLabels(ImmutableSet.of("label"));
        week.setRows(ImmutableList.of(row));
        
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
}
