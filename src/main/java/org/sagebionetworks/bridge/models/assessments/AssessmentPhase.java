package org.sagebionetworks.bridge.models.assessments;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

public enum AssessmentPhase {

    /**
     * Assessment is still actively being developed. The AssessmentConfig associated with it can be edited.
     */
    DRAFT,

    /**
     * Assessment is ready to be included in a schedule for review and testing. The AssessmentConfig associated with it
     * cannot be edited. To update the AssessmentConfig the Assessment must be moved back into DRAFT phase.
     */
    REVIEW,

    /**
     * Assessment is now being used in a live study. The AssessmentConfig associated with it cannot be edited and the
     * phase can no longer be changed.
     */
    PUBLISHED;

    public String label() {
        return "“" + this.name().toLowerCase() + "”";
    }

    public static final Map<AssessmentPhase, Set<AssessmentPhase>> ALLOWED_PHASE_TRANSITIONS = new ImmutableMap.Builder<AssessmentPhase, Set<AssessmentPhase>>()
            .put(DRAFT, ImmutableSet.of(REVIEW, PUBLISHED))
            .put(REVIEW, ImmutableSet.of(DRAFT, PUBLISHED))
            .put(PUBLISHED, ImmutableSet.of()).build();

    public static final Set<AssessmentPhase> CAN_EDIT_ASSESSMENT_CONFIG = ImmutableSet.of(DRAFT);

}
