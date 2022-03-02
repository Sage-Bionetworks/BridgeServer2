package org.sagebionetworks.bridge.hibernate;


import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HibernatePermissionDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getPermissions_pass() {
        
    }
    
    @Test
    public void getPermissions_failsWithNeitherUserIdOrObjectFilters() {
        
    }
    
    @Test
    public void getPermissions_passWithOnlyUserId() {
        
    }
    
    @Test
    public void getPermissions_passWithOnlyObjectFilters() {
        
    }
    
    @Test
    public void createPermission_pass() {
        
    }
    
    @Test
    public void updatePermission_pass() {
        
    }
    
    @Test
    public void deletePermission_pass() {
        
    }
}