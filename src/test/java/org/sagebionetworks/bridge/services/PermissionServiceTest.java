package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT_LIBRARY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.MEMBERS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ORGANIZATION;
import static org.sagebionetworks.bridge.models.permissions.EntityType.PARTICIPANTS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.SPONSORED_STUDIES;
import static org.sagebionetworks.bridge.models.permissions.EntityType.STUDY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.STUDY_PI;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
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
    
    @Mock
    SponsorService mockSponsorService;
    
    @InjectMocks
    @Spy
    PermissionService service;
    
    @Captor
    ArgumentCaptor<Permission> permissionCaptor;
    
    @Captor
    ArgumentCaptor<String> guidCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createPermission_pass() {
        when(service.generateGuid()).thenReturn(GUID);
        when(service.getModifiedOn()).thenReturn(CREATED_ON);
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.createPermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        PermissionDetail returnedPermission = service.createPermission(TEST_APP_ID, permission);
        
        verify(service).getModifiedOn();
        verify(mockDao).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(returnedPermission, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), CREATED_ON);
        assertEquals(captured.getVersion(), 0L);
    }
    
    @Test
    public void createPermission_generatesGuid() {
        when(service.generateGuid()).thenReturn(GUID);
        Permission permission = createPermission();
        permission.setGuid("fake-guid");
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
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
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        Permission permission = createPermission();
        Permission existingPermission = createPermission();
        existingPermission.setAccessLevel(AccessLevel.LIST);
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        when(mockDao.getPermission(eq(TEST_APP_ID), eq(GUID))).thenReturn(Optional.of(existingPermission));
        
        PermissionDetail returnedDetail = service.updatePermission(TEST_APP_ID, permission);
        
        verify(service).getModifiedOn();
        verify(mockDao).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(returnedDetail, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertEquals(captured.getVersion(), 1L);
    }
    
    @Test
    public void updatePermission_onlyUpdatesAccessLevel() {
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        Permission permission = createPermission();
        permission.setAppId("fake-app-id");
        permission.setUserId("fake-user-id");
        permission.setEntityType(ORGANIZATION);
        permission.setEntityId("fake-entity-id");
        permission.setCreatedOn(DateTime.now());
        permission.setModifiedOn(DateTime.now());
        permission.setVersion(5L);
        
        Permission existingPermission = createPermission();
        existingPermission.setAccessLevel(AccessLevel.LIST);
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockDao.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        when(mockDao.getPermission(eq(TEST_APP_ID), eq(GUID))).thenReturn(Optional.of(existingPermission));
        
        PermissionDetail returnedDetail = service.updatePermission(TEST_APP_ID, permission);
        
        verify(mockDao).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(returnedDetail, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertEquals(captured.getVersion(), 5L);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updatePermission_validatesIncomingPermission() {
        Permission permission = createPermission();
        permission.setGuid(null);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updatePermission_noExistingPermission() {
        Permission permission = createPermission();
        when(mockDao.getPermission(any(), any())).thenReturn(Optional.empty());
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID)))
                .thenReturn(ImmutableList.of(permission));
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        List<PermissionDetail> permissionDetails = service.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID);
        
        verify(mockDao).getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID));
        assertEquals(permissionDetails.size(), 1);
        PermissionDetail returnedDetail = permissionDetails.stream().findFirst().orElse(null);
        assertEquals(returnedDetail, permissionDetail);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getPermissionsForUser_nonExistentUser() {
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());
        
        service.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID);
    }
    
    @Test
    public void getPermissionsForEntity_pass() {
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        EntityRef entityRef = new EntityRef(STUDY, TEST_STUDY_ID, "test-study-name");
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, new AccountRef(account));
        
        Study study = Study.create();
        when(mockStudyService.getStudy(eq(TEST_APP_ID), eq(TEST_STUDY_ID), eq(true))).thenReturn(study);
        
        when(mockDao.getPermissionsForEntity(eq(TEST_APP_ID), eq(STUDY), eq(TEST_STUDY_ID)))
                .thenReturn(ImmutableList.of(permission));
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        List<PermissionDetail> permissionDetails = service.getPermissionsForEntity(TEST_APP_ID, "STUDY", TEST_STUDY_ID);
        
        verify(mockDao).getPermissionsForEntity(eq(TEST_APP_ID), eq(STUDY), eq(TEST_STUDY_ID));
        assertEquals(permissionDetails.size(), 1);
        PermissionDetail returnedDetail = permissionDetails.stream().findFirst().orElse(null);
        assertEquals(returnedDetail, permissionDetail);
    }
    
    @Test
    public void getPermissionsForEntity_nonExistentEntity() {
        when(mockOrgService.getOrganization(eq(TEST_APP_ID), any())).thenThrow(EntityNotFoundException.class);
        when(mockStudyService.getStudy(eq(TEST_APP_ID), any(), eq(true)))
                .thenThrow(EntityNotFoundException.class);
        when(mockAssessmentService.getAssessmentByGuid(eq(TEST_APP_ID), any(), any()))
                .thenThrow(EntityNotFoundException.class);
        
        for (EntityType entityType : EntityType.values()) {
            String entityId = entityType.name() + "-testId";
            
            try {
                service.getPermissionsForEntity(TEST_APP_ID, entityType.toString(), entityId);
                fail("Should have failed to find the entity of type " + entityType.toString());
            } catch (EntityNotFoundException e) {
                if (orgTypes.contains(entityType)) {
                    verify(mockOrgService).getOrganization(eq(TEST_APP_ID), eq(entityId));
                } else if (studyTypes.contains(entityType)) {
                    verify(mockStudyService).getStudy(eq(TEST_APP_ID), eq(entityId), eq(true));
                } else if (assessmentTypes.contains(entityType)) {
                    verify(mockAssessmentService).getAssessmentByGuid(eq(TEST_APP_ID), eq(null), eq(entityId));
                } else {
                    fail("Unexpected entity type");
                }
            }
        }
    }
    
    @Test
    public void deletePermission_pass() {
        when(mockDao.getPermission(eq(TEST_APP_ID), eq(GUID))).thenReturn(Optional.of(createPermission()));
        doNothing().when(mockDao).deletePermission(eq(TEST_APP_ID), eq(GUID));
        
        service.deletePermission(TEST_APP_ID, GUID);
        verify(mockDao).deletePermission(eq(TEST_APP_ID), eq(GUID));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deletePermission_noExistingPermission() {
        when(mockDao.getPermission(any(), any())).thenReturn(Optional.empty());
        
        service.deletePermission(TEST_APP_ID, GUID);
    }
    
    @Test
    public void getPermissionDetail_includesAccountRef() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        
        Study study = Study.create();
        when(mockStudyService.getStudy(any(), any(), eq(true))).thenReturn(study);
        
        Permission permission = createPermission();
        PermissionDetail permissionDetail = service.getPermissionDetail(TEST_APP_ID, permission);
        
        assertEquals(permissionDetail.getGuid(), GUID);
        assertEquals(permissionDetail.getAccessLevel(), AccessLevel.ADMIN);
        assertNotNull(permissionDetail.getAccount());
        
        AccountRef accountRef = permissionDetail.getAccount();
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
        when(mockStudyService.getStudy(eq(TEST_APP_ID), any(), eq(true))).thenReturn(study);
        
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
            assertEquals(permissionDetail.getAccessLevel(), AccessLevel.ADMIN);
            assertNotNull(permissionDetail.getAccount());
            assertNotNull(permissionDetail.getEntity());
            
            AccountRef accountRef = permissionDetail.getAccount();
            assertEquals(accountRef.getEmail(), EMAIL);
            
            EntityRef entityRef = permissionDetail.getEntity();
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
    
    @Test
    public void updatePermissionsFromRoles_createsPermissions() {
        Account persistedAccount = createAccount();
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        List<Permission> expectedPermissionsList = ImmutableList.of(
                createPermission(AccessLevel.DELETE, SPONSORED_STUDIES, TEST_ORG_ID),
                createPermission(AccessLevel.DELETE, ASSESSMENT_LIBRARY, TEST_ORG_ID),
                createPermission(AccessLevel.EDIT, ASSESSMENT_LIBRARY, TEST_ORG_ID));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        doReturn(null).when(service).createPermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, persistedAccount);
        
        verify(service, times(3)).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        List<Permission> capturedPermissions = permissionCaptor.getAllValues();
        assertEquals(capturedPermissions.size(), 3);
        assertTrue(capturedPermissions.containsAll(expectedPermissionsList));
    }
    
    @Test
    public void updatePermissionsFromRoles_handlesExistingDuplicatePermissionsOnCreate() {
        Account persistedAccount = createAccount();
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        doThrow(ConstraintViolationException.class).when(service).createPermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, persistedAccount);
        
        verify(service, times(3)).createPermission(eq(TEST_APP_ID), any());
    }
    
    @Test
    public void updatePermissionsFromRoles_createsAllIfNewAccount() {
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(RESEARCHER));
        
        List<Permission> expectedPermissionsList = ImmutableList.of(
                createPermission(AccessLevel.LIST, ORGANIZATION, TEST_ORG_ID),
                createPermission(AccessLevel.READ, ORGANIZATION, TEST_ORG_ID),
                createPermission(AccessLevel.LIST, PARTICIPANTS, TEST_STUDY_ID),
                createPermission(AccessLevel.READ, PARTICIPANTS, TEST_STUDY_ID),
                createPermission(AccessLevel.EDIT, PARTICIPANTS, TEST_STUDY_ID),
                createPermission(AccessLevel.DELETE, PARTICIPANTS, TEST_STUDY_ID),
                createPermission(AccessLevel.LIST, SPONSORED_STUDIES, TEST_ORG_ID),
                createPermission(AccessLevel.READ, SPONSORED_STUDIES, TEST_ORG_ID),
                createPermission(AccessLevel.EDIT, SPONSORED_STUDIES, TEST_ORG_ID),
                createPermission(AccessLevel.LIST, MEMBERS, TEST_ORG_ID),
                createPermission(AccessLevel.READ, MEMBERS, TEST_ORG_ID),
                createPermission(AccessLevel.LIST, ASSESSMENT_LIBRARY, TEST_ORG_ID),
                createPermission(AccessLevel.READ, ASSESSMENT_LIBRARY, TEST_ORG_ID));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        doReturn(null).when(service).createPermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, null);
        
        verify(service, times(13)).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        List<Permission> capturedPermissions = permissionCaptor.getAllValues();
        assertEquals(capturedPermissions.size(), 13);
        assertTrue(capturedPermissions.containsAll(expectedPermissionsList));
    }
    
    @Test
    public void updatePermissionsFromRoles_deletesPermissions() {
        Account persistedAccount = createAccount();
        persistedAccount.setRoles(ImmutableSet.of(ADMIN));
        
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(ORG_ADMIN));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        List<Permission> existingPermissionsList = ImmutableList.of(
                createPermission(AccessLevel.LIST, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.EDIT, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.DELETE, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.ADMIN, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-1"),
                createPermission(AccessLevel.READ, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-2"),
                createPermission(AccessLevel.EDIT, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-3"),
                createPermission(AccessLevel.DELETE, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-4"),
                createPermission(AccessLevel.ADMIN, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-5"),
                createPermission(AccessLevel.LIST, SPONSORED_STUDIES, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, SPONSORED_STUDIES, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.EDIT, SPONSORED_STUDIES, TEST_ORG_ID, "delete-guid-6"),
                createPermission(AccessLevel.DELETE, SPONSORED_STUDIES, TEST_ORG_ID, "delete-guid-7"),
                createPermission(AccessLevel.ADMIN, SPONSORED_STUDIES, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.EDIT, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.DELETE, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.ADMIN, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, ASSESSMENT_LIBRARY, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, ASSESSMENT_LIBRARY, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.EDIT, ASSESSMENT_LIBRARY, TEST_ORG_ID, "delete-guid-8"),
                createPermission(AccessLevel.DELETE, ASSESSMENT_LIBRARY, TEST_ORG_ID, "delete-guid-9"),
                createPermission(AccessLevel.ADMIN, ASSESSMENT_LIBRARY, TEST_ORG_ID, GUID));
        
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID))).thenReturn(existingPermissionsList);
        
        doReturn(null).when(service).createPermission(eq(TEST_APP_ID), any());
        doNothing().when(service).deletePermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, persistedAccount);
        
        verify(service, times(9)).deletePermission(eq(TEST_APP_ID), guidCaptor.capture());
        
        List<String> capturedGuids = guidCaptor.getAllValues();
        assertEquals(capturedGuids.size(), 9);
        assertTrue(capturedGuids.containsAll(ImmutableSet.of("delete-guid-1", "delete-guid-2",
                "delete-guid-3", "delete-guid-4", "delete-guid-5", "delete-guid-6", "delete-guid-7",
                "delete-guid-8", "delete-guid-9")));
    }
    
    @Test
    public void updatePermissionsFromRoles_doesNotDeleteNonExistentPermissions() {
        Account persistedAccount = createAccount();
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        List<Permission> existingPermissionsList = ImmutableList.of(
                createPermission(AccessLevel.READ, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-1"),
                createPermission(AccessLevel.DELETE, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-4"),
                createPermission(AccessLevel.LIST, SPONSORED_STUDIES, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, ASSESSMENT_LIBRARY, TEST_ORG_ID, GUID));
        
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID))).thenReturn(existingPermissionsList);
        
        doReturn(null).when(service).createPermission(eq(TEST_APP_ID), any());
        doNothing().when(service).deletePermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, persistedAccount);
        
        verify(service, times(2)).deletePermission(eq(TEST_APP_ID), guidCaptor.capture());
        
        List<String> capturedGuids = guidCaptor.getAllValues();
        assertEquals(capturedGuids.size(), 2);
        assertTrue(capturedGuids.containsAll(ImmutableSet.of("delete-guid-1", "delete-guid-4")));
    }
    
    @Test
    public void updatePermissionsFromRoles_deleteIgnoresEntityNotFoundException() {
        Account persistedAccount = createAccount();
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        Account account = createAccount();
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        when(mockSponsorService.getSponsoredStudyIds(eq(TEST_APP_ID), eq(TEST_ORG_ID)))
                .thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        
        List<Permission> existingPermissionsList = ImmutableList.of(
                createPermission(AccessLevel.READ, ORGANIZATION, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-1"),
                createPermission(AccessLevel.DELETE, PARTICIPANTS, TEST_STUDY_ID, "delete-guid-4"),
                createPermission(AccessLevel.LIST, SPONSORED_STUDIES, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.LIST, MEMBERS, TEST_ORG_ID, GUID),
                createPermission(AccessLevel.READ, ASSESSMENT_LIBRARY, TEST_ORG_ID, GUID));
        
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID))).thenReturn(existingPermissionsList);
        
        doReturn(null).when(service).createPermission(eq(TEST_APP_ID), any());
        doThrow(EntityNotFoundException.class).when(service).deletePermission(eq(TEST_APP_ID), any());
        
        service.updatePermissionsFromRoles(account, persistedAccount);
        
        verify(service, times(2)).deletePermission(eq(TEST_APP_ID), any());
    }
    
    @Test
    public void updatePermissionsFromRoles_noOrgInAccount() {
        Account persistedAccount = Account.create();
        persistedAccount.setRoles(ImmutableSet.of(ORG_ADMIN));
        
        Account account = Account.create();
        account.setRoles(ImmutableSet.of(RESEARCHER));
        
        service.updatePermissionsFromRoles(account, account);
        
        verifyZeroInteractions(mockSponsorService);
        verify(service, times(0)).deletePermission(any(), any());
        verify(service, times(0)).createPermission(any(), any());
    }
    
    private Account createAccount() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setOrgMembership(TEST_ORG_ID);
        return account;
    }
    
    private Permission createPermission() {
        Permission permission = new Permission();
        permission.setGuid(GUID);
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(AccessLevel.ADMIN);
        permission.setEntityType(STUDY);
        permission.setEntityId(TEST_STUDY_ID);
        permission.setCreatedOn(CREATED_ON);
        permission.setModifiedOn(CREATED_ON);
        permission.setVersion(1L);
        return permission;
    }
    
    private Permission createPermission(AccessLevel accessLevel, EntityType entityType, String entityId) {
        Permission permission = new Permission();
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(accessLevel);
        permission.setEntityType(entityType);
        permission.setEntityId(entityId);
        return permission;
    }
    
    private Permission createPermission(AccessLevel accessLevel, EntityType entityType, String entityId, String guid) {
        Permission permission = new Permission();
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(accessLevel);
        permission.setEntityType(entityType);
        permission.setEntityId(entityId);
        permission.setGuid(guid);
        return permission;
    }
}