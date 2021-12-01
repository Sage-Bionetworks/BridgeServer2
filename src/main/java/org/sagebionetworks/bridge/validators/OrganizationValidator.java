package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_IDENTIFIER_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_IDENTIFIER_PATTERN;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.MYSQL_TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationValidator extends AbstractValidator {
    
    public static final Validator INSTANCE = new OrganizationValidator();

    @Override
    public void validate(Object target, Errors errors) {
        Organization org = (Organization)target;
        
        if (isBlank(org.getAppId())) {
            errors.rejectValue("appId", CANNOT_BE_BLANK);
        }
        if (isBlank(org.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (!org.getIdentifier().matches(BRIDGE_IDENTIFIER_PATTERN)) {
            errors.rejectValue("identifier", BRIDGE_IDENTIFIER_ERROR);
        }
        if (isBlank(org.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, org.getIdentifier(), "identifier");
        validateStringLength(errors, 255, org.getName(), "name");
        validateStringLength(errors, MYSQL_TEXT_SIZE, org.getDescription(), "description");
    }
}
