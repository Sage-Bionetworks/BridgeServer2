package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.permissions.EntityRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.sagebionetworks.bridge.services.PermissionService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

public class PermissionControllerTest extends Mockito {
    
    @Mock
    PermissionService mockService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    PermissionController controller;
    
    @Captor
    ArgumentCaptor<Permission> permissionCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(PermissionController.class);
        assertGet(PermissionController.class, "getPermissionsForUser");
        assertGet(PermissionController.class, "getPermissionsForEntity");
        assertCreate(PermissionController.class, "createPermission");
        assertPost(PermissionController.class, "updatePermission");
        assertDelete(PermissionController.class, "deletePermission");
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        PermissionDetail permissionDetail = createPermissionDetail();
        
        when(mockService.getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID))).thenReturn(ImmutableList.of(permissionDetail));
        
        List<PermissionDetail> retValue = controller.getPermissionsForUser(TEST_USER_ID);
        
        verify(mockService).getPermissionsForUser(eq(TEST_APP_ID), eq(TEST_USER_ID));
        
        assertEquals(retValue, ImmutableList.of(permissionDetail));
    }
    
    @Test
    public void getPermissionsForEntity_pass() {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        PermissionDetail permissionDetail = createPermissionDetail();
    
        when(mockService.getPermissionsForEntity(eq(TEST_APP_ID), eq("STUDY"), eq(TEST_STUDY_ID)))
                .thenReturn(ImmutableList.of(permissionDetail));
    
        List<PermissionDetail> retValue = controller.getPermissionsForEntity("STUDY", TEST_STUDY_ID);
    
        verify(mockService).getPermissionsForEntity(eq(TEST_APP_ID), eq("STUDY"), eq(TEST_STUDY_ID));
    
        assertEquals(retValue, ImmutableList.of(permissionDetail));
    }
    
    @Test
    public void createPermission_pass() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        Permission permission = createPermission();
        permission.setGuid(null);
        
        mockRequestBody(mockRequest, permission);
        
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.createPermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
        
        PermissionDetail retValue = controller.createPermission();
        
        verify(mockService).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(retValue, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertNull(captured.getGuid());
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), EntityType.STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
    }
    
    @Test
    public void createPermission_nullsGuid() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        Permission permission = createPermission();
        permission.setGuid(GUID);
        
        mockRequestBody(mockRequest, permission);
        
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.createPermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
        
        PermissionDetail retValue = controller.createPermission();
        
        verify(mockService).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(retValue, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertNull(captured.getGuid());
    }
    
    @Test
    public void createPermission_usesSessionAppId() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        Permission permission = createPermission();
        permission.setAppId("fake-app-id");
    
        mockRequestBody(mockRequest, permission);
    
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.createPermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
    
        PermissionDetail retValue = controller.createPermission();
    
        verify(mockService).createPermission(eq(TEST_APP_ID), permissionCaptor.capture());
    
        assertEquals(retValue, permissionDetail);
    
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void updatePermission_pass() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        Permission permission = createPermission();
        
        mockRequestBody(mockRequest, permission);
    
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
    
        PermissionDetail retValue = controller.updatePermission(GUID);
    
        verify(mockService).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
        
        assertEquals(retValue, permissionDetail);
        
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(captured.getEntityType(), EntityType.STUDY);
        assertEquals(captured.getEntityId(), TEST_STUDY_ID);
    }
    
    @Test
    public void updatePermission_usesSessionAppId() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        Permission permission = createPermission();
        permission.setAppId("fake-app-id");
    
        mockRequestBody(mockRequest, permission);
    
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
    
        PermissionDetail retValue = controller.updatePermission(GUID);
    
        verify(mockService).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
    
        assertEquals(retValue, permissionDetail);
    
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void updatePermission_usesPathGuid() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        Permission permission = createPermission();
        permission.setGuid("fake-guid");
    
        mockRequestBody(mockRequest, permission);
    
        PermissionDetail permissionDetail = createPermissionDetail();
        when(mockService.updatePermission(eq(TEST_APP_ID), any())).thenReturn(permissionDetail);
    
        PermissionDetail retValue = controller.updatePermission(GUID);
    
        verify(mockService).updatePermission(eq(TEST_APP_ID), permissionCaptor.capture());
    
        assertEquals(retValue, permissionDetail);
    
        Permission captured = permissionCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
    }
    
    @Test
    public void deletePermission_pass() {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        doNothing().when(mockService).deletePermission(eq(TEST_APP_ID), eq(GUID));
    
        StatusMessage message = controller.deletePermission(GUID);
        
        assertEquals(message.getMessage(), "Permission deleted.");
    
        verify(mockService).deletePermission(eq(TEST_APP_ID), eq(GUID));
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
    
    private PermissionDetail createPermissionDetail() {
        Account account = Account.create();
        AccountRef accountRef = new AccountRef(account);
        
        Permission permission = createPermission();
        
        EntityRef entityRef = new EntityRef(permission.getEntityType(), permission.getEntityId(), "test-study-name");
                
        return new PermissionDetail(permission, entityRef, accountRef);
    }
}