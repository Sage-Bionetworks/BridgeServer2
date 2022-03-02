package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.BridgeEntity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "Permissions")
public class Permission implements BridgeEntity {
    
    @Id
    private String guid;
    @JsonIgnore
    private String appId;
    private String userId;
    private String role; // TODO: rename?
    private PermissionType permissionType;
    private String objectId;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(guid, that.guid) && Objects.equals(appId, that.appId) && Objects.equals(userId, that.userId) && Objects.equals(role, that.role) && permissionType == that.permissionType && Objects.equals(objectId, that.objectId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(guid, appId, userId, role, permissionType, objectId);
    }
    
    @Override
    public String toString() {
        return "Permission{" +
                "guid='" + guid + '\'' +
                ", appId='" + appId + '\'' +
                ", userId='" + userId + '\'' +
                ", role='" + role + '\'' +
                ", permissionType=" + permissionType +
                ", objectId='" + objectId + '\'' +
                '}';
    }
    
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
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public PermissionType getPermissionType() {
        return permissionType;
    }
    
    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
    }
    
    public String getObjectId() {
        return objectId;
    }
    
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
}
