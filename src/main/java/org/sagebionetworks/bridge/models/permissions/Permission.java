package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.BridgeEntity;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "Permissions")
public final class Permission implements BridgeEntity {
    
    @Id
    private String guid;
    @JsonIgnore
    private String appId;
    private String userId;
    @Enumerated(EnumType.STRING)
    private AccessLevel accessLevel;
    @Enumerated(EnumType.STRING)
    private EntityType entityType;
    private String entityId;
    
    public String getGuid() {
        return guid;
    }
    
    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public AccessLevel getAccessLevel() {
        return accessLevel;
    }
    
    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(guid, that.guid) && Objects.equals(appId, that.appId) && Objects.equals(userId, that.userId) && accessLevel == that.accessLevel && entityType == that.entityType && Objects.equals(entityId, that.entityId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(guid, appId, userId, accessLevel, entityType, entityId);
    }
    
}
