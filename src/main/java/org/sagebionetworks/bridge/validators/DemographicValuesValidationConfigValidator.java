package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.io.IOException;

import org.sagebionetworks.bridge.models.demographics.DemographicValuesValidationConfig;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class DemographicValuesValidationConfigValidator implements Validator {
    private static final String INVALID_VALIDATION_RULES = "invalid validation rules";

    public static final DemographicValuesValidationConfigValidator INSTANCE = new DemographicValuesValidationConfigValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return DemographicValuesValidationConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        DemographicValuesValidationConfig config = (DemographicValuesValidationConfig) target;

        if (config.getValidationType() == null) {
            errors.rejectValue("validationType", CANNOT_BE_NULL);
        }
        if (config.getValidationRules() == null || config.getValidationRules().isNull()) {
            errors.rejectValue("validationRules", CANNOT_BE_NULL);
        }
        if (errors.hasErrors()) {
            // cannot continue because something is null
            return;
        }

        DemographicValuesValidator valuesValidator;
        try {
            valuesValidator = config.getValidationType().getValidatorWithRules(config.getValidationRules());
        } catch (IOException e) {
            errors.rejectValue("validationRules", INVALID_VALIDATION_RULES);
            return;
        }
        // perform validation on the rules
        errors.pushNestedPath("validationRules");
        valuesValidator.validateRules(errors);
        errors.popNestedPath();
    }
}
