package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Optional;

import org.apache.commons.validator.routines.EmailValidator;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class IdentifierUpdateValidator implements Validator {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    private App app;
    private ExternalIdService externalIdService;
    
    public IdentifierUpdateValidator(App app, ExternalIdService externalIdService) {
        this.app = app;
        this.externalIdService = externalIdService;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return IdentifierUpdate.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        IdentifierUpdate update = (IdentifierUpdate)object;
        
        SignIn signIn = update.getSignIn();
        if (signIn == null) {
            errors.reject("requires a signIn object");
        } else {
            errors.pushNestedPath("signIn");
            if (signIn.getReauthToken() != null) {
                SignInValidator.REAUTH_SIGNIN.validate(signIn, errors);
            } else {
                SignInValidator.PASSWORD_SIGNIN.validate(signIn, errors);
            }
            errors.popNestedPath();
        }
        // Should have at least one update field.
        int updateFields = 0;
        if (update.getPhoneUpdate() != null) {
            updateFields++;
            if (!Phone.isValid(update.getPhoneUpdate())) {
                errors.rejectValue("phoneUpdate", "does not appear to be a phone number");
            }            
        }
        if (update.getEmailUpdate() != null) {
            updateFields++;
            if (!EMAIL_VALIDATOR.isValid(update.getEmailUpdate())) {
                errors.rejectValue("emailUpdate", "does not appear to be an email address");
            }
        }
        if (update.getExternalIdUpdate() != null) {
            updateFields++;
            if (isBlank(update.getExternalIdUpdate())) {
                errors.rejectValue("externalIdUpdate", "cannot be blank");
            } else {
                // the same validation we perform when adding a participant where external ID is required on sign up.
                Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(app.getIdentifier(),
                        update.getExternalIdUpdate());
                if (!optionalId.isPresent()) {
                    errors.rejectValue("externalIdUpdate", "is not a valid external ID");
                }
            }
        }
        if (update.getSynapseUserIdUpdate() != null) {
            updateFields++;
            if (isBlank(update.getSynapseUserIdUpdate())) {
                errors.rejectValue("synapseUserIdUpdate", "cannot be blank");
            } else if (!update.getSynapseUserIdUpdate().matches("^[0-9]+$")) {
                errors.rejectValue("synapseUserIdUpdate", "should be a string containing a positive integer");
            }
        }
        if (updateFields < 1) {
            errors.reject("requires at least one updated identifier (email, phone, externalId, synapseUserId)");
        }
    }
    
}
