package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.persistence.JoinColumn;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.sagebionetworks.bridge.json.DemographicDeserializer;
import org.sagebionetworks.bridge.json.DemographicSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity
@Table(name = "Demographics")
@JsonSerialize(using = DemographicSerializer.class)
@JsonDeserialize(using = DemographicDeserializer.class)
public class Demographic {
    @Id
    @NotNull
    private String id;
    @NotNull
    private String studyId;
    @NotNull
    private String userId;
    @NotNull
    private String categoryName;
    @NotNull
    private boolean multipleSelect;
    @ElementCollection
    @CollectionTable(name = "DemographicsValues", joinColumns = @JoinColumn(name = "demographicsId"))
    private List<DemographicValue> values;
    private String units;

    public Demographic(@NotNull String id, @NotNull String studyId, @NotNull String userId,
            @NotNull String categoryName, @NotNull boolean multipleSelect, List<DemographicValue> values,
            String units) {
        this.id = id;
        this.studyId = studyId;
        this.userId = userId;
        this.categoryName = categoryName;
        this.multipleSelect = multipleSelect;
        this.values = values;
        this.units = units;
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
