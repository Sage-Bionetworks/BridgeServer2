package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonValue;

// @Embeddable
// public class DemographicValue {
//     @NotNull
//     private String demographicsId;
//     @NotNull
//     private String value;

//     public DemographicValue() {
//     }

//     public DemographicValue(String demographicsId, String value) {
//         this.demographicsId = demographicsId;
//         this.value = value;
//     }

//     public String getDemographicsId() {
//         return demographicsId;
//     }

//     public void setDemographicsId(String demographicsId) {
//         this.demographicsId = demographicsId;
//     }

//     public String getValue() {
//         return value;
//     }

//     public void setValue(String value) {
//         this.value = value;
//     }

//     @Override
//     public String toString() {
//         return "DemographicValue [demographicsId=" + demographicsId + ", value=" + value + "]";
//     }
// }

@Embeddable
public class DemographicValue {
    @NotNull
    private String value;

    public DemographicValue() {
    }

    public DemographicValue(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "DemographicValue [value=" + value + "]";
    }
}
