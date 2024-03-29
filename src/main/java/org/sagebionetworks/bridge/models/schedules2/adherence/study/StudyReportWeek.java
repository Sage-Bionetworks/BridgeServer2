package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "startDate", "endDate", "weekInStudy", "adherencePercent", "rows", "byDayEntries", "type" })
public class StudyReportWeek {
    
    private int weekInStudy;
    private LocalDate startDate;
    private Integer adherencePercent;
    private Map<Integer, List<EventStreamDay>> byDayEntries;
    private Set<String> searchableLabels;
    private List<WeeklyAdherenceReportRow> rows;

    public StudyReportWeek() {
        searchableLabels = new HashSet<>();
        rows = new ArrayList<>();
        byDayEntries = new HashMap<>();
        byDayEntries.put(0, new ArrayList<>());
        byDayEntries.put(1, new ArrayList<>());
        byDayEntries.put(2, new ArrayList<>());
        byDayEntries.put(3, new ArrayList<>());
        byDayEntries.put(4, new ArrayList<>());
        byDayEntries.put(5, new ArrayList<>());
        byDayEntries.put(6, new ArrayList<>());
    }
    
    public int getWeekInStudy() {
        return weekInStudy;
    }
    public void setWeekInStudy(int weekInStudy) {
        this.weekInStudy = weekInStudy;
    }    
    public LocalDate getStartDate() {
        return startDate;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    public Integer getAdherencePercent() {
        return adherencePercent;
    }
    public void setAdherencePercent(Integer adherencePercent) {
        this.adherencePercent = adherencePercent;
    }
    public Map<Integer, List<EventStreamDay>> getByDayEntries() {
        return byDayEntries;
    }
    /**
     * Reports contain multiple rows with composite search information (e.g. study burst
     * foo, iteration 2, possibly even a specific week of that study burst). To make
     * the search API for these reports simpler, we're combining this information into
     * string descriptors that can be specified via API search, e.g. "study burst 2" + 
     * "Week 2". These are not displayed as a group for the whole report, they are 
     * shown in the row descriptors. But they are persisted as a collection on the report
     * for the SQL to retrieve records. 
     */
    @JsonIgnore
    public Set<String> getSearchableLabels() {
        return searchableLabels;
    }
    public List<WeeklyAdherenceReportRow> getRows() {
        return rows;
    }
    public void visitDays(BiConsumer<EventStreamDay, Integer> consumer) {
        for (List<EventStreamDay> days : byDayEntries.values()) {
            for (int i=0; i < days.size(); i++) {
                consumer.accept(days.get(i), i);
            }
        }
    }
    public StudyReportWeek copy() {
        StudyReportWeek week = new StudyReportWeek();
        week.setWeekInStudy(weekInStudy);
        week.setStartDate(startDate);
        week.setAdherencePercent(adherencePercent);
        week.getSearchableLabels().addAll(searchableLabels);
        week.getRows().addAll(rows);
        for (int i=0; i < 7; i++) {
            List<EventStreamDay> days = byDayEntries.get(i).stream()
                    .map(day -> day.copy()).collect(toList());
            week.getByDayEntries().put(i, days);
        }
        return week;
    }
}
