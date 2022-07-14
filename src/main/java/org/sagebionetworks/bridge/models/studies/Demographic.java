package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import javax.annotation.Nonnull;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.JoinColumn;

import org.sagebionetworks.bridge.json.DemographicDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Entity
@Table(name = "Demographics")
@JsonDeserialize(using = DemographicDeserializer.class)
public class Demographic {
    @Id
    @Nonnull
    @JsonIgnore
    private String id;
    @Nonnull
    @JsonIgnore
    private String studyId;
    @Nonnull
    private String userId;
    @Nonnull
    @JsonIgnore
    private String categoryName;
    @Nonnull
    private boolean multipleSelect;
    @Nonnull
    @ElementCollection
    @CollectionTable(name = "DemographicsValues", joinColumns = @JoinColumn(name = "demographicsId"))
    private List<DemographicValue> values;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String units;

    public Demographic(String id, String studyId, String userId, String categoryName, boolean multipleSelect,
            List<DemographicValue> values, String units) {
        this.id = id;
        this.studyId = studyId;
        this.userId = userId;
        this.categoryName = categoryName;
        this.multipleSelect = multipleSelect;
        this.values = values;
        this.units = units;
    }

    public Demographic() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isMultipleSelect() {
        return multipleSelect;
    }

    public void setMultipleSelect(boolean multipleSelect) {
        this.multipleSelect = multipleSelect;
    }

    public List<DemographicValue> getValues() {
        return values;
    }

    public void setValues(List<DemographicValue> values) {
        this.values = values;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    @Override
    public String toString() {
        return "Demographic [categoryName=" + categoryName + ", id=" + id + ", multipleSelect=" + multipleSelect
                + ", studyId=" + studyId + ", units=" + units + ", userId=" + userId + ", values=" + values + "]";
    }

}
