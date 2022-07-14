package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

public class DemographicValidator implements Validator {
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
        if (StringUtils.isBlank(demographic.getStudyId())) {
            errors.rejectValue("studyId", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (StringUtils.isBlank(demographic.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (demographic.getValues() == null) {
            errors.rejectValue("values", CANNOT_BE_NULL);
        }
        if (!demographic.isMultipleSelect() && demographic.getValues().size() != 1) {
            errors.rejectValue("multipleSelect", "must have exactly 1 value with multipleSelect=false");
        }
    }
}
