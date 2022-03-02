package org.sagebionetworks.bridge.services;


import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PermissionServiceTest extends Mockito {
    
    @Mock
    PermissionDao mockDao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createPermission_pass() {
        
    }
    
    @Test
    public void createPermission_ignoresIncomingGuid() {
        
    }
    
    @Test
    public void createPermission_validatesIncomingPermission() {
        
    }
    
    @Test
    public void updatePermission_pass() {
        
    }
    
    @Test
    public void updatePermission_validatesIncomingPermission() {
        
    }
    
    @Test
    public void getPermissionsForUser_pass() {
        
    }
    
    @Test
    public void getPermissionsForObject_pass() {
        
    }
    
    @Test
    public void deletePermission_pass() {
        
    }
}