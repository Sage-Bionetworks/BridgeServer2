package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.BridgeUtils.periodInMinutes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.sagebionetworks.bridge.models.schedules2.Notification;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;

/**
 * The view of a schedule partially resolved for client apps, which the GUIDs that
 * are needed to track the performance of specific sessions and assessments, and 
 * the related information to display tasks in the UI.
 */
@JsonPropertyOrder({ "duration", "totalMinutes", "totalNotifications", "schedule", "sessions", "assessments",
        "studyBursts", "type" })
public class Timeline {

    private final String lang;
    private final Period duration;
    private final List<ScheduledSession> scheduledSessions;
    private final List<AssessmentInfo> assessments;
    private final List<SessionInfo> sessions;
    private final List<StudyBurstInfo> studyBursts;
    private final List<TimelineMetadata> metadata;
    private final int totalMinutes;
    private final int totalNotifications;
    
    private Timeline(Period duration, String lang, List<ScheduledSession> scheduledSessions,
            List<AssessmentInfo> assessments, List<SessionInfo> sessions, List<TimelineMetadata> metadata,
            List<StudyBurstInfo> studyBursts, int totalMinutes, int totalNotifications) {
        this.duration = duration;
        this.lang = lang;
        this.scheduledSessions = scheduledSessions;
        this.assessments = assessments;
        this.sessions = sessions;
        this.metadata = metadata;
        this.studyBursts = studyBursts;
        this.totalMinutes = totalMinutes;
        this.totalNotifications = totalNotifications;
    }
    
    @JsonIgnore
    public String getLang() {
        return lang;
    }
    public int getTotalMinutes() {
        return totalMinutes;
    }
    public int getTotalNotifications() {
        return totalNotifications;
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
    public List<StudyBurstInfo> getStudyBursts() {
        return studyBursts;
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
        // maintain the order the sessions are inserted, which is the order they exist in 
        // the session.
        private Map<String, SessionInfo> sessions = new LinkedHashMap<>();
        private Map<String, StudyBurstInfo> studyBursts = new LinkedHashMap<>();
        private List<TimelineMetadata> metadata = new ArrayList<>();
        private int totalMinutes;
        private int totalNotifications;
        
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
        public Builder withScheduledSession(ScheduledSession schSession) {
            this.scheduledSessions.add(schSession);
            
            TimelineMetadata sessionMeta = new TimelineMetadata();
            sessionMeta.setGuid(schSession.getInstanceGuid());
            sessionMeta.setAppId(schedule.getAppId());
            sessionMeta.setScheduleGuid(schedule.getGuid());
            sessionMeta.setScheduleModifiedOn(schedule.getModifiedOn());
            sessionMeta.setSchedulePublished(schedule.isPublished());
            sessionMeta.setSessionInstanceGuid(schSession.getInstanceGuid());
            sessionMeta.setSessionGuid(schSession.getSession().getGuid());
            sessionMeta.setSessionStartEventId(schSession.getStartEventId());
            sessionMeta.setSessionInstanceStartDay(schSession.getStartDay());
            sessionMeta.setSessionInstanceEndDay(schSession.getEndDay());
            sessionMeta.setSessionSymbol(schSession.getSession().getSymbol());
            sessionMeta.setSessionName(schSession.getSession().getName());
            sessionMeta.setTimeWindowGuid(schSession.getTimeWindow().getGuid());
            sessionMeta.setTimeWindowPersistent(schSession.getTimeWindow().isPersistent());
            sessionMeta.setStudyBurstId(schSession.getStudyBurstId());
            sessionMeta.setStudyBurstNum(schSession.getStudyBurstNum());
            metadata.add(sessionMeta);
            
            for (ScheduledAssessment schAsmt : schSession.getAssessments()) {
                TimelineMetadata asmtMeta = TimelineMetadata.copy(sessionMeta);
                asmtMeta.setGuid(schAsmt.getInstanceGuid());
                asmtMeta.setAssessmentInstanceGuid(schAsmt.getInstanceGuid());
                asmtMeta.setAssessmentGuid(schAsmt.getReference().getGuid());
                asmtMeta.setAssessmentId(schAsmt.getReference().getIdentifier());
                asmtMeta.setAssessmentRevision(schAsmt.getReference().getRevision());
                metadata.add(asmtMeta);    
            }
            
            for (Notification notification : schSession.getSession().getNotifications()) {
                this.totalNotifications += 1;
                // Repeating notifications will produce more notifications. TimeWindow is never
                // null but try telling that to FindBugs.
                if (notification.getInterval() != null && schSession.getTimeWindow() != null) {
                    long windowMinutes = periodInMinutes(schSession.getTimeWindow().getExpiration());
                    long intervalMinutes = periodInMinutes(notification.getInterval());
                    this.totalNotifications += Math.floorDiv(windowMinutes, intervalMinutes);
                }
            }
            // Get the sum of all minutes for all sessions. Each time a scheduled session 
            // is added, we're adding to this running total.
            Integer min = this.sessions.get(schSession.getRefGuid()).getMinutesToComplete();
            if (min != null) {
                this.totalMinutes += min.intValue();
            }
            
            return this;
        }
        public Builder withAssessmentInfo(AssessmentInfo asmtInfo) {
            this.assessments.put(asmtInfo.getKey(), asmtInfo);
            return this;
        }
        public Builder withSessionInfo(SessionInfo sessionInfo) {
            this.sessions.put(sessionInfo.getGuid(), sessionInfo);    
            return this;
        }
        public Builder withStudyBurstInfo(StudyBurstInfo studyBurstInfo) {
            this.studyBursts.put(studyBurstInfo.getIdentifier(), studyBurstInfo);
            return this;
        }
        public Timeline build() {
            Collections.sort(scheduledSessions, (sc1, sc2) -> {
                int res = sc1.getStartDay() - sc2.getStartDay();
                if (res == 0) {
                    res = sc1.getEndDay() - sc2.getEndDay();
                }
                if (res == 0 && sc1.getStudyBurstId() != null && sc1.getStudyBurstId().equals(sc2.getStudyBurstId())) {
                    res = sc1.getStudyBurstNum() - sc2.getStudyBurstNum();
                }
                return res;
            });
            return new Timeline(duration, lang, scheduledSessions, ImmutableList.copyOf(assessments.values()),
                    ImmutableList.copyOf(sessions.values()), metadata, ImmutableList.copyOf(studyBursts.values()),
                    totalMinutes, totalNotifications);
        }
    }
}
