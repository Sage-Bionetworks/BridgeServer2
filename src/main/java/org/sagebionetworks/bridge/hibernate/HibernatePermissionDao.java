package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;
import java.util.Optional;

@Component
public class HibernatePermissionDao implements PermissionDao {
    
    static final String APP_ID = "appId";
    static final String USER_ID = "userId";
    static final String ENTITY_TYPE = "entityType";
    static final String ENTITY_ID = "entityId";
    
    static final String GET_BY_USER = "FROM Permission WHERE appId=:appId AND userId=:userId";
    static final String GET_BY_ENTITY_TYPE = "FROM Permission WHERE appId=:appId "+
            "AND entityType=:entityType AND entityId=:entityId";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public Optional<Permission> getPermission(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        return Optional.ofNullable(hibernateHelper.getById(Permission.class, guid));
    }
    
    @Override
    public List<Permission> getPermissionsForUser(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);
        
        return hibernateHelper.queryGet(GET_BY_USER, ImmutableMap.of(APP_ID, appId, USER_ID, userId),
                null, null, Permission.class);
    }
    
    @Override
    public List<Permission> getPermissionsForEntity(String appId, EntityType entityType, String entityId) {
        checkNotNull(appId);
        checkNotNull(entityType);
        checkNotNull(entityId);
        
        return hibernateHelper.queryGet(GET_BY_ENTITY_TYPE,
                ImmutableMap.of(APP_ID, appId, ENTITY_TYPE, entityType, ENTITY_ID, entityId),
                null, null, Permission.class);
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
        // TODO: this should only update access level, so entity id should be safe
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
