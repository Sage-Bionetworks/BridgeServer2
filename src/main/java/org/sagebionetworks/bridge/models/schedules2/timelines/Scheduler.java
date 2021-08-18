package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.ENCODER;

import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class Scheduler {
    
    public static final Scheduler INSTANCE = new Scheduler();
    
    private static final HashFunction HASHER = Hashing.murmur3_128();
    
    private Scheduler() {}
    
    public final Timeline calculateTimeline(Schedule2 schedule) {
        Timeline.Builder builder = new Timeline.Builder();
        
        builder.withDuration(schedule.getDuration());
        builder.withSchedule(schedule);
        calculateLanguageKey(builder);
        
        for (Session session : schedule.getSessions()) {
            if (!session.getAssessments().isEmpty()) {
                for (TimeWindow window : session.getTimeWindows()) {
                    scheduleTimeWindowSequence(builder, schedule, session, window);
                }
            }
        }
        return builder.build();
    }

    void calculateLanguageKey(Timeline.Builder builder) {
        builder.withLang("en");
        List<String> callerLangs = RequestContext.get().getCallerLanguages();
        if (!callerLangs.isEmpty()) {
            callerLangs = callerLangs.stream().map(s -> s.toLowerCase()).collect(toList());
            builder.withLang(COMMA_JOINER.join(callerLangs));
        }
    }
    
    int calculateEndDay(int studyLengthInDays, LocalTime startTime, int startDay, Period expiration) {
        // endDay is zero indexed, so we subtract one from studyLengthInDays.
        if (expiration == null) {
            return studyLengthInDays - 1;
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

    void scheduleTimeWindowSequence(Timeline.Builder builder, Schedule2 schedule, Session session, TimeWindow window) {
        // Can be in days or weeks. Note that this means no individual session time stream can be longer than the
        // duration of the study, *not* that the study will last the duration on the calendar, since events that 
        // trigger a session series can start at any time. Those sessions will *also* run for the duration. It’s up 
        // to study designers to reconcile this for their study. 
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
            
            LocalTime startTime = window.getStartTime();
            Period delay = session.getDelay();
            Period expiration = window.getExpiration();
            
            endDay = calculateEndDay(studyLengthInDays, startTime, startDay, expiration);
            // If it extends beyond the end of the study, it is not included in timeline.
            // days are zero indexed so we subtract 1 from studyLengthInDays.
            if (endDay > (studyLengthInDays-1)) {
                break;
            }
            if (expiration == null) {
                // but this value is *not* zero indexed. 
                expiration = Period.parse("P" + (endDay - startDay + 1) + "D");
            }
            
            // This session will be used, so we can add it
            builder.withSessionInfo(SessionInfo.create(session));
            
            ScheduledSession.Builder scheduledSession = new ScheduledSession.Builder();
            scheduledSession.withSession(session);
            scheduledSession.withTimeWindow(window);
            scheduledSession.withStartDay(startDay);
            scheduledSession.withEndDay(endDay);
            scheduledSession.withStartTime(window.getStartTime());
            scheduledSession.withExpiration(expiration);
            scheduledSession.withPersistent(window.isPersistent());
            // If it is the first day, and there is a delay set that is less than a day,
            // it’s not entirely defined what should happen in this situation. We include
            // the delay value so the client can try and wait the delay period before 
            // presenting the scheduled session to the user. But this is not 
            // mandatory.
            if (startDay == 0 && delay != null && delay.toStandardDays().getDays() == 0) {
                scheduledSession.withDelayTime(delay);
            }
            // Add a scheduled session with a different GUID for each event, and one SessionInfo object for
            // all of them.
            
            for (String oneEventId : session.getStartEventIds()) {
                // Clear the state that is set in each iteration
                scheduledSession = scheduledSession.copy();

                // The position of an assessment in a session is used to differentiate repeated assessments
                // in a single session. Assessments can be configured differently, but if they are exactly
                // the same, we still generate a unique ID.
                ids.clear();
                for (AssessmentReference ref : session.getAssessments()) {
                    ids.add(ref.getGuid());
                    
                    // scheduleGuid:sessionGuid:startDay:windowGuid:assessmentGuid:asmtOccurrentCount
                    String asmtInstanceGuid = generateAssessmentInstanceGuid(schedule.getGuid(), session.getGuid(),
                            oneEventId, startDay, window.getGuid(), ref.getGuid(), ids.count(ref.getGuid()));
                 
                    AssessmentInfo asmtInfo = AssessmentInfo.create(ref);
                    builder.withAssessmentInfo(asmtInfo);
                    
                    ScheduledAssessment schAsmt = new ScheduledAssessment(asmtInfo.getKey(), asmtInstanceGuid, ref);
                    scheduledSession.withScheduledAssessment(schAsmt);
                }
                
                // scheduleGuid:sessionGuid:eventId:startDay:windowGuid
                String sessionInstanceGuid = generateSessionInstanceGuid(
                        schedule.getGuid(), session.getGuid(), oneEventId, startDay, window.getGuid());
                scheduledSession.withInstanceGuid(sessionInstanceGuid);
                scheduledSession.withStartEventId(oneEventId);
                builder.withScheduledSession(scheduledSession.build());
            }
            startDay += intervalInDays;
            occurrenceCount++;
            
        } while(intervalInDays > 0 && occurrenceCount < occurrenceMax && startDay < studyLengthInDays);
    }
    
    /**
     * Create a unique GUID for a session instance that is the same each time you calculate a Timeline
     * from a Schedule. It should look like a GUID and not a compound identifier, as client developers 
     * have (in the past) parsed compound identifiers, and we want to discourage this.
     */
    String generateSessionInstanceGuid(String scheduleGuid, String sessionGuid, String eventId, int startDay, String windowGuid) {
        Hasher hc = HASHER.newHasher();
        hc.putString(scheduleGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(sessionGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(eventId, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putInt(startDay);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(windowGuid, Charsets.UTF_8);
        return ENCODER.encodeToString(hc.hash().asBytes());
    }
    
    /**
     * Create a unique GUID for an assessment instance that is the same each time you calculate a Timeline
     * from a Schedule. It should look like a GUID and not a compound identifier, as client developers 
     * have (in the past) parsed compound identifiers, and we want to discourage this.
     */
    String generateAssessmentInstanceGuid(String scheduleGuid, String sessionGuid, String eventId, 
            int startDay, String windowGuid, String assessmentGuid, int assessmentOccurrence) {
        Hasher hc = HASHER.newHasher();
        hc.putString(scheduleGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(sessionGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(eventId, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putInt(startDay);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(windowGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putString(assessmentGuid, Charsets.UTF_8);
        hc.putString(":", Charsets.UTF_8);
        hc.putInt(assessmentOccurrence);
        return ENCODER.encodeToString(hc.hash().asBytes());
    }
}
