package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;

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
    public Permission getPermission(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        return hibernateHelper.getById(Permission.class, guid);
    }
    
    @Override
    public List<Permission> getPermissionsForUser(String appId, String userId) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_BY_USER, APP_ID, appId, USER_ID, userId);
        
        return hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), null, null, Permission.class);
    }
    
    @Override
    public List<Permission> getPermissionsForEntity(String appId, EntityType entityType, String entityId) {
        checkArgument(isNotBlank(appId));
        checkNotNull(entityType);
        checkArgument(isNotBlank(entityId));
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_BY_ENTITY_TYPE, APP_ID, appId, ENTITY_TYPE, entityType, ENTITY_ID, entityId);
    
        return hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), null, null, Permission.class);
    }
    
    @Override
    public Permission createPermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
    
        hibernateHelper.create(permission);
        
        return permission;
    }
    
    @Override
    public Permission updatePermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
    
        hibernateHelper.update(permission);
    
        return permission;
    }
    
    @Override
    public void deletePermission(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        hibernateHelper.deleteById(Permission.class, guid);
    }
}
