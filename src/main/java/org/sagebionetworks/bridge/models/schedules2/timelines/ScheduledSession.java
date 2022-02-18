package org.sagebionetworks.bridge.models.schedules2.timelines;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

@JsonPropertyOrder({ "instanceGuid", "refGuid", "timeWindowGuid", "startEventId", "startDay", "endDay", "startDate",
        "endDate", "state", "startTime", "delayTime", "expiration", "persistent", "studyBurstId", "studyBurstNum",
        "assessments", "type" })
public class ScheduledSession {

    private String instanceGuid;
    private String startEventId;
    private Integer startDay;
    private Integer endDay;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private Period delayTime;
    private Period expiration;
    private Boolean persistent;
    private final String studyBurstId;
    private final Integer studyBurstNum;
    private List<ScheduledAssessment> assessments = new ArrayList<>();
    // This is carried over in order to make it faster and easer to construct
    // the TimelineMetadata object during construction of the Timeline. It is
    // not part of the JSON serialization of the ScheduledSession.
    private final Session session;
    private final TimeWindow window;
    // For the ParticipantSchedule. These values cannot be known for the Timeline.
    private LocalDate startDate;
    private LocalDate endDate;
    private SessionCompletionState state;
    
    private ScheduledSession(ScheduledSession.Builder builder) {
        this.instanceGuid = builder.instanceGuid;
        this.startEventId = builder.startEventId;
        this.startDay = builder.startDay;
        this.endDay = builder.endDay;
        this.delayTime = builder.delayTime;
        this.startTime = builder.startTime;
        this.expiration = builder.expiration;
        this.session = builder.session;
        this.window = builder.window;
        this.studyBurstId = builder.studyBurstId;
        this.studyBurstNum = builder.studyBurstNum;
        if (TRUE.equals(builder.persistent)) {
            this.persistent = TRUE;    
        }
        this.assessments = builder.assessments;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.state = builder.state;
    }
    
    public String getRefGuid() {
        return session.getGuid();
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    public String getStartEventId() {
        return startEventId;
    }
    public Integer getStartDay() {
        return startDay;
    }
    public Integer getEndDay() {
        return endDay;
    }
    public LocalDate getStartDate() {
        return startDate;
    }
    public LocalDate getEndDate() {
        return endDate;
    }
    public SessionCompletionState getState() {
        return state;
    }
    public Period getDelayTime() {
        return delayTime;
    }
    public LocalTime getStartTime() {
        return startTime;
    }
    public Period getExpiration() {
        return expiration;
    }
    public Boolean isPersistent() {
        return persistent;
    }
    public List<ScheduledAssessment> getAssessments() {
        return assessments;
    }
    @JsonIgnore
    public Session getSession() {
        return session;
    }
    @JsonIgnore
    public TimeWindow getTimeWindow() {
        return window;
    }
    public String getTimeWindowGuid() {
        return window.getGuid();
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public Integer getStudyBurstNum() {
        return studyBurstNum;
    }
    /**
     * Important: the builder does not maintain the assessment references of the scheduled session.
     * Everywhere we use this function, we need to recalcuate the assessments.
     */
    @JsonIgnore
    public ScheduledSession.Builder toBuilder() {
        ScheduledSession.Builder builder = new ScheduledSession.Builder();
        builder.instanceGuid = instanceGuid;
        builder.startEventId = startEventId;
        builder.startDay = startDay;
        builder.endDay = endDay;
        builder.delayTime = delayTime;
        builder.startTime = startTime;
        builder.expiration = expiration;
        builder.persistent = persistent;
        builder.session = session;
        builder.window = window;
        builder.studyBurstId = studyBurstId;
        builder.studyBurstNum = studyBurstNum;
        builder.startDate = startDate;
        builder.endDate = endDate;
        builder.state = state;
        builder.assessments = new ArrayList<>();
        return builder;
    }

    public static class Builder {
        private String instanceGuid;
        private String startEventId;
        private Integer startDay;
        private Integer endDay;
        private Period delayTime;
        private LocalTime startTime;
        private Period expiration;
        private Boolean persistent;
        private List<ScheduledAssessment> assessments = new ArrayList<>();
        private Session session;
        private TimeWindow window;
        private String studyBurstId;
        private Integer studyBurstNum;
        // For the ParticipantSchedule. These values cannot be known for the generic Timeline
        private LocalDate startDate;
        private LocalDate endDate;
        private SessionCompletionState state;
        
        public Builder withInstanceGuid(String instanceGuid) {
            this.instanceGuid = instanceGuid;
            return this;
        }
        public Builder withStartEventId(String startEventId) {
            this.startEventId = startEventId;
            return this;
        }
        public Builder withStartDay(Integer startDay) {
            this.startDay = startDay;
            return this;
        }
        public Builder withEndDay(Integer endDay) {
            this.endDay = endDay;
            return this;
        }
        public Builder withDelayTime(Period delayTime) {
            this.delayTime = delayTime;
            return this;
        }
        public Builder withStartTime(LocalTime startTime) {
            this.startTime = startTime;
            return this;
        }
        public Builder withExpiration(Period expiration) {
            this.expiration = expiration;
            return this;
        }
        public Builder withPersistent(Boolean persistent) {
            this.persistent = persistent;
            return this;
        }
        public Builder withScheduledAssessment(ScheduledAssessment asmt) {
            this.assessments.add(asmt);
            return this;
        }
        public Builder withSession(Session session) {
            this.session = session;
            return this;
        }
        public Builder withTimeWindow(TimeWindow window) {
            this.window = window;
            return this;
        }
        
        public Builder withStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }
        public Builder withEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }
        public Builder withState(SessionCompletionState state) {
            this.state = state;
            return this;
        }
        public ScheduledSession build() {
            checkNotNull(session);
            checkNotNull(window);
            
            if (startEventId != null && startEventId.startsWith("study_burst:")) {
                String[] els = startEventId.split(":");
                this.studyBurstId = els[1];
                this.studyBurstNum = Integer.parseInt(els[2]);
            }
            return new ScheduledSession(this);
        }
    }
}