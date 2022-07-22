package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import java.util.ListIterator;

import javax.annotation.Nonnull;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "Demographics")
public class Demographic implements BridgeEntity {
    @Nonnull
    @Id
    String id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "demographicUserId")
    DemographicUser demographicUser;

    @Nonnull
    @JsonIgnore // used as key to parent DemographicUser map instead
    String categoryName;

    @Nonnull
    private boolean multipleSelect;

    @Nonnull
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "DemographicsValues", joinColumns = @JoinColumn(name = "demographicId", referencedColumnName = "id"))
    private List<DemographicValue> values;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String units;

    public Demographic(String id, DemographicUser demographicUser, String categoryName, boolean multipleSelect,
            List<DemographicValue> values, String units) {
        this.id = id;
        this.demographicUser = demographicUser;
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

    public DemographicUser getDemographicUser() {
        return demographicUser;
    }

    public void setDemographicUser(DemographicUser demographicUser) {
        this.demographicUser = demographicUser;
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

    @JsonProperty("values")
    public void setValues(List<DemographicValue> values) {
        if (values != null) {
            // replace null with DemographicValue(null)
            for (ListIterator<DemographicValue> iter = values.listIterator(); iter.hasNext();) {
                if (iter.next() == null) {
                    iter.set(new DemographicValue(null));
                }
            }
        }
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
                + ", units=" + units + ", values=" + values + "]";
    }
}
