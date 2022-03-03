package org.sagebionetworks.bridge.hibernate;

import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class HibernatePermissionDao implements PermissionDao {
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public Set<Permission> getPermissions(String appId, String userId, Map<EntityType, List<String>> entityIdFilters) {
        checkNotNull(appId);
        
        // TODO: build query based on incoming parameters
        
        return null;
    }
    
    @Override
    public Permission createPermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
    
        hibernateHelper.create(permission);
        
        return permission;
    }
    
    @Override
    public Permission updatePermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
    
        hibernateHelper.update(permission);
    
        return permission;
    }
    
    @Override
    public void deletePermission(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        hibernateHelper.deleteById(Permission.class, guid);
    }
}
