package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.Set;

public class PermissionServiceTest extends Mockito {
    
    @Mock
    PermissionDao mockDao;
    
    @Mock
    AccountService mockAccountService;
    
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
        PermissionDetail permissionDetail = new PermissionDetail(permission, new AccountRef(account));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
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
    
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        when(mockDao.createPermission(eq(TEST_APP_ID), any())).thenReturn(permission);
    
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
    
        Account account = Account.create();
        account.setEmail("email@email.com");
        PermissionDetail permissionDetail = new PermissionDetail(permission, new AccountRef(account));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        when(mockDao.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permission);
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
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
        when(service.generateGuid()).thenReturn(GUID);
        Permission permission = createPermission();
        permission.setGuid(null);
        
        service.updatePermission(TEST_APP_ID, permission);
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        Permission permission = createPermission();
        when(mockDao.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID)))
                .thenReturn(ImmutableSet.of(permission));
        
        Set<Permission> permissionSet = service.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID);
        
        verify(mockDao).getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID));
        assertEquals(permissionSet.size(), 1);
        assertTrue(permissionSet.contains(permission));
    }
    
    @Test
    public void getPermissionsForObject_pass() {
        Permission permission = createPermission();
        
        Account account = Account.create();
        account.setEmail("email@email.com");
        PermissionDetail permissionDetail = new PermissionDetail(permission, new AccountRef(account));
        
        when(mockDao.getPermissionsForEntity(eq(TEST_APP_ID), eq(EntityType.STUDY), eq(TEST_STUDY_ID)))
                .thenReturn(ImmutableSet.of(permission));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));
        doReturn(permissionDetail).when(service).getPermissionDetail(eq(TEST_APP_ID), any());
        
        Set<PermissionDetail> permissionDetailSet = service.getPermissionsForEntity(TEST_APP_ID, "STUDY", TEST_STUDY_ID);
    
        verify(mockDao).getPermissionsForEntity(eq(TEST_APP_ID), eq(EntityType.STUDY), eq(TEST_STUDY_ID));
        assertEquals(permissionDetailSet.size(), 1);
        PermissionDetail returnedDetail = permissionDetailSet.stream().findFirst().orElse(null);
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