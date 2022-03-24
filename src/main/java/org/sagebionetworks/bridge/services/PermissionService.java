package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT_LIBRARY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.MEMBERS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ORGANIZATION;
import static org.sagebionetworks.bridge.models.permissions.EntityType.PARTICIPANTS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.SPONSORED_STUDIES;
import static org.sagebionetworks.bridge.models.permissions.EntityType.STUDY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.STUDY_PI;
import static org.sagebionetworks.bridge.validators.PermissionValidator.INSTANCE;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.permissions.EntityRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PermissionService {
    
    private final Set<EntityType> orgTypes = ImmutableSet.of(ASSESSMENT_LIBRARY, MEMBERS, ORGANIZATION, SPONSORED_STUDIES);
    private final Set<EntityType> studyTypes = ImmutableSet.of(PARTICIPANTS, STUDY, STUDY_PI);
    private final Set<EntityType> assessmentTypes = ImmutableSet.of(ASSESSMENT);
    
    private PermissionDao permissionDao;
    
    private AccountService accountService;
    
    private OrganizationService organizationService;
    
    private StudyService studyService;
    
    private AssessmentService assessmentService;
    
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
        if (orgTypes.contains(entityType)) {
            Organization organization = organizationService.getOrganization(appId, entityId);
            entityName = organization.getName();
        } else if (studyTypes.contains(entityType)) {
            Study study = studyService.getStudy(appId, entityId, true);
            entityName = study.getName();
        } else if (assessmentTypes.contains(entityType)) {
            Assessment assessment = assessmentService.getAssessmentByGuid(appId, null, entityId);
            entityName = assessment.getTitle();
        }
        return entityName;
    }
}
