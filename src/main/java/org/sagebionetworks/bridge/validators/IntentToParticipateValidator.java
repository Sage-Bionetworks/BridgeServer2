package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;

import org.apache.commons.validator.routines.EmailValidator;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class IntentToParticipateValidator implements Validator {
    public static final IntentToParticipateValidator INSTANCE = new IntentToParticipateValidator();
    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    
    private IntentToParticipateValidator() {
    }
    
    public boolean supports(Class<?> clazz) {
        return IntentToParticipate.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        IntentToParticipate intent = (IntentToParticipate)object;
        
        if (isBlank(intent.getAppId())) {
            errors.rejectValue("appId", "is required");
        }
        if (isBlank(intent.getSubpopGuid())) {
            errors.rejectValue("subpopGuid", "is required");
        }
        if (intent.getScope() == null) {
            errors.rejectValue("scope", "is required");
        }
        boolean hasPhoneOrEmail = (intent.getPhone() != null || intent.getEmail() != null);
        boolean hasPhoneAndEmail = (intent.getPhone() != null && intent.getEmail() != null);
        if (hasPhoneAndEmail) {
            errors.reject("one of phone or email should be provided (not both)");
        } else if (!hasPhoneOrEmail) {
            errors.reject("either phone or email is required");
        } else {
            if (intent.getPhone() != null && !Phone.isValid(intent.getPhone())) {
                errors.rejectValue("phone", INVALID_PHONE_ERROR);
            }
            if (intent.getEmail() != null && !EMAIL_VALIDATOR.isValid(intent.getEmail())) {
                errors.rejectValue("email", INVALID_EMAIL_ERROR);
            }
        }
        if (intent.getConsentSignature() == null) {
            errors.rejectValue("consentSignature", "is required");
        } else {
            // consent signature is validated during construction, which
            // prevents us from doing this here
        }
    }
}
