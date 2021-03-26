package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.joda.time.LocalTime;
import org.joda.time.Period;

public class ScheduledSession {

    private String guid;
    private String instanceGuid;
    private int startDay;
    private int endDay;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private Period expiration;
    private boolean persistent;
    private List<ScheduledAssessment> assessments = new ArrayList<>();
    
    private ScheduledSession(String guid, String instanceGuid, int startDay, int endDay,
            LocalTime startTime, Period expiration, boolean persistent, 
            List<ScheduledAssessment> assessments) {
        this.guid = guid;
        this.instanceGuid = instanceGuid;
        this.startDay = startDay;
        this.endDay = endDay;
        this.startTime = startTime;
        this.expiration = expiration;
        this.persistent = persistent;
        this.assessments = assessments;
    }
    
    public String getGuid() {
        return guid;
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
    public LocalTime getStartTime() {
        return startTime;
    }
    public Period getExpiration() {
        return expiration;
    }
    public boolean isPersistent() {
        return persistent;
    }
    public List<ScheduledAssessment> getAssessments() {
        return assessments;
    }
    
    public static class Builder {
        private String guid;
        private String instanceGuid;
        private int startDay;
        private int endDay;
        private LocalTime startTime;
        private Period expiration;
        private boolean persistent;
        private List<ScheduledAssessment> assessments = new ArrayList<>();

        public Builder withGuid(String guid) {
            this.guid = guid;
            return this;
        }
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
        public Builder withStartTime(LocalTime startTime) {
            this.startTime = startTime;
            return this;
        }
        public Builder withExpiration(Period expiration) {
            this.expiration = expiration;
            return this;
        }
        public Builder withPersistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }
        public Builder withScheduledAssessment(ScheduledAssessment asmt) {
            this.assessments.add(asmt);
            return this;
        }
        public ScheduledSession build() {
            return new ScheduledSession(guid, instanceGuid, startDay, endDay, startTime, 
                    expiration, persistent, assessments);
        }
    }
    
}
