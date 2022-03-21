package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.AccessLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HibernatePermissionDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String, Object>> paramsCaptor;
    
    @InjectMocks
    HibernatePermissionDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getPermission_pass() {
        Permission permission = createPermission();
        when(mockHelper.getById(eq(Permission.class), eq(GUID))).thenReturn(permission);
        
        Optional<Permission> result = dao.getPermission(TEST_APP_ID, GUID);
        assertEquals(permission, result.get());
        
        verify(mockHelper).getById(eq(Permission.class), eq(GUID));
    }
    
    @Test
    public void getPermission_noMatchingPermission() {
        when(mockHelper.getById(eq(Permission.class), eq(GUID))).thenReturn(null);
        
        Optional<Permission> result = dao.getPermission(TEST_APP_ID, GUID);
        assertFalse(result.isPresent());
        
        verify(mockHelper).getById(eq(Permission.class), eq(GUID));
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        Permission permission = createPermission();
        when(mockHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(permission));
        
        List<Permission> permissions = dao.getPermissionsForUser(TEST_APP_ID, TEST_USER_ID);
        
        assertEquals(permissions.size(), 1);
        Permission returnedPermission = permissions.stream().findFirst().orElse(null);
        assertEquals(returnedPermission, permission);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Permission.class));
        assertEquals(queryCaptor.getValue(), "FROM Permission WHERE appId=:appId AND userId=:userId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("userId"), TEST_USER_ID);
    }
    
    @Test
    public void getPermissionsForEntity_pass() {
        Permission permission = createPermission();
        when(mockHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(permission));
        
        List<Permission> permissions = dao.getPermissionsForEntity(TEST_APP_ID, EntityType.STUDY, TEST_STUDY_ID);
    
        assertEquals(permissions.size(), 1);
        Permission returnedPermission = permissions.stream().findFirst().orElse(null);
        assertEquals(returnedPermission, permission);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Permission.class));
        assertEquals(queryCaptor.getValue(), "FROM Permission WHERE appId=:appId "+
                "AND entityType=:entityType AND entityId=:entityId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("entityType"), EntityType.STUDY);
        assertEquals(paramsCaptor.getValue().get("entityId"), TEST_STUDY_ID);
    }
    
    @Test
    public void createPermission_pass() {
        Permission permission = createPermission();
        dao.createPermission(TEST_APP_ID, permission);
        
        verify(mockHelper).create(eq(permission));
    }
    
    @Test
    public void updatePermission_pass() {
        Permission permission = createPermission();
        dao.updatePermission(TEST_APP_ID, permission);
        
        verify(mockHelper).update(eq(permission));
    }
    
    @Test
    public void deletePermission_pass() {
        dao.deletePermission(TEST_APP_ID, GUID);
        
        verify(mockHelper).deleteById(eq(Permission.class), eq(GUID));
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