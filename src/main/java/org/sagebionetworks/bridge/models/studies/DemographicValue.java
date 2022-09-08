package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents a single value out of possibly multiple values in a specific
 * demographic category for a specific user
 */
@Embeddable
public class DemographicValue implements BridgeEntity {
    @NotNull
    private String value;

    public DemographicValue() {
    }

    @JsonCreator
    public DemographicValue(String value) {
        this.value = value;
    }

    @JsonCreator
    public DemographicValue(long value) {
        this.value = String.valueOf(value);
    }

    @JsonCreator
    public DemographicValue(double value) {
        this.value = String.valueOf(value);
    }

    @JsonCreator
    public DemographicValue(boolean value) {
        this.value = String.valueOf(value);
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
