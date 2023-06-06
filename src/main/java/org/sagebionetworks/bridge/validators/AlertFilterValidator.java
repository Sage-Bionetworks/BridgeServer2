package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.sagebionetworks.bridge.models.studies.AlertFilter;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AlertFilterValidator implements Validator {
    public static final AlertFilterValidator INSTANCE = new AlertFilterValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return AlertFilter.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AlertFilter alertFilter = (AlertFilter) target;

        if (alertFilter.getAlertCategories() == null) {
            errors.rejectValue("alertCategories", CANNOT_BE_NULL);
        }
    }
}
