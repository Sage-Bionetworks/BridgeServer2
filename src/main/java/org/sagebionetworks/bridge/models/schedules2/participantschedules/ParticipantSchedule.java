package org.sagebionetworks.bridge.models.schedules2.participantschedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.schedules2.timelines.AssessmentInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.SessionInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.StudyBurstInfo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A schedule that is very similar to a timeline, but laid out with a real chronology for a specific
 * user, along with information about their adherence to this schedule.
 */
@JsonPropertyOrder({ "createdOn", "clientTimeZone", "dateRange", "schedule", "sessions", "assessments", "studyBursts",
        "eventTimestamps", "type" })
public class ParticipantSchedule {
    
    private DateRange dateRange;
    private DateTime createdOn;
    private String clientTimeZone;
    private List<ScheduledSession> scheduledSessions = ImmutableList.of();
    private List<AssessmentInfo> assessments = ImmutableList.of();
    private List<SessionInfo> sessions = ImmutableList.of();
    private List<StudyBurstInfo> studyBursts = ImmutableList.of();
    private Map<String, DateTime> eventTimestamps = ImmutableMap.of();

    public DateRange getDateRange() {
        return dateRange;
    }
    public void setDateRange(DateRange dateRange) {
        this.dateRange = dateRange;
    }
    public List<ScheduledSession> getSchedule() {
        return scheduledSessions;
    }
    public void setSchedule(List<ScheduledSession> schedule) {
        this.scheduledSessions = schedule;
    }
    public List<AssessmentInfo> getAssessments() {
        return assessments;
    }
    public void setAssessments(List<AssessmentInfo> assessments) {
        this.assessments = assessments;
    }
    public List<SessionInfo> getSessions() {
        return sessions;
    }
    public void setSessions(List<SessionInfo> sessions) {
        this.sessions = sessions;
    }
    public List<StudyBurstInfo> getStudyBursts() {
        return studyBursts;
    }
    public void setStudyBursts(List<StudyBurstInfo> studyBursts) {
        this.studyBursts = studyBursts;
    }
    @JsonSerialize(using = DateTimeSerializer.class) // preserve time zone offset
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    public Map<String, DateTime> getEventTimestamps() {
        return eventTimestamps;
    }
    public void setEventTimestamps(Map<String, DateTime> eventTimestamps) {
        this.eventTimestamps = eventTimestamps;
    }
}
