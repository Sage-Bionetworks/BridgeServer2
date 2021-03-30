package org.sagebionetworks.bridge.models.schedules2.timelines;

public class ScheduledAssessment {

    private final String refKey;
    private final String instanceGuid;
    
    public ScheduledAssessment(String refKey, String instanceGuid) {
        this.refKey = refKey;
        this.instanceGuid = instanceGuid;
    }
    public String getRefKey() {
        return refKey;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
}
