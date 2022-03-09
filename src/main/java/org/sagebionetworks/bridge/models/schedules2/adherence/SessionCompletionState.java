package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.EnumSet;

public enum SessionCompletionState {
    
    /** Session will occur but is still in the future for the participant. */
    NOT_YET_AVAILABLE, 
    /** Session is currently available to be started by the participant. */
    UNSTARTED,
    /** Session is available and started. */
    STARTED,
    /** Session was completed before it expired. */
    COMPLETED, 
    /** Session was started, but it expired before it was finished; it is not in compliance. */
    ABANDONED,
    /** Session is now in the past for the participant and never started; it is not in compliance. */
    EXPIRED,
    /** Session will not occur for this participant because they don't have the triggering event. */
    NOT_APPLICABLE,
    /** After becoming available but before expiring, the session was declined; it is not in compliance. */
    DECLINED;

    // These include all sessions except sessions that are not yet available, and thus cannot be included
    // in a calculation of adherence.
    public static final EnumSet<SessionCompletionState> NONCOMPLIANT = EnumSet.of(ABANDONED, EXPIRED, DECLINED);
    public static final EnumSet<SessionCompletionState> COMPLIANT = EnumSet.of(COMPLETED);
    public static final EnumSet<SessionCompletionState> UNKNOWN = EnumSet.of(UNSTARTED, STARTED, NOT_YET_AVAILABLE);
}
