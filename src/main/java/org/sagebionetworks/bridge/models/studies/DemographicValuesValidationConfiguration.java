package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

public class DemographicValuesValidationConfiguration implements BridgeEntity {
    private ValidationType validationType;
    private JsonNode validationRules;

    public static enum ValidationType {
        NUMBER_RANGE,
        ENUM
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    public void setValidationType(ValidationType validationType) {
        this.validationType = validationType;
    }

    public JsonNode getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(JsonNode validationRules) {
        this.validationRules = validationRules;
    }
}
