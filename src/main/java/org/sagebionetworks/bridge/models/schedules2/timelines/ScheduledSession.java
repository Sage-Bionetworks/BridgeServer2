package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.lang.Boolean.TRUE;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.joda.time.LocalTime;
import org.joda.time.Period;

public class ScheduledSession {

    private String refGuid;
    private String instanceGuid;
    private int startDay;
    private int endDay;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private Period delayTime;
    private Period expiration;
    private Boolean persistent;
    private List<ScheduledAssessment> assessments = new ArrayList<>();
    
    private ScheduledSession(String refGuid, String instanceGuid, int startDay, int endDay, LocalTime startTime,
            Period delayTime, Period expiration, Boolean persistent, List<ScheduledAssessment> assessments) {
        this.refGuid = refGuid;
        this.instanceGuid = instanceGuid;
        this.startDay = startDay;
        this.endDay = endDay;
        this.delayTime = delayTime;
        this.startTime = startTime;
        this.expiration = expiration;
        if (TRUE.equals(persistent)) {
            this.persistent = TRUE;    
        }
        this.assessments = assessments;
    }
    
    public String getRefGuid() {
        return refGuid;
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
    
    public static class Builder {
        private String refGuid;
        private String instanceGuid;
        private int startDay;
        private int endDay;
        private Period delayTime;
        private LocalTime startTime;
        private Period expiration;
        private Boolean persistent;
        private List<ScheduledAssessment> assessments = new ArrayList<>();

        public Builder withRefGuid(String refGuid) {
            this.refGuid = refGuid;
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
        public ScheduledSession build() {
            return new ScheduledSession(refGuid, instanceGuid, startDay, endDay, startTime, delayTime, expiration,
                    persistent, assessments);
        }
    }
    
}
