package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "activeOnly", "timestamp", "adherencePercentage", "dayRangeOfAllStreams", "streams", "type" })
public class EventStreamAdherenceReport {
    private boolean activeOnly;
    private DateTime timestamp;
    private int adherencePercent;
    private List<EventStream> streams = new ArrayList<>();
    
    public int[] getDayRangeOfAllStreams() {
        // This can be done with streams, but I'm not convinced it's as fast. 
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (EventStream stream : streams) {
            for (Integer i : stream.getByDayEntries().keySet()) {
                if (i < min) {
                    min = i;
                }
                if (i > max) {
                    max = i;
                }
            }
        }
        return new int[] { min, max };
    }
    public boolean isActiveOnly() {
        return activeOnly;
    }
    public void setActiveOnly(boolean activeOnly) {
        this.activeOnly = activeOnly;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
    public int getAdherencePercent() {
        return adherencePercent;
    }
    public void setAdherencePercent(int adherencePercent) {
        this.adherencePercent = adherencePercent;
    }
    public List<EventStream> getStreams() {
        return streams;
    }
    public void setStreams(List<EventStream> streams) {
        this.streams = streams;
    }
}
