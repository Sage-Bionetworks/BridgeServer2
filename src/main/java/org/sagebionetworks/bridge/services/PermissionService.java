package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class PermissionService {
    
    private PermissionDao permissionDao;
    
    @Autowired
    final void setPermissionDao(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }
    
    public Permission createPermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
        
        permission.setGuid(BridgeUtils.generateGuid());
        
        // TODO: validate permission
        
        return permissionDao.createPermission(appId, permission);
    }
    
    public Permission updatePermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
    
        // TODO: validate permission
        
        return permissionDao.updatePermission(appId, permission);
    }
    
    public Set<Permission> getPermissionsForUser(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);
        
        return null;
    }
    
    public Set<Permission> getPermissionsForObject(String appId, String permissionType, String objectId) {
        checkNotNull(appId);
        checkNotNull(permissionType);
        checkNotNull(objectId);
        
        return null;
    }
    
    public void deletePermission(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        permissionDao.deletePermission(appId, guid);
    }
}
