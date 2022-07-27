package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT_TYPES;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ORGANIZATION_TYPES;
import static org.sagebionetworks.bridge.models.permissions.EntityType.STUDY_TYPES;
import static org.sagebionetworks.bridge.validators.PermissionValidator.INSTANCE;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.sagebionetworks.bridge.models.permissions.EntityRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.sagebionetworks.bridge.models.permissions.RolesToPermissionsMapGenerator;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PermissionService {
    
    private PermissionDao permissionDao;
    
    private AccountService accountService;
    
    private OrganizationService organizationService;
    
    private StudyService studyService;
    
    private AssessmentService assessmentService;
    
    private SponsorService sponsorService;
    
    @Autowired
    final void setPermissionDao(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setAssessmentService(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }
    
    @Autowired
    final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }
    
    // For mock testing
    protected String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    protected DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    public PermissionDetail createPermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
        
        DateTime now = getModifiedOn();
        
        permission.setGuid(generateGuid());
        permission.setCreatedOn(now);
        permission.setModifiedOn(now);
        permission.setVersion(0L);
        
        Validate.entityThrowingException(INSTANCE, permission);
        
        Permission createdPermission = permissionDao.createPermission(appId, permission);
        
        return getPermissionDetail(appId, createdPermission);
    }
    
    public PermissionDetail updatePermission(String appId, Permission permission) {
        checkNotNull(appId);
        checkNotNull(permission);
        
        Validate.entityThrowingException(INSTANCE, permission);
        
        Permission existingPermission = permissionDao.getPermission(appId, permission.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Permission.class));
        
        // Can only update accessLevel
        permission.setAppId(existingPermission.getAppId());
        permission.setUserId(existingPermission.getUserId());
        permission.setEntityType(existingPermission.getEntityType());
        permission.setEntityId(existingPermission.getEntityId());
        permission.setCreatedOn(existingPermission.getCreatedOn());
        permission.setModifiedOn(getModifiedOn());
        
        Permission updatedPermission = permissionDao.updatePermission(appId, permission);
        
        return getPermissionDetail(appId, updatedPermission);
    }
    
    public List<PermissionDetail> getPermissionsForUser(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);
        
        AccountId accountId = AccountId.forId(appId, userId);
        accountService.getAccount(accountId).orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        List<Permission> permissions = permissionDao.getPermissionsForUser(appId, userId);
        
        List<PermissionDetail> permissionDetails = new ArrayList<>();
        for (Permission permission : permissions) {
            permissionDetails.add(getPermissionDetail(appId, permission));
        }
        
        return permissionDetails;
    }
    
    public List<PermissionDetail> getPermissionsForEntity(String appId, String entityType, String entityId) {
        checkNotNull(appId);
        checkNotNull(entityType);
        checkNotNull(entityId);
        
        // This will throw an EntityNotFound Exception for the entity if it does not exist.
        getEntityName(appId, EntityType.valueOf(entityType), entityId);
        
        List<Permission> permissions = permissionDao.getPermissionsForEntity(appId, EntityType.valueOf(entityType), entityId);
        
        List<PermissionDetail> permissionDetails = new ArrayList<>();
        for (Permission permission : permissions) {
            permissionDetails.add(getPermissionDetail(appId, permission));
        }
        
        return permissionDetails;
    }
    
    public void deletePermission(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        permissionDao.getPermission(appId, guid).orElseThrow(() -> new EntityNotFoundException(Permission.class));
        
        permissionDao.deletePermission(appId, guid);
    }
    
    public void updatePermissionsFromRoles(Account account, Account persistedAccount) {
        checkNotNull(account);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> newPermissions = getPermissionsForRoles(account);
        Map<EntityType, Map<String, Set<AccessLevel>>> oldPermissions = new HashMap<>();
        
        if (persistedAccount != null) {
            oldPermissions = getPermissionsForRoles(persistedAccount);
        }
        
        Map<EntityType, Map<String, Set<AccessLevel>>> additionalPermissions = new HashMap<>();
        Map<EntityType, Map<String, Set<AccessLevel>>> removablePermissions = new HashMap<>();
        
        for (Map.Entry<EntityType, Map<String, Set<AccessLevel>>> newPermissionEntry: newPermissions.entrySet()) {
            EntityType entityType = newPermissionEntry.getKey();
            if (oldPermissions.containsKey(entityType)) {
                // check which permissions are already accounted for, add any missing
                Map<String, Set<AccessLevel>> newPermissionEntityTypeMap = newPermissionEntry.getValue();
                for (Map.Entry<String, Set<AccessLevel>> entry : newPermissionEntityTypeMap.entrySet()) {
                    String entityId = entry.getKey();
                    Set<AccessLevel> oldAccessLevels = oldPermissions.get(entityType).get(entityId);
                    Set<AccessLevel> additionalAccessLevels = new HashSet<>();
                    for (AccessLevel accessLevel : entry.getValue()) {
                        if (!oldAccessLevels.contains(accessLevel)) {
                            additionalAccessLevels.add(accessLevel);
                        }
                    }
                    Map<String, Set<AccessLevel>> entityTypeMap = additionalPermissions.getOrDefault(entityType, new HashMap<>());
                    entityTypeMap.put(entityId, additionalAccessLevels);
                    additionalPermissions.put(entityType, entityTypeMap);
                }
            } else {
                // the old permissions do not have any of the new so add them all
                additionalPermissions.put(entityType, newPermissions.get(entityType));
            }
        }
        
        for (Map.Entry<EntityType, Map<String, Set<AccessLevel>>> oldPermissionEntry : oldPermissions.entrySet()) {
            EntityType entityType = oldPermissionEntry.getKey();
            if (newPermissions.containsKey(entityType)) {
                Map<String, Set<AccessLevel>> oldPermissionEntityTypeMap = oldPermissionEntry.getValue();
                for (Map.Entry<String, Set<AccessLevel>> entityTypeEntry : oldPermissionEntityTypeMap.entrySet()) {
                    String entityId = entityTypeEntry.getKey();
                    Set<AccessLevel> newAccessLevels = newPermissions.get(entityType).get(entityId);
                    Set<AccessLevel> removableAccessLevels = new HashSet<>();
                    for (AccessLevel accessLevel : entityTypeEntry.getValue()) {
                        if (!newAccessLevels.contains(accessLevel)) {
                            removableAccessLevels.add(accessLevel);
                        }
                    }
                    Map<String, Set<AccessLevel>> entityTypeMap = removablePermissions.getOrDefault(entityType, new HashMap<>());
                    entityTypeMap.put(entityId, removableAccessLevels);
                    removablePermissions.put(entityType, entityTypeMap);
                }
            } else {
                removablePermissions.put(entityType, oldPermissions.get(entityType));
            }
        }
        
        List<Permission> currentPermissions = permissionDao.getPermissionsForUser(account.getAppId(), account.getId());
        for (Permission permission : currentPermissions) {
            if (removablePermissions.containsKey(permission.getEntityType())) {
                Map<String, Set<AccessLevel>> removableEntityMap = removablePermissions.get(permission.getEntityType());
                if (removableEntityMap.containsKey(permission.getEntityId())) {
                    Set<AccessLevel> accessLevelSet = removableEntityMap.get(permission.getEntityId());
                    if (accessLevelSet.contains(permission.getAccessLevel())) {
                        try {
                            deletePermission(account.getAppId(), permission.getGuid());
                        } catch (EntityNotFoundException e) {
                            // While this shouldn't happen, if the permission does not exist then it's ok to skip.
                        }
                    }
                }
            }
        }
    
        for (Map.Entry<EntityType, Map<String, Set<AccessLevel>>> additionalPermissionsEntry : additionalPermissions.entrySet()) {
            EntityType entityType = additionalPermissionsEntry.getKey();
            Map<String, Set<AccessLevel>> additionalEntityTypeMap = additionalPermissionsEntry.getValue();
            for (Map.Entry<String, Set<AccessLevel>> entityTypeEntry: additionalEntityTypeMap.entrySet()) {
                String entityId = entityTypeEntry.getKey();
                for (AccessLevel accessLevel : entityTypeEntry.getValue()) {
                    Permission newPermission = new Permission();
                    newPermission.setAppId(account.getAppId());
                    newPermission.setUserId(account.getId());
                    newPermission.setEntityType(entityType);
                    newPermission.setEntityId(entityId);
                    newPermission.setAccessLevel(accessLevel);
                    
                    try {
                        createPermission(account.getAppId(), newPermission);
                    } catch (ConstraintViolationException e) {
                        // It's possible that a duplicate permission already exists.
                    }
                }
            }
        }
    }
    
    private Map<EntityType, Map<String, Set<AccessLevel>>> getPermissionsForRoles(Account account) {
        String orgId = account.getOrgMembership();
        if (orgId == null) {
            return new HashMap<>();
        }
        
        Set<String> studyIds = sponsorService.getSponsoredStudyIds(account.getAppId(), orgId);
        
        return RolesToPermissionsMapGenerator.INSTANCE.generate(account.getRoles(), orgId, studyIds);
    }
    
    protected PermissionDetail getPermissionDetail(String appId, Permission permission) {
        AccountId accountId = AccountId.forId(appId, permission.getUserId());
        Account userAccount = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        AccountRef userAccountRef = new AccountRef(userAccount);
        
        String entityName = getEntityName(appId, permission.getEntityType(), permission.getEntityId());
        
        EntityRef entityRef = new EntityRef(permission.getEntityType(), permission.getEntityId(), entityName);
        
        return new PermissionDetail(permission, entityRef, userAccountRef);
    }
    
    private String getEntityName(String appId, EntityType entityType, String entityId) {
        String entityName = "";
        if (ORGANIZATION_TYPES.contains(entityType)) {
            Organization organization = organizationService.getOrganization(appId, entityId);
            entityName = organization.getName();
        } else if (STUDY_TYPES.contains(entityType)) {
            Study study = studyService.getStudy(appId, entityId, true);
            entityName = study.getName();
        } else if (ASSESSMENT_TYPES.contains(entityType)) {
            Assessment assessment = assessmentService.getAssessmentByGuid(appId, null, entityId);
            entityName = assessment.getTitle();
        }
        return entityName;
    }
}