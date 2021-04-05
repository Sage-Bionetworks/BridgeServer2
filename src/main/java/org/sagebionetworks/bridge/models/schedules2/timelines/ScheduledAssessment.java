package org.sagebionetworks.bridge.models.schedules2.timelines;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public class ScheduledAssessment {

    private final String refKey;
    private final String instanceGuid;
    private final AssessmentReference reference;
    
    public ScheduledAssessment(String refKey, String instanceGuid, AssessmentReference reference) {
        this.refKey = refKey;
        this.instanceGuid = instanceGuid;
        this.reference = reference;
    }
    public String getRefKey() {
        return refKey;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    @JsonIgnore
    public AssessmentReference getReference() {
        return reference;
    }
}
