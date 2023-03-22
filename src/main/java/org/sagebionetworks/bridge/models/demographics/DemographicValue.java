package org.sagebionetworks.bridge.models.demographics;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;

import org.sagebionetworks.bridge.json.DemographicValueDeserializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents a single value out of possibly multiple values in a specific
 * demographic category for a specific user
 */
@Embeddable
@JsonDeserialize(using = DemographicValueDeserializer.class)
public class DemographicValue implements BridgeEntity {
    @NotNull
    private String value;
    // empty: valid
    // non-empty: contains reason for invalidity
    private String invalidity;

    public DemographicValue() {
    }

    public DemographicValue(String value) {
        this.value = value;
    }

    public DemographicValue(String key, String value) {
        this.value = key + "=" + value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getInvalidity() {
        return invalidity;
    }

    public void setInvalidity(String invalidity) {
        this.invalidity = invalidity;
    }

    public DemographicValue withInvalidity(String invalidity) {
        this.invalidity = invalidity;
        return this;
    }

    @Override
    public String toString() {
        return "DemographicValue [value=" + value + ", invalidity=" + invalidity + "]";
    }
}
