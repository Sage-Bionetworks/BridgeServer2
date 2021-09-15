package org.sagebionetworks.bridge.models.studies;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public enum StudyPhase {
    /**
     * If not set, the study is in the LEGACY phase, and no domain logic will be
     * applied to the study, enrollments, etc.
     */
    LEGACY,
    /**
     * Study is being designed and tested and has not begun. All accounts created
     * in this phase are marked as test accounts, and schedules are still mutable.
     * The study is not visible in public registries. Study can transition to 
     * PUBLIC_RECRUITMENT, ENROLL_BY_INVITATION, or WITHDRAWN.
     */
    DESIGN,
    /**
     * Study has launched and is visible in public registries, and accepting new
     * participants through some form of enrollment. The schedule is published 
     * when the study is transitioned to this phase, and can no longer change. 
     * Study can transition to IN_FLIGHT or WITHDRAWN.
     */
    RECRUITMENT,
    /**
     * The study is no longer accepting new participants, but participants are 
     * still active in the study. The study is no longer visible in public 
     * registries and will no longer accept new sign ups. Study can transition 
     * to ANALYSIS or WITHDRAWN.
     */
    IN_FLIGHT,
    /**
     * All participants have completed the study protocol, and the data is being
     * analyzed. For IRBs, this study is still open and it should still be 
     * available in administrative UIs for reporting, but no mobile or desktop
     * participant-facing client should be engaged with the study. Study can 
     * transition to COMPLETED or WITHDRAWN.
     */
    ANALYSIS,
    /**
     * Analysis has been completed and the study has been reported to the IRB. 
     * The study can now be logically deleted.
     */
    COMPLETED,
    /**
     * The study was withdrawn before completion. It can be withdrawn from any
     * other phase, and at that point it can be logically deleted. Ideally it 
     * would appear to be no longer available to participants.
     */
    WITHDRAWN;
    
    public String label() {
        return "“" + this.name().toLowerCase() + "”";
    }
    
    public static final Map<StudyPhase, Set<StudyPhase>> ALLOWED_PHASE_TRANSITIONS = new ImmutableMap.Builder<StudyPhase, Set<StudyPhase>>()
            .put(LEGACY, ImmutableSet.of(DESIGN))
            .put(DESIGN, ImmutableSet.of(RECRUITMENT, WITHDRAWN))
            .put(RECRUITMENT, ImmutableSet.of(IN_FLIGHT, WITHDRAWN))
            .put(IN_FLIGHT, ImmutableSet.of(RECRUITMENT, ANALYSIS, WITHDRAWN))
            .put(ANALYSIS, ImmutableSet.of(RECRUITMENT, IN_FLIGHT, COMPLETED, WITHDRAWN))
            .put(COMPLETED, ImmutableSet.of())
            .put(WITHDRAWN, ImmutableSet.of()).build();
    
    // Legacy studies, and studies created in the design phase, are fully editable/deletable, which was
    // their legacy behavior. In later phases, these no longer become possible. 
    public static final Set<StudyPhase> CAN_EDIT_STUDY_METADATA = ImmutableSet.of(LEGACY, DESIGN, RECRUITMENT, IN_FLIGHT);
    public static final Set<StudyPhase> CAN_EDIT_STUDY_CORE = ImmutableSet.of(LEGACY, DESIGN);
    public static final Set<StudyPhase> CAN_DELETE_STUDY = ImmutableSet.of(LEGACY, DESIGN, COMPLETED, WITHDRAWN);

}
