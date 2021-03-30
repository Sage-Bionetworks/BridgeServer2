package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_JOINER;

import java.util.List;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class Scheduler {
    
    public final Timeline calculateTimeline(Schedule2 schedule) {
        Timeline.Builder timeline = new Timeline.Builder();
        
        timeline.withDuration(schedule.getDuration());
        timeline.withSchedule(schedule);
        calculateLanguageKey(timeline);
        
        for (Session session : schedule.getSessions()) {
            for (TimeWindow window : session.getTimeWindows()) {
                scheduleTimeWindowSequence(timeline, schedule, session, window);
            }
        }
        return timeline.build();
    }

    void calculateLanguageKey(Timeline.Builder timeline) {
        timeline.withLang("en");
        List<String> callerLangs = RequestContext.get().getCallerLanguages();
        if (!callerLangs.isEmpty()) {
            callerLangs = callerLangs.stream().map(s -> s.toLowerCase()).collect(toList());
            timeline.withLang(COMMA_JOINER.join(callerLangs));
        }
    }
    
    int calculateEndDay(int studyLengthInDays, LocalTime startTime, int startDay, Period expiration) {
        if (expiration == null) {
            return studyLengthInDays;
        }
        int expInMinutes = expiration.toStandardMinutes().getMinutes();
        int minutesInDay = (startTime.getHourOfDay() * 60) + startTime.getMinuteOfHour();
        
        int endDay = startDay + ((minutesInDay + expInMinutes)/60/24);
        // If the time of day is 00:00, then don't return the end day because no part of it can be used
        // by the participant, and there's no reason for the client to pull the scheduled session for 
        // this day.
        if ((minutesInDay + expInMinutes) % 1440 == 0) {
            endDay -= 1;
        }
        return endDay;
    }

    void scheduleTimeWindowSequence(Timeline.Builder timeline, Schedule2 schedule, Session session, TimeWindow window) {
        int studyLengthInDays = schedule.getDuration().toStandardDays().getDays(); // can be in days or weeks
        
        int delayInDays = (session.getDelay() == null) ? 0 : session.getDelay().toStandardDays().getDays();
        int intervalInDays = (session.getInterval() == null) ? 0 : session.getInterval().toStandardDays().getDays();
        int occurrenceMax = (session.getOccurrences() == null) ? Integer.MAX_VALUE : session.getOccurrences();
        
        int startDay = delayInDays;
        int endDay = delayInDays;
        int occurrenceCount = 0;
        boolean addedSession = false; // don't add session until we've proven it will be in timeline

        Multiset<String> ids = HashMultiset.create();
        do {
            endDay = startDay;
            
            LocalTime startTime = window.getStartTime();
            Period delay = session.getDelay();
            Period expiration = window.getExpiration();
            
            endDay = calculateEndDay(studyLengthInDays, startTime, startDay, expiration);
            // If it extends beyond the end of the study, it is not included in timeline
            if (endDay > studyLengthInDays) {
                break;
            }
            if (!addedSession) {
                timeline.withSessionInfo(SessionInfo.create(session));
                addedSession = true;
            }
            
            ScheduledSession.Builder scheduledSession = new ScheduledSession.Builder();
            scheduledSession.withRefGuid(session.getGuid());
            scheduledSession.withStartDay(startDay);
            scheduledSession.withEndDay(endDay);
            scheduledSession.withStartTime(window.getStartTime());
            scheduledSession.withExpiration(window.getExpiration());
            scheduledSession.withPersistent(window.isPersistent());
            // If we have a delay under one day, and it's the first day, then skip the startTime 
            // and delay for the indicated time before showing.
            if (startDay == 0 && delay != null && delay.toStandardDays().getDays() == 0) {
                scheduledSession.withDelayTime(delay);
                scheduledSession.withStartTime(null);
            }
            
            String sessionInstanceGuid = generateSessionInstanceGuid(
                    schedule.getGuid(), session.getGuid(), window.getGuid(), occurrenceCount);
            scheduledSession.withInstanceGuid(sessionInstanceGuid);

            // The position of an assessment in a session is used to differentiate repeated assessments
            // in a single session. Each configuration gets a separate entry in the timeline with a 
            // separate key that can be referenced for lookup.
            ids.clear();
            for (AssessmentReference ref : session.getAssessments()) {
                ids.add(ref.getGuid());
                
                String asmtInstanceGuid = generateAssessmentInstanceGuid(schedule.getGuid(), session.getGuid(),
                        window.getGuid(), occurrenceCount, ref.getGuid(), ids.count(ref.getGuid()));
             
                AssessmentInfo asmtInfo = AssessmentInfo.create(ref);
                timeline.withAssessmentInfo(asmtInfo);
                
                ScheduledAssessment schAsmt = new ScheduledAssessment(asmtInfo.getKey(), asmtInstanceGuid);
                scheduledSession.withScheduledAssessment(schAsmt);
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
                + windowOccurrence + sessionGuid.substring(0, 4) + scheduleGuid.substring(0, 6);
    }
}
