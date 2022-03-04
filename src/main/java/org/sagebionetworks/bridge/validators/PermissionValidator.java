package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.permissions.Permission;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

public class PermissionValidator implements Validator {
    
    public static final PermissionValidator INSTANCE = new PermissionValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Permission.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object target, Errors errors) {
        Permission permission = (Permission) target;
        
        if (isBlank(permission.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        if (isBlank(permission.getAppId())) {
            errors.rejectValue("appId", CANNOT_BE_BLANK);
        }
        if (isBlank(permission.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_BLANK);
        }
        if (permission.getAccessLevel() == null) {
            errors.rejectValue("accessLevel", CANNOT_BE_NULL);
        }
        if (permission.getEntityType() == null) {
            errors.rejectValue("entityType", CANNOT_BE_NULL);
        }
        if (isBlank(permission.getEntityId())) {
            errors.rejectValue("entityId", CANNOT_BE_BLANK);
        }
    }
}
