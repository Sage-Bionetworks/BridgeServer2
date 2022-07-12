package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "Demographics")
public class Demographic {
    @JsonIgnore
    private String studyId; // future-proofing against userId not being unique to study
    @JsonIgnore
    private String userId;
    @JsonIgnore
    private String categoryName;
    private String value;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String units;

    public Demographic() {
    }

    public Demographic(String studyId, String userId, String categoryName, String value, String units) {
        this.studyId = studyId;
        this.userId = userId;
        this.categoryName = categoryName;
        this.value = value;
        this.units = units;
    }

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    @Override
    public String toString() {
        return "Demographic [categoryName=" + categoryName + ", studyId=" + studyId + ", units=" + units + ", userId="
                + userId + ", value=" + value + "]";
    }
}
