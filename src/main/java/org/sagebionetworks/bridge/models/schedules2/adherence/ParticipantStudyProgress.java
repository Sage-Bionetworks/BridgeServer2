package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.EnumSet;

public enum ParticipantStudyProgress {
    UNSTARTED,
    IN_PROGRESS,
    DONE,
    NO_SCHEDULE;
    
    public static final EnumSet<ParticipantStudyProgress> REPORTABLE_STATES = EnumSet.of(IN_PROGRESS, DONE);
}
