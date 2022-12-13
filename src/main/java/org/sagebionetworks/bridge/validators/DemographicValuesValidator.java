package org.sagebionetworks.bridge.validators;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * This is NOT a validator to check if demographics have malformed inputs. For
 * those validators, see DemographicUserValidator, DemographicValidator, and
 * DemographicValueValidator.
 * 
 * This validator is used to validate the values in demographics to ensure they
 * meet user-specified restrictions listed in a
 * DemographicValuesValidationConfig.
 * 
 * Specifically, this validator does 3 things:
 * 1) Deserializes a JSON blob of validationRules from a
 * DemographicValuesValidationConfig into a specific rule type based upon the
 * validationType specified in the DemographicValuesValidationConfig
 * 2) After 1), it can validate the rules themselves using the Spring validator
 * framework
 * 3) After 1), it can validate a Demographic using those rules
 * 
 * This functionality is grouped because the deserialization of the rules (1) is
 * done the same way in two separate cases: validating rules (2) when they are
 * uploaded in a DemographicValuesValidationConfig and validating demographics
 * (3) when demographics are uploaded.
 * 
 * A DemographicValuesValidator can be obtained from
 * DemographicValuesValidationType.getValidator().
 */
public interface DemographicValuesValidator {
    void deserializeRules(JsonNode validationRules) throws IOException;

    void validateRules(Errors errors);

    void validateDemographicUsingRules(Demographic demographic);
}
