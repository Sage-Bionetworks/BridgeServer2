package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EventStream {
    private String startEventId;
    private DateTime eventTimestamp;
    private Integer daysSinceEvent;
    private String studyBurstId;
    private Integer studyBurstNum;
    private Map<Integer, List<EventStreamDay>> byDayEntries;
    
    public EventStream() {
        byDayEntries = new TreeMap<>();
    }
    public String getStartEventId() {
        return startEventId;
    }
    public void setStartEventId(String startEventId) {
        this.startEventId = startEventId;
    }
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
    @Override
    public String toString() {
        return "EventStream [startEventId=" + startEventId + ", daysSinceEvent=" + daysSinceEvent + ", studyBurstId="
                + studyBurstId + ", studyBurstNum=" + studyBurstNum + ", byDayEntries=" + byDayEntries + "]";
    }
}
