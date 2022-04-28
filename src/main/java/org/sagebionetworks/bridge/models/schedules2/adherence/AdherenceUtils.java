package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLIANT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.OFFERED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

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
    
    public static int calculateAdherencePercentage(Map<Integer, List<EventStreamDay>> byDayEntries) {
        long compliant = count(byDayEntries.values().stream(), COMPLIANT);
        long total = count(byDayEntries.values().stream(), OFFERED);

        return calcPercent(compliant, total);
    }
    
    public static int calculateAdherencePercentage(Collection<EventStream> streams) {
        long compliant = count(streams.stream()
                .flatMap(es -> es.getByDayEntries().values().stream()), COMPLIANT);
        long total = count(streams.stream()
                .flatMap(es -> es.getByDayEntries().values().stream()), OFFERED);
        
        return calcPercent(compliant, total);
    }
    
    public static ParticipantStudyProgress calculateProgress(AdherenceState state, List<EventStream> eventStreams) {
        if (state.getMetadata().isEmpty()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        long total = count(eventStreams.stream().flatMap(es -> es.getByDayEntries().values().stream()),
                EnumSet.allOf(SessionCompletionState.class));
        long na = count(eventStreams.stream().flatMap(es -> es.getByDayEntries().values().stream()),
                EnumSet.of(NOT_APPLICABLE));
        long done = count(eventStreams.stream().flatMap(es -> es.getByDayEntries().values().stream()),
                EnumSet.of(ABANDONED, EXPIRED, DECLINED, COMPLETED));
        
        if (na == total) {
            return ParticipantStudyProgress.UNSTARTED;
        } else if ((na + done) == total) {
            return ParticipantStudyProgress.DONE;
        }
        return ParticipantStudyProgress.IN_PROGRESS;
    }
    
    private static int calcPercent(long compliant, long total) {
        float percentage = 1.0f;
        if (total > 0) {
            percentage = ((float) compliant / (float) total);
        }
        // This truncates to zero for <1%.
        return (int) (percentage * 100);
    }
    
    private static long count(Stream<List<EventStreamDay>> day, Set<SessionCompletionState> states) {
        return day.flatMap(list -> list.stream())
            .flatMap(esd -> esd.getTimeWindows().stream())
            .filter(win -> states.contains(win.getState()))
            .collect(Collectors.counting());
    }
}
