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
    
    private ScheduledSession(String instanceGuid, int startDay, int endDay, LocalTime startTime, Period delayTime,
            Period expiration, Boolean persistent, List<ScheduledAssessment> assessments, Session session,
            TimeWindow window) {
        this.instanceGuid = instanceGuid;
        this.startDay = startDay;
        this.endDay = endDay;
        this.delayTime = delayTime;
        this.startTime = startTime;
        this.expiration = expiration;
        this.session = session;
        this.window = window;
        if (TRUE.equals(persistent)) {
            this.persistent = TRUE;    
        }
        this.assessments = assessments;
    }
    
    public String getRefGuid() {
        return session.getGuid();
    }
    public String getInstanceGuid() {
        return instanceGuid;
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
        private int startDay;
        private int endDay;
        private Period delayTime;
        private LocalTime startTime;
        private Period expiration;
        private Boolean persistent;
        private List<ScheduledAssessment> assessments = new ArrayList<>();
        private Session session;
        private TimeWindow window;

        public Builder withInstanceGuid(String instanceGuid) {
            this.instanceGuid = instanceGuid;
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
            
            return new ScheduledSession(instanceGuid, startDay, endDay, startTime, 
                    delayTime, expiration, persistent, assessments, session, window);
        }
    }
}