package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_IDENTIFIER_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_IDENTIFIER_PATTERN;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationValidator extends AbstractValidator {
    
    public static final Validator INSTANCE = new OrganizationValidator();

    @Override
    public void validate(Object target, Errors errors) {
        Organization org = (Organization)target;
        
        if (isBlank(org.getAppId())) {
            errors.rejectValue("appId", "cannot be missing, null or blank");
        }
        if (isBlank(org.getIdentifier())) {
            errors.rejectValue("identifier", "cannot be missing, null or blank");
        } else if (!org.getIdentifier().matches(BRIDGE_IDENTIFIER_PATTERN)) {
            errors.rejectValue("identifier", BRIDGE_IDENTIFIER_ERROR);
        }
        if (isBlank(org.getName())) {
            errors.rejectValue("name", "cannot be missing, null or blank");
        }
    }
}
