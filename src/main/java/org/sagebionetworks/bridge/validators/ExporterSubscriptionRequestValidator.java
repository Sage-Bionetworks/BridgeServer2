package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;

/**
 * Validator for ExporterSubscriptionRequest. Checks that values are specified. Does not attempt to validate
 * SNS values.
 */
public class ExporterSubscriptionRequestValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final ExporterSubscriptionRequestValidator INSTANCE = new ExporterSubscriptionRequestValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return ExporterSubscriptionRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        //noinspection ConstantConditions
        if (object == null) {
            errors.rejectValue("ExporterSubscriptionRequest", Validate.CANNOT_BE_NULL);
        } else if (!(object instanceof ExporterSubscriptionRequest)) {
            errors.rejectValue("ExporterSubscriptionRequest", Validate.INVALID_TYPE);
        } else {
            ExporterSubscriptionRequest request = (ExporterSubscriptionRequest) object;

            if (StringUtils.isBlank(request.getEndpoint())) {
                errors.rejectValue("endpoint", "is required");
            }

            if (StringUtils.isBlank(request.getProtocol())) {
                errors.rejectValue("protocol", "is required");
            }
        }
    }
}
