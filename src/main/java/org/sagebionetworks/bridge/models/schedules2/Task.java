package org.sagebionetworks.bridge.models.schedules2;

public class Task {

    private String label;
    private String assessmentGuid;
    
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getAssessmentGuid() {
        return assessmentGuid;
    }
    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }
}
