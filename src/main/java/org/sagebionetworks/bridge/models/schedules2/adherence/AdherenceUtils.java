package org.sagebionetworks.bridge.models.schedules2.adherence;

import static java.util.stream.Collectors.counting;
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
        long compliantSessions = streams.stream()
                .flatMap(es -> es.getByDayEntries().values().stream()).flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream()).filter(tw -> COMPLIANT.contains(tw.getState()))
                .collect(counting());
        long noncompliantSessions = streams.stream()
                .flatMap(es -> es.getByDayEntries().values().stream()).flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream()).filter(tw -> NONCOMPLIANT.contains(tw.getState()))
                .collect(counting());
        long unkSessions = streams.stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream()).flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> UNKNOWN.contains(tw.getState())).collect(counting());

        long totalSessions = compliantSessions + noncompliantSessions + unkSessions;

        float percentage = 1.0f;
        if (totalSessions > 0) {
            percentage = ((float) compliantSessions / (float) totalSessions);
        }
        return (int) (percentage * 100);
    }
}
