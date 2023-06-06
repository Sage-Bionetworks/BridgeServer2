package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

/**
 * Validates a Demographic
 */
public class DemographicValidator implements Validator {
    public static final String MUST_HAVE_ONE_VALUE = "must have exactly 1 value with multipleSelect=false";
    public static final String CANNOT_CONTAIN_NULL = "cannot contain null";

    public static final DemographicValidator INSTANCE = new DemographicValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return Demographic.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Demographic demographic = (Demographic) target;

        if (StringUtils.isBlank(demographic.getId())) {
            errors.rejectValue("id", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (demographic.getDemographicUser() == null) {
            errors.rejectValue("demographicUser", CANNOT_BE_NULL_OR_EMPTY);
        } else if (StringUtils.isBlank(demographic.getDemographicUser().getId())) {
            errors.rejectValue("demographicUser.id", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (StringUtils.isBlank(demographic.getCategoryName())) {
            errors.rejectValue("categoryName", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (demographic.getValues() == null) {
            errors.rejectValue("values", CANNOT_BE_NULL);
        } else {
            if (!demographic.isMultipleSelect() && demographic.getValues().size() != 1) {
                errors.rejectValue("values", MUST_HAVE_ONE_VALUE);
            }

            for (DemographicValue value : demographic.getValues()) {
                if (value == null) {
                    errors.rejectValue("values", CANNOT_CONTAIN_NULL);
                } else {
                    Validate.entity(DemographicValueValidator.INSTANCE, errors, value);
                }
            }
        }

        validateStringLength(errors, 60, demographic.getId(), "id");
        if (demographic.getDemographicUser() != null) {
            validateStringLength(errors, 60, demographic.getDemographicUser().getId(), "demographicUser.id");
        }
        validateStringLength(errors, 768, demographic.getCategoryName(), "categoryName");
        validateStringLength(errors, 512, demographic.getUnits(), "units");
    }
}
