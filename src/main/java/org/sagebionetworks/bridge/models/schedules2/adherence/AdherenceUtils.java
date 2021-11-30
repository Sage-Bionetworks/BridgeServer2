package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;

public class AdherenceUtils {

    /**
     * If an event doesn’t exist we mark sessions that are triggered by that event as "not 
     * applicable" which will probably be counted as out-of-compliance, I’m not sure. We’re 
     * not looking at the declined flag...user can decline but we consider it to be out of compliance.
     */
    public static SessionCompletionState calculateSessionState(
            AdherenceRecord record, int startDay, int endDay, Integer daysSinceEvent) {

        // Not at all clear that this means it's not applicable.
        if (daysSinceEvent == null) {
            return NOT_APPLICABLE;
        }
        if (record == null) {
            if (startDay > daysSinceEvent) {
                return NOT_YET_AVAILABLE;
            }
            if (endDay < daysSinceEvent) {
                return EXPIRED;
            }
            return UNSTARTED;
        }
        // If the record is declined, we report it as declined and out of compliance. Note though that
        // we trust the client not to set this and then also finish the session or submit data. We have
        // no way to “prove” the session was truly declined.
        if (record.isDeclined()) {
            return DECLINED;
        }
        if (endDay < daysSinceEvent) {
            if (record.getStartedOn() != null && record.getFinishedOn() != null) {
                return COMPLETED;
            }
            if (record.getStartedOn() != null) {
                return ABANDONED;
            }
            return EXPIRED;
        }
        // This record falls on the day and so it is available to the participant.
        if (record.getStartedOn() != null && record.getFinishedOn() != null) {
            return COMPLETED;
        }
        if (record.getStartedOn() != null) {
            return STARTED;
        }
        return UNSTARTED;
    }
}
