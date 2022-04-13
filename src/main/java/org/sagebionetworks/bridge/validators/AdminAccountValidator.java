package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_TIME_ZONE;
import static org.sagebionetworks.bridge.validators.Validate.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AdminAccountValidator implements Validator {

    private PasswordPolicy passwordPolicy;
    private Set<String> userProfileAttributes;
    
    public AdminAccountValidator(PasswordPolicy passwordPolicy, Set<String> userProfileAttributes) {
        this.passwordPolicy = passwordPolicy;
        this.userProfileAttributes = userProfileAttributes;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Account.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Account account = (Account)target;
        
        
        if (!ValidatorUtils.accountHasValidIdentifier(account)) {
            errors.reject("email, phone, or synapseUserId is required");
        }
        Phone phone = account.getPhone();
        if (phone != null && !Phone.isValid(phone)) {
            errors.rejectValue("phone", INVALID_PHONE_ERROR);
        }
        // If provided, email must be valid. Commons email validator v1.7 causes our test to 
        // fail because the word "test" appears in the user name, for reasons I could not 
        // deduce from their code. So we have switched to using OWASP regular expression to 
        // match valid email addresses.
        String email = account.getEmail();
        if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
            errors.rejectValue("email", INVALID_EMAIL_ERROR);
        }
        String password = account.getPassword();
        if (password != null) {
            ValidatorUtils.password(errors, passwordPolicy, password);
        }
        if (account.getSynapseUserId() != null && isBlank(account.getSynapseUserId())) {
            errors.rejectValue("synapseUserId", CANNOT_BE_BLANK);
        }
        for (String attributeName : account.getAttributes().keySet()) {
            if (!userProfileAttributes.contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(userProfileAttributes, attributeName));
            } else {
                String attributeValue = account.getAttributes().get(attributeName);
                validateStringLength(errors, 255, attributeValue,"attributes["+attributeName+"]");
            }
        }
        if (account.getClientTimeZone() != null) {
            try {
                ZoneId.of(account.getClientTimeZone());
            } catch (DateTimeException e) {
                errors.rejectValue("clientTimeZone", INVALID_TIME_ZONE);
            }
        }
        validateStringLength(errors, 255, account.getEmail(), "email");
        validateStringLength(errors, 255, account.getFirstName(), "firstName");
        validateStringLength(errors, 255, account.getLastName(), "lastName");
        validateJsonLength(errors, TEXT_SIZE, account.getClientData(), "clientData");
    }

    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for app (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}
