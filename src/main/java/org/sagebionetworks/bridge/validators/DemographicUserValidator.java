package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class DemographicUserValidator implements Validator {
    public static final DemographicUserValidator INSTANCE = new DemographicUserValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return DemographicUser.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        DemographicUser demographicUser = (DemographicUser) target;

        if (StringUtils.isBlank(demographicUser.getId())) {
            errors.rejectValue("id", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (StringUtils.isBlank(demographicUser.getAppId())) {
            errors.rejectValue("id", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (StringUtils.isBlank(demographicUser.getUserId())) {
            errors.rejectValue("id", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (demographicUser.getDemographics() == null) {
            errors.rejectValue("values", CANNOT_BE_NULL);
        }
        for (Map.Entry<String, Demographic> entry : demographicUser.getDemographics().entrySet()) {
            if (!entry.getKey().equals(entry.getValue().getDemographicId().getCategoryName())) {
                errors.reject("keys in demographics must match the corresponding Demographic's categoryName");
            }
            Validate.entity(DemographicValidator.INSTANCE, errors, entry.getValue());
        }
    }
}
