package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class HibernatePermissionDao implements PermissionDao {
    
    static final String GUID = "guid";
    static final String APP_ID = "appId";
    static final String USER_ID = "userId";
    static final String ACCESS_LEVEL = "accessLevel";
    static final String ENTITY_TYPE = "entityType";
    static final String ENTITY_ID = "entityId";
    static final String CREATED_ON = "createdOn";
    static final String MODIFIED_ON = "modifiedOn";
    static final String VERSION = "version";
    static final String ASSESSMENT_ID = "assessmentId";
    static final String ORGANIZATION_ID = "organizationId";
    static final String STUDY_ID = "studyId";
    
    static final String GET_BY_USER = "FROM Permission WHERE appId=:appId AND userId=:userId";
    static final String GET_BY_ENTITY_TYPE = "FROM Permission WHERE appId=:appId "+
            "AND entityType=:entityType AND entityId=:entityId";
    static final String INSERT = "INSERT INTO Permissions (guid, appId, userId, accessLevel, entityType, "+
            "entityId, createdOn, modifiedOn, version, assessmentId, organizationId, studyId) VALUES "+
            "(:guid, :appId, :userId, :accessLevel, :entityType, :entityId, :createdOn, :modifiedOn, "+
            ":version, :assessmentId, :organizationId, :studyId)";
    
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
        
        // TODO: add logic to translate the entity id to the correct location
    
//        Map<String, Object> params = new HashMap<>();
//        params.put(GUID, permission.getGuid());
//        params.put(APP_ID, appId);
//        params.put(USER_ID, permission.getUserId());
//        params.put(ACCESS_LEVEL, permission.getAccessLevel());
//        params.put(ENTITY_TYPE, permission.getEntityType());
//        params.put(ENTITY_ID, permission.getEntityId());
//        params.put(CREATED_ON, permission.getCreatedOn());
//        params.put(MODIFIED_ON, permission.getModifiedOn());
//        params.put(VERSION, Long.toString(permission.getVersion()));
//        
//        if (permission.getEntityType() == EntityType.ASSESSMENT) {
//            params.put(ASSESSMENT_ID, permission.getEntityId());
//        } else {
//            params.put(ASSESSMENT_ID, null);
//        }
//        if (permission.getEntityType() == EntityType.ORGANIZATION) {
//            params.put(ORGANIZATION_ID, permission.getEntityId());
//        } else {
//            params.put(ORGANIZATION_ID, null);
//        }
//        if (permission.getEntityType() == EntityType.STUDY) {
//            params.put(STUDY_ID, permission.getEntityId());
//        } else {
//            params.put(STUDY_ID, null);
//        }
//        
//        hibernateHelper.query(INSERT, params);
    // -----------------------------------------------------------------
//        QueryBuilder builder = new QueryBuilder();
//        builder.append(INSERT);
//        builder.getParameters().put(GUID, permission.getGuid());
//        builder.getParameters().put(APP_ID, appId);
//        builder.getParameters().put(USER_ID, permission.getUserId());
//        builder.getParameters().put(ACCESS_LEVEL, permission.getAccessLevel());
//        builder.getParameters().put(ENTITY_TYPE, permission.getEntityType());
//        builder.getParameters().put(ENTITY_ID, permission.getEntityId());
//        builder.getParameters().put(CREATED_ON, permission.getCreatedOn());
//        builder.getParameters().put(MODIFIED_ON, permission.getModifiedOn());
//        builder.getParameters().put(VERSION, permission.getVersion());
//
//        if (permission.getEntityType() == EntityType.ASSESSMENT) {
//            builder.getParameters().put(ASSESSMENT_ID, permission.getEntityId());
//        } else {
//            builder.getParameters().put(ASSESSMENT_ID, null);
//        }
//        if (permission.getEntityType() == EntityType.ORGANIZATION) {
//            builder.getParameters().put(ORGANIZATION_ID, permission.getEntityId());
//        } else {
//            builder.getParameters().put(ORGANIZATION_ID, null);
//        }
//        if (permission.getEntityType() == EntityType.STUDY) {
//            builder.getParameters().put(STUDY_ID, permission.getEntityId());
//        } else {
//            builder.getParameters().put(STUDY_ID, null);
//        }
//        
//        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
        // --------------------------------------------------------------------
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
