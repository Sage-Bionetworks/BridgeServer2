package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class DemographicValuesValidationConfigurationValidator implements Validator {
    public static final DemographicValuesValidationConfigurationValidator INSTANCE = new DemographicValuesValidationConfigurationValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return DemographicValuesValidationConfiguration.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        DemographicValuesValidationConfiguration configuration = (DemographicValuesValidationConfiguration) target;
        if (configuration.getValidationType() == null) {
            errors.rejectValue("validationType", CANNOT_BE_NULL);
        }
        if (configuration.getValidationRules() == null) {
            errors.rejectValue("validationRules", CANNOT_BE_NULL);
        }
    }
}