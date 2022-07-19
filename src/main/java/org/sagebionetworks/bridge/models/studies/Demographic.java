package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import javax.annotation.Nonnull;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "Demographics")
public class Demographic implements BridgeEntity {
    @EmbeddedId
    @JsonIgnore
    DemographicId demographicId;

    @JsonIgnore
    @MapsId("demographicUserId")
    @ManyToOne
    @JoinColumn(name = "demographicUserId")
    DemographicUser demographicUser;

    @Nonnull
    private boolean multipleSelect;

    @Nonnull
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "DemographicsValues", joinColumns = {
            @JoinColumn(name = "demographicUserId", referencedColumnName = "demographicUserId"),
            @JoinColumn(name = "categoryName", referencedColumnName = "categoryName") })
    private List<DemographicValue> values;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String units;

    public Demographic(DemographicId demographicId, DemographicUser demographicUser, boolean multipleSelect,
            List<DemographicValue> values, String units) {
        this.demographicId = demographicId;
        this.demographicUser = demographicUser;
        this.multipleSelect = multipleSelect;
        this.values = values;
        this.units = units;
    }

    public Demographic() {
    }

    public DemographicId getDemographicId() {
        return demographicId;
    }

    public void setDemographicId(DemographicId demographicId) {
        this.demographicId = demographicId;
    }

    public DemographicUser getDemographicUser() {
        return demographicUser;
    }

    public void setDemographicUser(DemographicUser demographicUser) {
        this.demographicUser = demographicUser;
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
        return "Demographic [demographicId=" + demographicId + ", multipleSelect=" + multipleSelect + ", units=" + units
                + ", values=" + values + "]";
    }
}
