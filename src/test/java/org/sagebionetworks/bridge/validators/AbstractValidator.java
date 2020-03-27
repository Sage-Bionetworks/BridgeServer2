package org.sagebionetworks.bridge.validators;

import org.springframework.validation.Validator;

public abstract class AbstractValidator implements Validator {

    // We do not currently use this method because we manually execute validation
    // as part of our service-tier logic. This method was intended for the Spring
    // framework so it can automatically find the appropriate validator.
    @Override
    public boolean supports(Class<?> clazz) {
        return false;
    }

}
