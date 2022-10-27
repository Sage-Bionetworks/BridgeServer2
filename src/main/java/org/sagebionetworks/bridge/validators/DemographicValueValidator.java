package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates a DemographicValue
 */
public class DemographicValueValidator implements Validator {
    public static final DemographicValueValidator INSTANCE = new DemographicValueValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return DemographicValue.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        DemographicValue demographicValue = (DemographicValue) target;

        if (StringUtils.isBlank(demographicValue.getValue())) {
            errors.rejectValue("value", CANNOT_BE_NULL_OR_EMPTY);
        }

        validateStringLength(errors, 1024, demographicValue.getValue(), "value");
    }
}
