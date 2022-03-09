package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;

import org.sagebionetworks.bridge.models.permissions.Permission;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class PermissionValidator implements Validator {
    
    static final String GUID_FIELD = "guid";
    static final String APP_ID_FIELD = "appId";
    static final String USER_ID_FIELD = "userId";
    static final String ACCESS_LEVEL_FIELD = "accessLevel";
    static final String ENTITY_TYPE_FIELD = "entityType";
    static final String ENTITY_ID_FIELD = "entityId";
    
    public static final PermissionValidator INSTANCE = new PermissionValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Permission.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object target, Errors errors) {
        Permission permission = (Permission) target;
        
        if (isBlank(permission.getGuid())) {
            errors.rejectValue(GUID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
        }
        if (isBlank(permission.getAppId())) {
            errors.rejectValue(APP_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
        }
        if (isBlank(permission.getUserId())) {
            errors.rejectValue(USER_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
        }
        if (permission.getAccessLevel() == null) {
            errors.rejectValue(ACCESS_LEVEL_FIELD, CANNOT_BE_NULL);
        }
        if (permission.getEntityType() == null) {
            errors.rejectValue(ENTITY_TYPE_FIELD, CANNOT_BE_NULL);
        }
        if (isBlank(permission.getEntityId())) {
            errors.rejectValue(ENTITY_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
        }
    }
}
