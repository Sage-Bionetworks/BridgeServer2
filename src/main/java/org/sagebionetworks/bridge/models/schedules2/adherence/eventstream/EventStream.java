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
    // Within that report, each unique day entry is defined by the combination of session, event ID, and start day.
    // We create a key for this map of the required values.
    @JsonIgnore
    private Map<String, EventStreamDay> streamsByStreamKey;
    
    public EventStream() {
        byDayEntries = new TreeMap<>();
        streamsByStreamKey = new HashMap<>();
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
    public EventStreamDay retrieveDay(TimelineMetadata meta) {
        checkNotNull(meta.getSessionGuid());
        
        String eventId = checkNotNull(meta.getSessionStartEventId());
        int startDay = checkNotNull(meta.getSessionInstanceStartDay());
        
        String streamKey = String.format("%s:%s:%s", meta.getSessionGuid(), eventId, startDay);
        EventStreamDay eventStream = streamsByStreamKey.get(streamKey);
        if (eventStream == null) {
            eventStream = new EventStreamDay();
            eventStream.setSessionGuid(meta.getSessionGuid());
            eventStream.setSessionName(meta.getSessionName());
            eventStream.setSessionSymbol(meta.getSessionSymbol());
            // Don't set these fields here because in the event stream report, this information 
            // is reported once for each stream, and doesn't need to be repeated here.
            // TODO: So why are these here again?
            // eventStream.setStudyBurstId(meta.getStudyBurstId());
            // eventStream.setStudyBurstNum(meta.getStudyBurstNum());
            streamsByStreamKey.put(streamKey, eventStream);
            addEntry(startDay, eventStream);
        }
        return eventStream;
    }
    @Override
    public String toString() {
        return "EventStream [startEventId=" + startEventId + ", eventTimestamp=" + eventTimestamp + ", daysSinceEvent="
                + daysSinceEvent + ", studyBurstId=" + studyBurstId + ", studyBurstNum=" + studyBurstNum
                + ", byDayEntries=" + byDayEntries + ", streamsByStreamKey=" + streamsByStreamKey + "]";
    }
}
