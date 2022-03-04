package org.sagebionetworks.bridge.spring.controllers;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.services.PermissionService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Set;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.testng.Assert.assertEquals;

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
        assertPost(PermissionController.class, "createPermission");
        assertPost(PermissionController.class, "updatePermission");
        assertDelete(PermissionController.class, "deletePermission");
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    
        Permission permission = new Permission();
        permission.setGuid("testGuid");
        when(mockService.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID)).thenReturn(ImmutableSet.of(permission));
        
        Set<Permission> retValue = controller.getPermissionsForUser(TEST_USER_ID);
        
        assertEquals(retValue, ImmutableSet.of(permission));
    }
    
    @Test
    public void getPermissionsForObject_pass() {
//        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
//    
//        Permission permission = new Permission();
//        permission.setGuid("testGuid");
//        when(mockService.getPermissionsForEntity(TEST_APP_ID, "STUDY", TEST_STUDY_ID)).thenReturn(ImmutableSet.of(permission));
//    
//        Set<Permission> retValue = controller.getPermissionsForEntity("STUDY", TEST_STUDY_ID);
//    
//        assertEquals(retValue, ImmutableSet.of(permission));
    }
    
    @Test
    public void createPermission_pass() {
        
    }
    
    @Test
    public void createPermission_correctsAppId() {
        
    }
    
    @Test
    public void updatePermission_pass() {
        
    }
    
    @Test
    public void updatePermission_correctsAppId() {
        
    }
    
    @Test
    public void deletePermission_pass() {
        
    }
}