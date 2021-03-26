package org.sagebionetworks.bridge.models.schedules2.timelines;

public class ScheduledAssessment {

    private final String guid;
    private final String instanceGuid;
    
    public ScheduledAssessment(String guid, String instanceGuid) {
        this.guid = guid;
        this.instanceGuid = instanceGuid;
    }
    public String getGuid() {
        return guid;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }
}
