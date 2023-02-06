package org.sagebionetworks.bridge.validators;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.sagebionetworks.bridge.models.studies.AlertIdCollection;

public class AlertIdCollectionValidator implements Validator {
    public static final AlertIdCollectionValidator INSTANCE = new AlertIdCollectionValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return AlertIdCollection.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AlertIdCollection alertIdCollection = (AlertIdCollection) target;

        if (alertIdCollection.getAlertIds() == null) {
            errors.rejectValue("alertIds", CANNOT_BE_NULL);
        }
    }
}
