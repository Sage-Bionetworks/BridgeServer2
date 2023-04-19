package org.sagebionetworks.bridge.models.schedules2.adherence;

public enum AssessmentCompletionState {
    
    /** The assessment was finished by the participant. */
    COMPLETED,
    /** The assessment was started but not finished. */
    NOT_COMPLETED,
    /** The assessment was declined by the participant. */
    DECLINED
}
