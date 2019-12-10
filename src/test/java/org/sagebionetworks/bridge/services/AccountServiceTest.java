package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class AccountServiceTest extends Mockito {

    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    
    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    PagedResourceList<AccountSummary> mockAccountSummaries;

    
    @InjectMocks
    AccountService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getStudyIdsForUser() { 
        List<String> studies = ImmutableList.of("study1", "study2");
        when(mockAccountDao.getStudyIdsForUser(SYNAPSE_USER_ID)).thenReturn(studies);
        
        List<String> returnVal = service.getStudyIdsForUser(SYNAPSE_USER_ID);
        assertEquals(returnVal, studies);
        verify(mockAccountDao).getStudyIdsForUser(SYNAPSE_USER_ID);
    }
    
    @Test
    public void verifyChannel() {
        Account account = Account.create();
        
        service.verifyChannel(ChannelType.EMAIL, account);
        verify(mockAccountDao).verifyChannel(ChannelType.EMAIL, account);
    }
    
    @Test
    public void changePassword() {
        Account account = Account.create();
        
        service.changePassword(account, ChannelType.PHONE, "asdf");
        verify(mockAccountDao).changePassword(account, ChannelType.PHONE, "asdf");
    }
    
    @Test
    public void authenticate() {
        Study study = Study.create();
        SignIn signIn = new SignIn.Builder().build();
        Account account = Account.create();
        when(mockAccountDao.authenticate(study, signIn)).thenReturn(account);
        
        Account returnVal = service.authenticate(study, signIn);
        assertEquals(returnVal, account);
        verify(mockAccountDao).authenticate(study, signIn);
    }

    @Test
    public void reauthenticate() {
        Study study = Study.create();
        SignIn signIn = new SignIn.Builder().build();
        Account account = Account.create();
        when(mockAccountDao.reauthenticate(study, signIn)).thenReturn(account);
        
        Account returnVal = service.reauthenticate(study, signIn);
        assertEquals(returnVal, account);
        verify(mockAccountDao).reauthenticate(study, signIn);
    }
    
    @Test
    public void deleteReauthToken() {
        service.deleteReauthToken(ACCOUNT_ID);
        verify(mockAccountDao).deleteReauthToken(ACCOUNT_ID);
    }
    
    @Test
    public void createAccount() {
        Study study = Study.create();
        Account account = Account.create();
        Consumer<Account> consumer = (oneAccount) -> {};
        service.createAccount(study, account, consumer);
        verify(mockAccountDao).createAccount(study, account, consumer);
    }
    
    @Test
    public void updateAccount() {
        Account account = Account.create();
        Consumer<Account> consumer = (oneAccount) -> {};
        service.updateAccount(account, consumer);
        verify(mockAccountDao).updateAccount(account, consumer);
    }
    
    @Test
    public void editAccount() {
        Consumer<Account> consumer = (oneAccount) -> {};
        service.editAccount(TEST_STUDY, HEALTH_CODE, consumer);
        verify(mockAccountDao).editAccount(TEST_STUDY, HEALTH_CODE, consumer);
    }
    
    @Test
    public void getAccount() {
        Account account = Account.create();
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account returnVal = service.getAccount(ACCOUNT_ID);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void deleteAccount() {
        service.deleteAccount(ACCOUNT_ID);
        verify(mockAccountDao).deleteAccount(ACCOUNT_ID);
    }

    @Test
    public void getPagedAccountSummaries() {
        Study study = Study.create();
        when(mockAccountDao.getPagedAccountSummaries(study, EMPTY_SEARCH)).thenReturn(mockAccountSummaries);
        
        PagedResourceList<AccountSummary> returnVal = service.getPagedAccountSummaries(study, EMPTY_SEARCH);
        assertEquals(returnVal, mockAccountSummaries);
        verify(mockAccountDao).getPagedAccountSummaries(study, EMPTY_SEARCH);   
    }

    @Test
    public void getHealthCodeForAccount() {
        Account account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        String healthCode = service.getHealthCodeForAccount(ACCOUNT_ID);
        assertEquals(healthCode, HEALTH_CODE);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void getHealthCodeForAccountNoAccount() {
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        String healthCode = service.getHealthCodeForAccount(ACCOUNT_ID);
        assertNull(healthCode);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
}
