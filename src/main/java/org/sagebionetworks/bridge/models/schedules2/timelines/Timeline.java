package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The view of a schedule partially resolved for client apps, which the GUIDs that
 * are needed to track the performance of specific sessions and assessments, and 
 * the related information to display tasks in the UI.
 */
public class Timeline {

    private final String lang;
    private final List<ScheduledSession> scheduledSessions;
    private final Collection<AssessmentInfo> assessments;
    private final Collection<SessionInfo> sessions;
    
    private Timeline(String lang, List<ScheduledSession> scheduledSessions, 
            Collection<AssessmentInfo> assessments, Collection<SessionInfo> sessions) {
        this.lang = lang;
        this.scheduledSessions = scheduledSessions;
        this.assessments = assessments;
        this.sessions = sessions;
    }
    
    public String getLang() {
        return lang;
    }
    public List<ScheduledSession> getScheduledSessions() {
        return scheduledSessions;
    }
    public Collection<AssessmentInfo> getAssessments() {
        return assessments;
    }
    public Collection<SessionInfo> getSessions() {
        return sessions;
    }
    
    public static class Builder {
        private String lang;
        private List<ScheduledSession> scheduledSessions = new ArrayList<>();
        private Map<Integer, AssessmentInfo> assessments = new HashMap<>();
        private Map<String, SessionInfo> sessions = new HashMap<>();
        
        public Builder withLang(String lang) {
            this.lang = lang;
            return this;
        }
        public Builder withScheduledSession(ScheduledSession session) {
            this.scheduledSessions.add(session);
            return this;
        }
        public Builder withAssessmentInfo(AssessmentInfo ref) {
            this.assessments.put(ref.hashCode(), ref);
            return this;
        }
        public Builder withSessionInfo(SessionInfo info) {
            this.sessions.put(info.getGuid(), info);
            return this;
        }
        public Timeline build() {
            return new Timeline(lang, scheduledSessions, assessments.values(), sessions.values());
        }
    }
}
