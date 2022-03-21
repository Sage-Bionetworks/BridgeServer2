package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

import java.util.Objects;

@JsonPropertyOrder({ "guid", "userId", "accessLevel", "entityType", "entityId", "entityRef", "userAccountRef" })
public final class PermissionDetail {
    
    private final String guid;
    private final String userId;
    private final AccessLevel accessLevel;
    private final EntityType entityType;
    private final String entityId;
    private final EntityRef entityRef;
    private final AccountRef userAccountRef;
    
    public PermissionDetail(Permission permission, EntityRef entityRef, AccountRef userAccountRef) {
        this.guid = permission.getGuid();
        this.userId = permission.getUserId();
        this.accessLevel = permission.getAccessLevel();
        this.entityType = permission.getEntityType();
        this.entityId = permission.getEntityId();
        this.entityRef = entityRef;
        this.userAccountRef = userAccountRef;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public AccessLevel getAccessLevel() {
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
    
    public EntityRef getEntityRef() {
        return entityRef;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionDetail that = (PermissionDetail) o;
        return Objects.equals(guid, that.guid) && 
               Objects.equals(userId, that.userId) && 
                accessLevel == that.accessLevel && 
                entityType == that.entityType && 
                Objects.equals(entityId, that.entityId) && 
                Objects.equals(entityRef, that.entityRef) &&
                Objects.equals(userAccountRef, that.userAccountRef);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(guid, userId, accessLevel, entityType, entityId, entityRef, userAccountRef);
    }
    
}
