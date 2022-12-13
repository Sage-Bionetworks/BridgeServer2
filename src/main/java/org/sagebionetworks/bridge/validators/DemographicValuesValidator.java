package org.sagebionetworks.bridge.validators;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.databind.JsonNode;

public interface DemographicValuesValidator {
    void deserializeRules(JsonNode validationRules) throws IOException;

    void validateRules(Errors errors);

    void validateDemographicUsingRules(Demographic demographic);
}
