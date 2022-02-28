package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.DayRange;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonPropertyOrder({ "timestamp", "clientTimeZone", "adherencePercent", "dayRangeOfAllStreams",
        "dateRangeOfAllStreams", "progression", "streams", "type" })
public class EventStreamAdherenceReport {
    
    private DateTime timestamp;
    private String clientTimeZone;
    private int adherencePercent = 100;
    private ParticipantStudyProgress progression;
    private List<EventStream> streams = new ArrayList<>();
    private DayRange dayRangeOfAllStreams;
    private DateRange dateRangeOfAllStreams;
    
    public DayRange getDayRangeOfAllStreams() {
        return dayRangeOfAllStreams;
    }
    public void setDayRangeOfAllStreams(DayRange dayRangeOfAllStreams) {
        this.dayRangeOfAllStreams = dayRangeOfAllStreams;
    }
    public DateRange getDateRangeOfAllStreams() {
        return dateRangeOfAllStreams;
    }
    public void setDateRangeOfAllStreams(DateRange dateRangeOfAllStreams) {
        this.dateRangeOfAllStreams = dateRangeOfAllStreams;
    }
    @JsonSerialize(using = DateTimeSerializer.class) // preserve time zone offset
    public DateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    public int getAdherencePercent() {
        return adherencePercent;
    }
    public void setAdherencePercent(int adherencePercent) {
        this.adherencePercent = adherencePercent;
    }
    public ParticipantStudyProgress getProgression() {
        return progression;
    }
    public void setProgression(ParticipantStudyProgress progression) {
        this.progression = progression;
    }
    public List<EventStream> getStreams() {
        return streams;
    }
    public void setStreams(List<EventStream> streams) {
        this.streams = streams;
    }
}
