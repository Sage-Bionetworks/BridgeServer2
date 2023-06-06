package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates a DemographicUser
 */
public class DemographicUserValidator implements Validator {
    public static final String KEYS_MUST_MATCH = "keys in demographics must match the corresponding Demographic's categoryName";
    public static final String CHILD_MUST_STORE_PARENT = "child Demographic must store correct parent";

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
            errors.rejectValue("appId", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (StringUtils.isBlank(demographicUser.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_NULL_OR_EMPTY);
        }
        if (demographicUser.getDemographics() == null) {
            errors.rejectValue("demographics", CANNOT_BE_NULL);
        } else {
            for (Map.Entry<String, Demographic> entry : demographicUser.getDemographics().entrySet()) {
                if (StringUtils.isBlank(entry.getKey())) {
                    errors.rejectValue("demographics key", CANNOT_BE_NULL_OR_EMPTY);
                    continue;
                }
                if (entry.getValue() == null) {
                    errors.rejectValue("demographics value", CANNOT_BE_NULL);
                    continue;
                }
                Validate.entity(DemographicValidator.INSTANCE, errors, entry.getValue());
                if (!entry.getKey().equals(entry.getValue().getCategoryName())) {
                    errors.reject(KEYS_MUST_MATCH);
                }
                if (!demographicUser.equals(entry.getValue().getDemographicUser())) {
                    errors.reject(CHILD_MUST_STORE_PARENT);
                }
            }
        }

        validateStringLength(errors, 60, demographicUser.getId(), "id");
        validateStringLength(errors, 60, demographicUser.getStudyId(), "studyId");
        validateStringLength(errors, 60, demographicUser.getAppId(), "appId");
        validateStringLength(errors, 255, demographicUser.getUserId(), "userId");
    }
}
