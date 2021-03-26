package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.List;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class Scheduler {
    
    public final Timeline calculateTimeline(Schedule2 schedule) {
        Timeline.Builder timeline = new Timeline.Builder();
        
        List<String> callerLangs = RequestContext.get().getCallerLanguages();
        String lang = BridgeUtils.COMMA_JOINER.join(callerLangs);
        timeline.withLang(lang);
        
        for (Session session : schedule.getSessions()) {
            for (AssessmentReference ref : session.getAssessments()) {
                AssessmentInfo info = AssessmentInfo.create(ref);
                timeline.withAssessmentInfo(info);
            }
            for (TimeWindow window : session.getTimeWindows()) {
                scheduleTimeWindowSequence(timeline, schedule, session, window);
            }
            SessionInfo info = SessionInfo.create(session);
            timeline.withSessionInfo(info);
        }
        return timeline.build();
    }

    void scheduleTimeWindowSequence(Timeline.Builder timeline, Schedule2 schedule, Session session, TimeWindow window) {
        int studyLengthInDays = schedule.getDuration().toStandardDays().getDays();
        int delayInDays = (session.getDelay() == null) ? 0 : session.getDelay().toStandardDays().getDays();
        int intervalInDays = (session.getInterval() == null) ? 0 : session.getInterval().toStandardDays().getDays();
        int occurrenceMax = (session.getOccurrences() == null) ? Integer.MAX_VALUE : session.getOccurrences();
        
        int startDay = delayInDays;
        int endDay = delayInDays;
        int occurrenceCount = 0;
        
        Multiset<String> ids = HashMultiset.create();
        do {
            endDay = startDay;
            
            ScheduledSession.Builder scheduledSession = new ScheduledSession.Builder();
            
            // The expiration time of a window can be longer than a day, and will change the endDay
            // of the scheduled session.
            LocalTime startTime = window.getStartTime();
            Period expiration = window.getExpiration();
            if (expiration == null) {
                endDay = studyLengthInDays;
            } else {
                int totalHours = startTime.getHourOfDay() + expiration.toStandardHours().getHours();
                if (totalHours > 24) {
                    endDay = startDay + (totalHours/24);
                }
            }
            scheduledSession.withGuid(session.getGuid());
            scheduledSession.withStartDay(startDay);
            scheduledSession.withEndDay(endDay);
            scheduledSession.withStartTime(window.getStartTime());
            scheduledSession.withExpiration(window.getExpiration());
            scheduledSession.withPersistent(window.isPersistent());
            
            String sig = generateSessionInstanceGuid(
                    schedule.getGuid(), session.getGuid(), window.getGuid(), occurrenceCount);
            scheduledSession.withInstanceGuid(sig);

            ids.clear();
            for (AssessmentReference ref : session.getAssessments()) {
                ids.add(ref.getGuid());
                
                String asmtInstanceGuid = generateAssessmentInstanceGuid(schedule.getGuid(), session.getGuid(),
                        window.getGuid(), occurrenceCount, ref.getGuid(), ids.count(ref.getGuid()));
                
                ScheduledAssessment copy = new ScheduledAssessment(ref.getGuid(), asmtInstanceGuid);
                scheduledSession.withScheduledAssessment(copy);
            }
            timeline.withScheduledSession(scheduledSession.build());
            
            startDay += intervalInDays;
            occurrenceCount++;
        } while(intervalInDays > 0 && occurrenceCount < occurrenceMax && startDay < studyLengthInDays);
    }
    
    /**
     * Create a unique GUID for a session instance that is the same each time you calculate a Timeline
     * from a Schedule. It should look like a GUID and not a compound identifier, as client developers 
     * have (in the past) parsed compound identifiers, and we want to discourage this.
     */
    String generateSessionInstanceGuid(String scheduleGuid, String sessionGuid, String windowGuid,
            int windowOccurrence) {
        return windowGuid.substring(0, 8) + windowOccurrence + sessionGuid.substring(0, 8)
                + scheduleGuid.substring(0, 6);
    }

    /**
     * Create a unique GUID for an assessment instance that is the same each time you calculate a Timeline
     * from a Schedule. It should look like a GUID and not a compound identifier, as client developers 
     * have (in the past) parsed compound identifiers, and we want to discourage this.
     */
    String generateAssessmentInstanceGuid(String scheduleGuid, String sessionGuid, String windowGuid,
            int windowOccurrence, String assessmentGuid, int assessmentOcccurrence) {
        return assessmentGuid.substring(0, 6) + assessmentOcccurrence + windowGuid.substring(0, 6)
                + windowOccurrence + sessionGuid.substring(0, 4) + scheduleGuid.substring(0, 4);
    }
}
