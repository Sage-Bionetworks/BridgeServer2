package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

public class EventStreamAdherenceReport {
    private boolean activeOnly;
    private DateTime timestamp;
    private int adherencePercent;
    private List<EventStream> streams = new ArrayList<>();

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
    @Override
    public String toString() {
        return "EventStreamAdherenceReport [activeOnly=" + activeOnly + ", timestamp=" + timestamp
                + ", adherencePercent=" + adherencePercent + ", streams=" + streams + "]";
    }
}
