package org.sagebionetworks.bridge.models.schedules2;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.Embeddable;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * This object has some code to convert to/from a nested JSON structure, since we're
 * collapsing some fields here to make persistence easier and to add a foreign key
 * constraint to the assessment GUID.
 */
@Embeddable
@Table(name = "AssessmentReferences")
@BridgeTypeName("AssessmentRef")
public class AssessmentReference {

    private String guid;
    private String assessmentAppId;
    private String assessmentGuid;
    
    @JsonProperty("assessment")
    public void setAssessment(Map<String, Object> amtMap) {
        this.assessmentAppId = (String)amtMap.get("appId");
        this.assessmentGuid = (String)amtMap.get("guid");
    }
    public Map<String, String> getAssessment() {
        Map<String, String> map = new HashMap<>();
        map.put("appId", assessmentAppId);
        map.put("guid", assessmentGuid);
        return map;
    }
    
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @JsonIgnore
    public String getAssessmentAppId() {
        return assessmentAppId;
    }
    public void setAssessmentAppId(String assessmentAppId) {
        this.assessmentAppId = assessmentAppId;
    }
    @JsonIgnore
    public String getAssessmentGuid() {
        return assessmentGuid;
    }
    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }
}
