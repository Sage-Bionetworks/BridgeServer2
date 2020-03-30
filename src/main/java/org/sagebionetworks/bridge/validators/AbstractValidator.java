package org.sagebionetworks.bridge.validators;

import org.springframework.validation.Validator;

public abstract class AbstractValidator implements Validator {

    /**
     * We do not currently use this method because we manually execute validation
     * as part of our service-tier logic. This method was intended for the Spring
     * Framework so it can automatically find the appropriate validator. We don't
     * seem likely to ever use this aspect of Spring.
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return false;
    }
}
