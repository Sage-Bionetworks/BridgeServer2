package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonPropertyOrder({ "startEventId", "eventTimestamp", "daysSinceEvent", "sessionGuids", 
    "studyBurstId", "studyBurstNum", "byDayEntries", "type" })
public class EventStream {
    private String startEventId;
    private DateTime eventTimestamp;
    private Integer daysSinceEvent;
    private String studyBurstId;
    private Integer studyBurstNum;
    // Each key in the map is a day after the event ID occurred when one or more session
    // has one or more scheduled time windows (these are all listed together in the list
    // of EventStreamDay records).
    private Map<Integer, List<EventStreamDay>> byDayEntries;
    
    public EventStream() {
        byDayEntries = new TreeMap<>(); // maintain the order of the days
    }
    public Set<String> getSessionGuids() {
        return byDayEntries.values().stream()
                .flatMap(list -> list.stream())
                .map(EventStreamDay::getSessionGuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public String getStartEventId() {
        return startEventId;
    }
    public void setStartEventId(String startEventId) {
        this.startEventId = startEventId;
    }
    @JsonSerialize(using = DateTimeSerializer.class) // preserve time zone offset
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }
    public void setEventTimestamp(DateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
    public Map<Integer, List<EventStreamDay>> getByDayEntries() {
        return byDayEntries;
    }
    public void addEntry(int startDay, EventStreamDay entry) {
        List<EventStreamDay> entries = this.byDayEntries.get(startDay);
        if (entries == null) {
            entries = new ArrayList<>();
            this.byDayEntries.put(startDay, entries);
        }
        entries.add(entry);
    }
    public Integer getDaysSinceEvent() {
        return (daysSinceEvent == null || daysSinceEvent < 0) ? null : daysSinceEvent;
    }
    public void setDaysSinceEvent(Integer daysSinceEvent) {
        this.daysSinceEvent = daysSinceEvent;
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public void setStudyBurstId(String studyBurstId) {
        this.studyBurstId = studyBurstId;
    }
    public Integer getStudyBurstNum() {
        return studyBurstNum;
    }
    public void setStudyBurstNum(Integer studyBurstNum) {
        this.studyBurstNum = studyBurstNum;
    }
}
