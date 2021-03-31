package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;

import org.joda.time.Period;

import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;

/**
 * The view of a schedule partially resolved for client apps, which the GUIDs that
 * are needed to track the performance of specific sessions and assessments, and 
 * the related information to display tasks in the UI.
 */
public class Timeline {

    private final String lang;
    private final Period duration;
    private final List<ScheduledSession> scheduledSessions;
    private final List<AssessmentInfo> assessments;
    private final List<SessionInfo> sessions;
    private final List<TimelineMetadata> metadata;
    
    private Timeline(Period duration, String lang, List<ScheduledSession> scheduledSessions,
            List<AssessmentInfo> assessments, List<SessionInfo> sessions, List<TimelineMetadata> metadata) {
        this.duration = duration;
        this.lang = lang;
        this.scheduledSessions = scheduledSessions;
        this.assessments = assessments;
        this.sessions = sessions;
        this.metadata = metadata;
    }
    
    @JsonIgnore
    public String getLang() {
        return lang;
    }
    public Period getDuration() {
        return duration;
    }
    public List<ScheduledSession> getSchedule() {
        return scheduledSessions;
    }
    public List<AssessmentInfo> getAssessments() {
        return assessments;
    }
    public List<SessionInfo> getSessions() {
        return sessions;
    }
    @JsonIgnore
    public List<TimelineMetadata> getMetadata() {
        return metadata;
    }
    
    public static class Builder {
        private Schedule2 schedule;
        private Period duration;
        private String lang;
        private List<ScheduledSession> scheduledSessions = new ArrayList<>();
        private Map<String, AssessmentInfo> assessments = new HashMap<>();
        private Map<String, SessionInfo> sessions = new HashMap<>();
        private List<TimelineMetadata> metadata = new ArrayList<>();
        
        public Builder withSchedule(Schedule2 schedule) {
            this.schedule = schedule;
            return this;
        }
        public Builder withDuration(Period duration) {
            this.duration = duration;
            return this;
        }
        public Builder withLang(String lang) {
            this.lang = lang;
            return this;
        }
        public Builder withScheduledSession(ScheduledSession session) {
            this.scheduledSessions.add(session);
            
            TimelineMetadata sessionMeta = new TimelineMetadata();
            sessionMeta.setGuid(session.getInstanceGuid());
            sessionMeta.setAppId(schedule.getAppId());
            sessionMeta.setScheduleGuid(schedule.getGuid());
            sessionMeta.setScheduleModifiedOn(schedule.getModifiedOn());
            sessionMeta.setSchedulePublished(schedule.isPublished());
            sessionMeta.setSessionInstanceGuid(session.getInstanceGuid());
            sessionMeta.setSessionGuid(session.getRefGuid());
            metadata.add(sessionMeta);
            
            for (ScheduledAssessment schAsmt : session.getAssessments()) { 
                TimelineMetadata schMeta = TimelineMetadata.copy(sessionMeta);
                // could avoid this map lookup by including the information but
                // excluding it from the json serialization.
                AssessmentInfo info = assessments.get(schAsmt.getRefKey());
                schMeta.setGuid(schAsmt.getInstanceGuid());
                schMeta.setAssessmentInstanceGuid(schAsmt.getInstanceGuid());
                schMeta.setAssessmentGuid(info.getGuid());
                schMeta.setAssessmentId(info.getIdentifier());
                metadata.add(schMeta);    
            }
            return this;
        }
        public Builder withAssessmentInfo(AssessmentInfo ref) {
            this.assessments.put(ref.getKey(), ref);
            return this;
        }
        public Builder withSession(Session session) {
            if (this.sessions.containsKey(session.getGuid())) {
                this.sessions.put(session.getGuid(), SessionInfo.create(session));    
            }
            return this;
        }
        public Timeline build() {
            Collections.sort(scheduledSessions, (sc1, sc2) -> {
                int res = sc1.getStartDay() - sc2.getStartDay();
                if (res == 0) {
                    return sc1.getEndDay() - sc2.getEndDay();
                }
                return res;
            });
            return new Timeline(duration, lang, scheduledSessions, ImmutableList.copyOf(assessments.values()),
                    ImmutableList.copyOf(sessions.values()), metadata);
        }
    }
}
