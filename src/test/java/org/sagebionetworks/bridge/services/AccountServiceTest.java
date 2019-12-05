package org.sagebionetworks.bridge.services;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AccountDao;

public class AccountServiceTest extends Mockito {

    @Mock
    AccountDao accountDao;
    
    AccountService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getStudyIdsForUser() { 
        
    }
    
    @Test
    public void verifyChannel() {
        
    }
    
    @Test
    public void changePassword() {
        
    }
    
    @Test
    public void authenticate() {
        
    }

    @Test
    public void reauthenticate() {
        
    }
    
    @Test
    public void deleteReauthToken() {
    }
    
    @Test
    public void createAccount() {
    }
    
    @Test
    public void updateAccount() {
    }
    
    @Test
    public void editAccount() {
    }
    
    @Test
    public void getAccount() {
    }
    
    @Test
    public void deleteAccount() {
    }

    @Test
    public void getPagedAccountSummaries() {
    }

    @Test
    public void getHealthCodeForAccount() {
    }
}
