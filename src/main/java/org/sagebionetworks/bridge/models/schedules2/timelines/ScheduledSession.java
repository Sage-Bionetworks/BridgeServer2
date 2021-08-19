package org.sagebionetworks.bridge.models.schedules2.timelines;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class ScheduledSession {

    private String instanceGuid;
    private String startEventId;
    private int startDay;
    private int endDay;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private Period delayTime;
    private Period expiration;
    private Boolean persistent;
    private List<ScheduledAssessment> assessments = new ArrayList<>();
    // This is carried over in order to make it faster and easer to construct
    // the TimelineMetadata object during construction of the Timeline. It is
    // not part of the JSON serialization of the ScheduledSession.
    private final Session session;
    private final TimeWindow window;
    
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
        if (TRUE.equals(builder.persistent)) {
            this.persistent = TRUE;    
        }
        this.assessments = builder.assessments;
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
    public int getStartDay() {
        return startDay;
    }
    public int getEndDay() {
        return endDay;
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
    
    public static class Builder {
        private String instanceGuid;
        private String startEventId;
        private int startDay;
        private int endDay;
        private Period delayTime;
        private LocalTime startTime;
        private Period expiration;
        private Boolean persistent;
        private List<ScheduledAssessment> assessments = new ArrayList<>();
        private Session session;
        private TimeWindow window;
        
        public Builder copyWithoutAssessments() { 
            ScheduledSession.Builder builder = new ScheduledSession.Builder();
            builder.instanceGuid = instanceGuid;
            builder.startEventId = startEventId;
            builder.startDay = startDay;
            builder.endDay= endDay;
            builder.delayTime = delayTime;
            builder.startTime = startTime;
            builder.expiration = expiration;
            builder.persistent = persistent;
            builder.assessments = new ArrayList<>();
            builder.session = session;
            builder.window = window;
            return builder;
        }
        public Builder withInstanceGuid(String instanceGuid) {
            this.instanceGuid = instanceGuid;
            return this;
        }
        public Builder withStartEventId(String startEventId) {
            this.startEventId = startEventId;
            return this;
        }
        public Builder withStartDay(int startDay) {
            this.startDay = startDay;
            return this;
        }
        public Builder withEndDay(int endDay) {
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
        public ScheduledSession build() {
            checkNotNull(session);
            checkNotNull(window);
            
            return new ScheduledSession(this);
        }
    }
}