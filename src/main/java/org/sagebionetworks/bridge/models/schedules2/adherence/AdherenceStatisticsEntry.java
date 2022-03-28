package org.sagebionetworks.bridge.models.schedules2.adherence;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "label", "searchableLabel", "sessionName", "weekInStudy", "studyBurstId", "studyBurstNum",
        "totalActive", "type" })
public class AdherenceStatisticsEntry {
    private String label;
    private String searchableLabel;
    private String sessionName;
    private Integer weekInStudy;
    private String studyBurstId;
    private Integer studyBurstNum;
    private Integer totalActive;
    
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getSearchableLabel() {
        return searchableLabel;
    }
    public void setSearchableLabel(String searchableLabel) {
        this.searchableLabel = searchableLabel;;
    }
    public String getSessionName() {
        return sessionName;
    }
    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public Integer getWeekInStudy() {
        return weekInStudy;
    }
    public void setWeekInStudy(Integer weekInStudy) {
        this.weekInStudy = weekInStudy;
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public void setStudyBurstId(String studyBurstId) {
        this.studyBurstId = studyBurstId;
    }
    public Integer getStudyBurstNum() {
        return studyBurstNum;
    }
    public void setStudyBurstNum(Integer studyBurstNum) {
        this.studyBurstNum = studyBurstNum;
    }
    public Integer getTotalActive() {
        return totalActive;
    }
    public void setTotalActive(Integer totalActive) {
        this.totalActive = totalActive;
    }

}
