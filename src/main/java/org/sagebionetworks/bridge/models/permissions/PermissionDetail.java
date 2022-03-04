package org.sagebionetworks.bridge.models.permissions;

import org.sagebionetworks.bridge.models.accounts.AccountRef;

public class PermissionDetail {
    
    private final String guid;
    private final String userId;
    private final PermissionAccessLevel accessLevel;
    private final EntityType entityType;
    private final String entityId;
    private final AccountRef userAccountRef;
    
    public PermissionDetail(Permission permission, AccountRef userAccountRef) {
        this.guid = permission.getGuid();
        this.userId = permission.getUserId();
        this.accessLevel = permission.getAccessLevel();
        this.entityType = permission.getEntityType();
        this.entityId = permission.getEntityId();
        this.userAccountRef = userAccountRef;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public PermissionAccessLevel getAccessLevel() {
        return accessLevel;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public AccountRef getUserAccountRef() {
        return userAccountRef;
    }
}
