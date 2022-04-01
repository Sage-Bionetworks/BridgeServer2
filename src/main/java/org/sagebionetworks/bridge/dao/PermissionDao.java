package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * DAO to manage administrative permissions by entity.
 */
public interface PermissionDao {
    
    /**
     * Retrieve a permission by its guid.
     * @param appId
     * @param guid the id of the permission to retrive
     * @return a permission
     */
    Optional<Permission> getPermission(String appId, String guid);
    
    /**
     * Retrieve all permissions granted to a user.
     * @param appId
     * @param userId the id of the administrative user
     * @return list of permissions
     */
    List<Permission> getPermissionsForUser(String appId, String userId);
    
    /**
     * Retrieve all permissions granting access to a specific entity.
     * @param appId
     * @param entityType the type of entity
     * @param entityId   the id of the entity
     * @return list of permissions
     */
    List<Permission> getPermissionsForEntity(String appId, EntityType entityType, String entityId);
    
    /**
     * Create a new permission record.
     * @param appId
     * @param permission the permission to create, cannot have any null fields
     * @return the new permission
     */
    Permission createPermission(String appId, Permission permission);
    
    /**
     * Update an existing permission record.
     * @param appId
     * @param permission the permission to update, cannot have any null fields
     * @return the updated permission
     */
    Permission updatePermission(String appId, Permission permission);
    
    /**
     * Delete an existing permission record.
     * @param appId
     * @param guid the id of the permission
     */
    void deletePermission(String appId, String guid);
    
}
