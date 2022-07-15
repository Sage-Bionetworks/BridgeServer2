package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import javax.annotation.Nonnull;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;

import org.sagebionetworks.bridge.json.DemographicDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Entity
@Table(name = "Demographics")
// @IdClass(DemographicId.class)
// @JsonDeserialize(using = DemographicDeserializer.class)
public class Demographic {
    @EmbeddedId
    DemographicId demographicId;

    @MapsId("demographicUserId")
    @ManyToOne
    @JoinColumn(name = "demographicUserId")
    DemographicUser demographicUser;

    // @Id
    // @Nonnull
    // private String demographicUserId;

    // @Id
    // @Nonnull
    // @JsonIgnore
    // private String categoryName;

    @Nonnull
    private boolean multipleSelect;

    @Nonnull
    @ElementCollection
    @CollectionTable(name = "DemographicsValues", joinColumns = {@JoinColumn(name = "demographicUserId", referencedColumnName = "demographicUserId"), @JoinColumn(name = "categoryName", referencedColumnName = "categoryName")})
    // @JoinColumns({ @JoinColumn(name = "demographicUserId", referencedColumnName = "demographicUserId"),
    //         @JoinColumn(name = "categoryName", referencedColumnName = "categoryName") })
    // private List<String> values;
    private List<DemographicValue> values;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String units;

    // public Demographic(String demographicUserId, String categoryName, boolean multipleSelect, List<String> values,
    //         String units) {
    //     this.demographicUserId = demographicUserId;
    //     this.categoryName = categoryName;
    //     this.multipleSelect = multipleSelect;
    //     this.values = values;
    //     this.units = units;
    // }

    // public Demographic() {
    // }

    // @MapsId("demographicUserId")
    // public String getDemographicUserId() {
    //     return demographicUserId;
    // }

    // public void setDemographicUserId(String demographicUserId) {
    //     this.demographicUserId = demographicUserId;
    // }

    // public String getCategoryName() {
    //     return categoryName;
    // }

    // public void setCategoryName(String categoryName) {
    //     this.categoryName = categoryName;
    // }

    // public boolean isMultipleSelect() {
    //     return multipleSelect;
    // }

    // public void setMultipleSelect(boolean multipleSelect) {
    //     this.multipleSelect = multipleSelect;
    // }

    // public List<String> getValues() {
    //     return values;
    // }

    // public void setValues(List<String> values) {
    //     this.values = values;
    // }

    // public String getUnits() {
    //     return units;
    // }

    // public void setUnits(String units) {
    //     this.units = units;
    // }

    @Override
    public String toString() {
        return "Demographic [demographicId=" + demographicId + ", multipleSelect=" + multipleSelect + ", units=" + units + ", values=" + values + "]";
    }

    // @Override
    // public String toString() {
    //     return "Demographic [categoryName=" + categoryName + ", demographicUserId=" + demographicUserId
    //             + ", multipleSelect=" + multipleSelect + ", units=" + units + ", values=" + values + "]";
    // }
}
