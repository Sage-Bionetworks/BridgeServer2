package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

/**
 * Validator for the Exporter 3 implementation of Health Data Record. This just checks basic things, like Records must
 * have app, healthcode, and createdOn.
 */
public class HealthDataRecordEx3Validator implements Validator {
    /** Singleton instance of this validator. */
    public static final HealthDataRecordEx3Validator INSTANCE = new HealthDataRecordEx3Validator();

    @Override
    public boolean supports(Class<?> clazz) {
        return HealthDataRecordEx3.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        //noinspection ConstantConditions
        if (object == null) {
            errors.rejectValue("HealthDataRecordEx3", Validate.CANNOT_BE_NULL);
        } else if (!(object instanceof HealthDataRecordEx3)) {
            errors.rejectValue("HealthDataRecordEx3", Validate.INVALID_TYPE);
        } else {
            HealthDataRecordEx3 record = (HealthDataRecordEx3) object;

            if (StringUtils.isBlank(record.getAppId())) {
                errors.rejectValue("appId", "is required");
            }

            if (StringUtils.isBlank(record.getHealthCode())) {
                errors.rejectValue("healthCode", "is required");
            }

            if (record.getCreatedOn() == null) {
                errors.rejectValue("createdOn", "is required");
            }
        }
    }
}
