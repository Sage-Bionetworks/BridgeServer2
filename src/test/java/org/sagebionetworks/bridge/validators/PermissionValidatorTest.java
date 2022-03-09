package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.PermissionValidator.ACCESS_LEVEL_FIELD;
import static org.sagebionetworks.bridge.validators.PermissionValidator.APP_ID_FIELD;
import static org.sagebionetworks.bridge.validators.PermissionValidator.ENTITY_ID_FIELD;
import static org.sagebionetworks.bridge.validators.PermissionValidator.ENTITY_TYPE_FIELD;
import static org.sagebionetworks.bridge.validators.PermissionValidator.GUID_FIELD;
import static org.sagebionetworks.bridge.validators.PermissionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.PermissionValidator.USER_ID_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;

import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.testng.annotations.Test;

public class PermissionValidatorTest {
    
    @Test
    public void validates() {
        Validate.entityThrowingException(INSTANCE, createPermission());
    }
    
    @Test
    public void guidNull() {
        Permission permission = createPermission();
        permission.setGuid(null);
        assertValidatorMessage(INSTANCE, permission, GUID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void guidBlank() {
        Permission permission = createPermission();
        permission.setGuid("");
        assertValidatorMessage(INSTANCE, permission, GUID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void appIdNull() {
        Permission permission = createPermission();
        permission.setAppId(null);
        assertValidatorMessage(INSTANCE, permission, APP_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void appIdBlank() {
        Permission permission = createPermission();
        permission.setAppId("");
        assertValidatorMessage(INSTANCE, permission, APP_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void userIdNull() {
        Permission permission = createPermission();
        permission.setUserId(null);
        assertValidatorMessage(INSTANCE, permission, USER_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void userIdBlank() {
        Permission permission = createPermission();
        permission.setUserId("");
        assertValidatorMessage(INSTANCE, permission, USER_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void accessLevelNull() {
        Permission permission = createPermission();
        permission.setAccessLevel(null);
        assertValidatorMessage(INSTANCE, permission, ACCESS_LEVEL_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void entityTypeNull() {
        Permission permission = createPermission();
        permission.setEntityType(null);
        assertValidatorMessage(INSTANCE, permission, ENTITY_TYPE_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void entityIdNull() {
        Permission permission = createPermission();
        permission.setEntityId(null);
        assertValidatorMessage(INSTANCE, permission, ENTITY_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void entityIdBlank() {
        Permission permission = createPermission();
        permission.setEntityId(null);
        assertValidatorMessage(INSTANCE, permission, ENTITY_ID_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    Permission createPermission() {
        Permission permission = new Permission();
        permission.setGuid(GUID);
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(AccessLevel.ADMIN);
        permission.setEntityType(EntityType.STUDY);
        permission.setEntityId(TEST_STUDY_ID);
        return permission;
    }
    
}
