package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.permissions.EntityType.*;
import static org.sagebionetworks.bridge.validators.PermissionValidator.INSTANCE;

import com.google.common.collect.ImmutableSet;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
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

import javax.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PermissionService {
    
    private Set<EntityType> orgTypes = ImmutableSet.of(ASSESSMENT_LIBRARY, MEMBERS, ORGANIZATION, SPONSORED_STUDIES);
    private Set<EntityType> studyTypes = ImmutableSet.of(PARTICIPANTS, STUDY, STUDY_PI);
    private Set<EntityType> assessmentTypes = ImmutableSet.of(ASSESSMENT);
    
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
    
    public PermissionDetail createPermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
        
        permission.setGuid(generateGuid());
        
        Validate.entityThrowingException(INSTANCE, permission);
        
        Permission createdPermission = permissionDao.createPermission(appId, permission);
        
        return getPermissionDetail(appId, createdPermission);
    }
    
    public PermissionDetail updatePermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
    
        Validate.entityThrowingException(INSTANCE, permission);
        
        Permission existingPermission = permissionDao.getPermission(appId, permission.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Permission.class));
        
        // Can only update accessLevel
        permission.setAppId(existingPermission.getAppId());
        permission.setUserId(existingPermission.getUserId());
        permission.setEntityType(existingPermission.getEntityType());
        permission.setEntityId(existingPermission.getEntityId());
        
        Permission updatedPermission = permissionDao.updatePermission(appId, permission);
    
        return getPermissionDetail(appId, updatedPermission);
    }
    
    public List<PermissionDetail> getPermissionsForUser(String appId, String userId) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
    
        List<Permission> permissions = permissionDao.getPermissionsForUser(appId, userId);
    
        List<PermissionDetail> permissionDetails = new ArrayList<>();
        for (Permission permission : permissions) {
            permissionDetails.add(getPermissionDetail(appId, permission));
        }
    
        return permissionDetails;
    }
    
    public List<PermissionDetail> getPermissionsForEntity(String appId, String entityType, String entityId) {
        checkArgument(isNotBlank(appId));
        checkNotNull(entityType);
        checkArgument(isNotBlank(entityId));
        
        List<Permission> permissions = permissionDao.getPermissionsForEntity(appId, EntityType.valueOf(entityType), entityId);
        
        List<PermissionDetail> permissionDetails = new ArrayList<>();
        for (Permission permission : permissions) {
            permissionDetails.add(getPermissionDetail(appId, permission));
        }
        
        return permissionDetails;
    }
    
    public void deletePermission(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        permissionDao.deletePermission(appId, guid);
    }
    
    protected PermissionDetail getPermissionDetail(String appId, Permission permission) {
        AccountId accountId = AccountId.forId(appId, permission.getUserId());
        Account userAccount = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        AccountRef userAccountRef = new AccountRef(userAccount);
        
        String entityName = "";
        if (orgTypes.contains(permission.getEntityType())) {
            Organization organization = organizationService.getOrganization(appId, permission.getEntityId());
            entityName = organization.getName();
        } else if (studyTypes.contains(permission.getEntityType())) {
            Study study = studyService.getStudy(appId, permission.getEntityId(), false);
            entityName = study.getName();
        } else if (assessmentTypes.contains(permission.getEntityType())) {
            Assessment assessment = assessmentService.getAssessmentByGuid(appId, null, permission.getEntityId());
            entityName = assessment.getTitle();
        }
    
        EntityRef entityRef = new EntityRef(permission.getEntityType(), permission.getEntityId(), entityName);
        
        return new PermissionDetail(permission, entityRef, userAccountRef);
    }
}
