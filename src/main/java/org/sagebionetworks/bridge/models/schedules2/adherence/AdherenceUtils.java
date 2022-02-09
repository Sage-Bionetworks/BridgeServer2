package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLIANT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NONCOMPLIANT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNKNOWN;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;

public class AdherenceUtils {

    /**
     * If an event doesn’t exist we mark sessions that are triggered by that event as "not 
     * applicable" which will probably be counted as out-of-compliance, I’m not sure. We’re 
     * not looking at the declined flag...user can decline but we consider it to be out of compliance.
     */
    public static SessionCompletionState calculateSessionState(AdherenceRecord record, 
            int startDay, int endDay, Integer daysSinceEvent) {
        if (daysSinceEvent == null) {
            return NOT_APPLICABLE;
        }
        if (record != null && record.isDeclined()) {
            return DECLINED;
        }
        if (record == null || (record.getStartedOn() == null && record.getFinishedOn() == null)) {
            if (startDay > daysSinceEvent) {
                return NOT_YET_AVAILABLE;
            }
            if (endDay < daysSinceEvent) {
                return EXPIRED;
            }
            return UNSTARTED;
        }
        if (record.getFinishedOn() != null) {
            return COMPLETED;   
        }
        if (endDay < daysSinceEvent) {
            return ABANDONED;
        }
        return STARTED;
    }
    
    public static int calculateAdherencePercentage(Collection<EventStream> streams) {
        long compliantSessions = counting(streams, COMPLIANT);
        long noncompliantSessions = counting(streams, NONCOMPLIANT);
        long unkSessions = counting(streams, UNKNOWN);
        long totalSessions = compliantSessions + noncompliantSessions + unkSessions;

        float percentage = 1.0f;
        if (totalSessions > 0) {
            percentage = ((float) compliantSessions / (float) totalSessions);
        }
        return (int) (percentage * 100);
    }
    
    public static ParticipantStudyProgress calculateProgress(AdherenceState state, List<EventStream> eventStreams) {
        if (state.getMetadata().isEmpty()) {
            return ParticipantStudyProgress.NO_SCHEDULE;
        }
        long total = counting(eventStreams, EnumSet.allOf(SessionCompletionState.class));
        long na = counting(eventStreams, EnumSet.of(NOT_APPLICABLE));
        long done = counting(eventStreams, EnumSet.of(ABANDONED, EXPIRED, DECLINED, COMPLETED));
        
        if (na == total) {
            return ParticipantStudyProgress.UNSTARTED;
        } else if ((na + done) == total) {
            return ParticipantStudyProgress.DONE;
        }
        return ParticipantStudyProgress.IN_PROGRESS;
    }
    
    public static long counting(Collection<EventStream> streams, Set<SessionCompletionState> states) {
      return streams.stream()
          .flatMap(es -> es.getByDayEntries().values().stream())
          .flatMap(list -> list.stream())
          .flatMap(esd -> esd.getTimeWindows().stream())
          .filter(win -> states.contains(win.getState()))
          .collect(Collectors.counting());
    }
}
