package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

public class DetailedAdherenceReportAssessmentRecord {
    private String assessmentName;
    private String assessmentId;
    private String assessmentGuid;
    private String assessmentInstanceGuid;
    private String assessmentStatus;
    private DateTime assessmentStart; // date time
    private DateTime assessmentCompleted; // date time
    private DateTime assessmentUploadedOn;
    private int sortPriority;
    
    public String getAssessmentName() {
        return assessmentName;
    }
    
    public void setAssessmentName(String assessmentName) {
        this.assessmentName = assessmentName;
    }
    
    public String getAssessmentId() {
        return assessmentId;
    }
    
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }
    
    public String getAssessmentGuid() {
        return assessmentGuid;
    }
    
    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }
    
    public String getAssessmentInstanceGuid() {
        return assessmentInstanceGuid;
    }
    
    public void setAssessmentInstanceGuid(String assessmentInstanceGuid) {
        this.assessmentInstanceGuid = assessmentInstanceGuid;
    }
    
    public String getAssessmentStatus() {
        return assessmentStatus;
    }
    
    public void setAssessmentStatus(String assessmentStatus) {
        this.assessmentStatus = assessmentStatus;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getAssessmentStart() {
        return assessmentStart;
    }
    
    public void setAssessmentStart(DateTime assessmentStart) {
        this.assessmentStart = assessmentStart;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getAssessmentCompleted() {
        return assessmentCompleted;
    }
    
    public void setAssessmentCompleted(DateTime assessmentCompleted) {
        this.assessmentCompleted = assessmentCompleted;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getAssessmentUploadedOn() {
        return assessmentUploadedOn;
    }
    
    public void setAssessmentUploadedOn(DateTime assessmentUploadedOn) {
        this.assessmentUploadedOn = assessmentUploadedOn;
    }
    
    @JsonIgnore
    public int getSortPriority() {
        return sortPriority;
    }
    
    public void setSortPriority(int sortPriority) {
        this.sortPriority = sortPriority;
    }
}
