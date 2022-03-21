package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.permissions.EntityType.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.permissions.EntityRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.sagebionetworks.bridge.models.studies.Study;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PermissionServiceTest extends Mockito {
    
    private Set<EntityType> orgTypes = ImmutableSet.of(ASSESSMENT_LIBRARY, MEMBERS, ORGANIZATION, SPONSORED_STUDIES);
    private Set<EntityType> studyTypes = ImmutableSet.of(PARTICIPANTS, STUDY, STUDY_PI);
    private Set<EntityType> assessmentTypes = ImmutableSet.of(ASSESSMENT);
    
    @Mock
    PermissionDao mockDao;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    OrganizationService mockOrgService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    AssessmentService mockAssessmentService;
    
    @InjectMocks
    @Spy
    PermissionService service;
    
    @Captor
    ArgumentCaptor<Permission> permissionCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createPermission_pass() {
        when(service.generateGuid()).thenReturn(GUID);
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.createPermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
    
        PermissionDetail returnedPermission = service.createPermission(TEST_APP_ID, permission);
        
        verify(mockDao).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(returnedPermission, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), EntityType.STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
    }
    
    @Test
    public void createPermission_generatesGuid() {
        when(service.generateGuid()).thenReturn(GUID);
        Permission permission = createPermission();
        permission.setGuid("fake-guid");
    
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
    
        when(mockDao.createPermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
    
        service.createPermission(TEST_APP_ID, permission);
        
        verify(service).generateGuid();
        verify(mockDao).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
    
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createPermission_validatesIncomingPermission() {
        when(service.generateGuid()).thenReturn(GUID);
        Permission permission = createPermission();
        permission.setUserId(null);
        
        service.createPermission(TEST_APP_ID, permission);
    }
    
    @Test
    public void updatePermission_pass() {
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setAccessLevel(AccessLevel.LIST);
    
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        when(mockDao.getPermission(eq(TEST_APP_ID), eq(GUID))).thenReturn(existingPermission);
        
        PermissionDetail returnedDetail = service.updatePermission(TEST_APP_ID, permission);
    
        verify(mockDao).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
    
        assertEquals(returnedDetail, permissionDetail);
    
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), EntityType.STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updatePermission_validatesIncomingPermission() {
        Permission permission = createPermission();
        permission.setGuid(null);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
          expectedExceptionsMessageRegExp = "Permission with provided GUID does not exist.")
    public void updatePermission_noExistingPermission() {
        Permission permission = createPermission();
        when(mockDao.getPermission(any(), any())).thenReturn(null);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = "Can only update accessLevel.")
    public void updatePermission_incorrectAppIdPermission() {
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setAppId("fake-app-id");
        when(mockDao.getPermission(any(), any())).thenReturn(existingPermission);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = "Can only update accessLevel.")
    public void updatePermission_incorrectUserIdPermission() {
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setUserId("fake-user-id");
        when(mockDao.getPermission(any(), any())).thenReturn(existingPermission);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = "Can only update accessLevel.")
    public void updatePermission_incorrectEntityTypePermission() {
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setEntityType(ASSESSMENT_LIBRARY);
        when(mockDao.getPermission(any(), any())).thenReturn(existingPermission);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = "Can only update accessLevel.")
    public void updatePermission_incorrectEntityIdPermission() {
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setEntityId("fake-entity-id");
        when(mockDao.getPermission(any(), any())).thenReturn(existingPermission);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        Permission permission = createPermission();
    
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID)))
                .thenReturn(ImmutableList.of(permission));
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        List<PermissionDetail> permissionDetails = service.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID);
        
        verify(mockDao).getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID));
        assertEquals(permissionDetails.size(), 1);
        PermissionDetail returnedDetail = permissionDetails.stream().findFirst().orElse(null);
        assertEquals(returnedDetail, permissionDetail);
    }
    
    @Test
    public void getPermissionsForObject_pass() {
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.getPermissionsForEntity(eq(TEST_APP_ID), eq(EntityType.STUDY), eq(TEST_STUDY_ID)))
                .thenReturn(ImmutableList.of(permission));
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        List<PermissionDetail> permissionDetails = service.getPermissionsForEntity(TEST_APP_ID, "STUDY", TEST_STUDY_ID);
    
        verify(mockDao).getPermissionsForEntity(eq(TEST_APP_ID), eq(EntityType.STUDY), eq(TEST_STUDY_ID));
        assertEquals(permissionDetails.size(), 1);
        PermissionDetail returnedDetail = permissionDetails.stream().findFirst().orElse(null);
        assertEquals(returnedDetail, permissionDetail);
    }
    
    @Test
    public void deletePermission_pass() {
        doNothing().when(mockDao).deletePermission(eq(TEST_APP_ID), eq(GUID));
        service.deletePermission(TEST_APP_ID, GUID);
        verify(mockDao).deletePermission(eq(TEST_APP_ID), eq(GUID));
    }
    
    @Test
    public void getPermissionDetail_includesAccountRef() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
    
        Study study = Study.create();
        study.setName("test-study-name");
        when(mockStudyService.getStudy(any(), any(), eq(false))).thenReturn(study);
        
        Permission permission = createPermission();
        PermissionDetail permissionDetail = service.getPermissionDetail(TEST_APP_ID, permission);
        
        assertEquals(permissionDetail.getGuid(), GUID);
        assertEquals(permissionDetail.getUserId(), TEST_USER_ID);
        assertEquals(permissionDetail.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(permissionDetail.getEntityType(), EntityType.STUDY);
        assertEquals(permissionDetail.getEntityId(), TEST_STUDY_ID);
        assertNotNull(permissionDetail.getUserAccountRef());
        
        AccountRef accountRef = permissionDetail.getUserAccountRef();
        assertEquals(accountRef.getEmail(), EMAIL);
    }
    
    @Test
    public void getPermissionDetail_collectsEntityRefBasedOnAnyEntityType() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
    
        Organization org = Organization.create();
        org.setName("test-org-name");
        when(mockOrgService.getOrganization(eq(TEST_APP_ID), any())).thenReturn(org);
        
        Study study = Study.create();
        study.setName("test-study-name");
        when(mockStudyService.getStudy(eq(TEST_APP_ID), any(), eq(false))).thenReturn(study);
    
        Assessment assessment = new Assessment();
        assessment.setTitle("test-assessment-name");
        when(mockAssessmentService.getAssessmentByGuid(eq(TEST_APP_ID), any(), any())).thenReturn(assessment);
        
        for (EntityType entityType : EntityType.values()) {
            Permission permission = createPermission();
            permission.setEntityType(entityType);
            String entityId = entityType.name() + "-testId";
            permission.setEntityId(entityId);
            
            PermissionDetail permissionDetail = service.getPermissionDetail(TEST_APP_ID, permission);
            
            assertEquals(permissionDetail.getGuid(), GUID);
            assertEquals(permissionDetail.getUserId(), TEST_USER_ID);
            assertEquals(permissionDetail.getAccessLevel(), AccessLevel.ADMIN);
            assertEquals(permissionDetail.getEntityType(), entityType);
            assertEquals(permissionDetail.getEntityId(), entityId);
            assertNotNull(permissionDetail.getUserAccountRef());
            assertNotNull(permissionDetail.getEntityRef());
            
            AccountRef accountRef = permissionDetail.getUserAccountRef();
            assertEquals(accountRef.getEmail(), EMAIL);
            
            EntityRef entityRef = permissionDetail.getEntityRef();
            assertEquals(entityRef.getEntityType(), entityType);
            assertEquals(entityRef.getEntityId(), entityId);
            
            if (orgTypes.contains(entityType)) {
                assertEquals(entityRef.getEntityName(), "test-org-name");
            } else if (studyTypes.contains(entityType)) {
                assertEquals(entityRef.getEntityName(), "test-study-name");
            } else if (assessmentTypes.contains(entityType)) {
                assertEquals(entityRef.getEntityName(), "test-assessment-name");
            } else {
                fail("Unexpected entity type");
            }
        }
    }
    
    private Permission createPermission() {
        Permission permission = new Permission();
        permission.setGuid(GUID);
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(AccessLevel.ADMIN);
        permission.setEntityType(EntityType.STUDY);
        permission.setEntityId(TEST_STUDY_ID);
        return permission;
    }
}