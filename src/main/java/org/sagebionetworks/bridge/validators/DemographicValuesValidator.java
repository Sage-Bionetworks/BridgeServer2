package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.springframework.validation.Errors;

/**
 * This is NOT a validator to check if demographics have malformed inputs. For
 * those validators, see DemographicUserValidator, DemographicValidator, and
 * DemographicValueValidator.
 * 
 * This validator is used to validate the values in demographics to ensure they
 * meet user-specified restrictions ("rules") listed in a
 * DemographicValuesValidationConfig.
 * 
 * Specifically, this validator does 2 things:
 * 2) It can validate the rules themselves using the Spring validator framework
 * 3) It can validate a Demographic using those rules
 * 
 * This functionality is grouped because the deserialization of the rules is
 * done the same way in both cases.
 * 
 * A DemographicValuesValidator can be obtained from
 * DemographicValuesValidationType.getValidatorWithRules().
 */
public interface DemographicValuesValidator {
    void validateRules(Errors errors);

    void validateDemographicUsingRules(Demographic demographic);
}
