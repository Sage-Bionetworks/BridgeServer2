package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PermissionDao {
    // TODO: add method descriptions
    Set<Permission> getPermissionsForUser(String appId, String userId);
    
    Set<Permission> getPermissionsForEntity(String appId, EntityType entityType, String entityId);
    
    Permission createPermission(String appId, Permission permission);
    
    Permission updatePermission(String appId, Permission permission);
    
    void deletePermission(String appId, String guid);
    
}
