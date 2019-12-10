package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.STORMPATH_HMAC_SHA_256;
import static org.sagebionetworks.bridge.services.AccountService.ROTATIONS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.hibernate.HibernateAccount;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class AccountServiceTest extends Mockito {

    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL);
    private static final SignIn SIGN_IN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withReauthToken("reauthToken").build();
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";

    private static final SignIn PASSWORD_SIGNIN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withPassword(DUMMY_PASSWORD).build();
    private static final SignIn REAUTH_SIGNIN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withReauthToken(REAUTH_TOKEN).build();

    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    AccountSecretDao mockAccountSecretDao;
    
    @Mock
    PagedResourceList<AccountSummary> mockAccountSummaries;
    
    @Mock
    AccountSecret mockSecret;

    @InjectMocks
    @Spy
    AccountService service;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_DATETIME.getMillis());
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    private Account mockGetAccountById() {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return account;
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
        Account account = mockGetAccountById();
        
        service.verifyChannel(ChannelType.EMAIL, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
        verify(mockAccountDao).updateAccount(account, null);
    }
    
    @Test
    public void changePassword() {
        Account account = mockGetAccountById();
        
        service.changePassword(account, ChannelType.PHONE, "asdf");
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
        verify(mockAccountDao).updateAccount(account, null);
    }
    
    @Test
    public void authenticate() {
        Study study = Study.create();
        Account account = mockGetAccountById();
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(account));
        doNothing().when(service).verifyPassword(any(), any());
        
        Account returnVal = service.authenticate(study, SIGN_IN);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
    }

    @Test
    public void reauthenticate() {
        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        Account account = mockGetAccountById();
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(account));
        when(mockAccountSecretDao.verifySecret(REAUTH, USER_ID, "reauthToken", ROTATIONS))
            .thenReturn(Optional.of(mockSecret));
        
        Account returnVal = service.reauthenticate(study, SIGN_IN);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
    }
    
    @Test
    public void deleteReauthToken() {
        mockGetAccountById();        
        
        service.deleteReauthToken(ACCOUNT_ID);
        verify(mockAccountSecretDao).removeSecrets(REAUTH, USER_ID);
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
        Account account = mockGetAccountById();
        Consumer<Account> consumer = (oneAccount) -> {};
        
        service.updateAccount(account, consumer);
        verify(mockAccountDao).updateAccount(account, consumer);
    }
    
    @Test
    public void editAccount() {
        Account account = mockGetAccountById();
        AccountId accountId = AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, HEALTH_CODE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));
        
        Consumer<Account> consumer = (oneAccount) -> {};
        service.editAccount(TEST_STUDY, HEALTH_CODE, consumer);
    }
    
    @Test
    public void getAccount() {
        Account account = mockGetAccountById();
        
        Account returnVal = service.getAccount(ACCOUNT_ID);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void deleteAccount() {
        mockGetAccountById();
        
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
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        String healthCode = service.getHealthCodeForAccount(ACCOUNT_ID);
        assertEquals(healthCode, HEALTH_CODE);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    // *************************************************************************************************
    
    @Test
    public void verifyEmailUsingToken() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setEmailVerified(FALSE);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(FALSE);
        
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        service.verifyChannel(ChannelType.EMAIL, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getEmailVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
        
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }

    @Test
    public void verifyEmailUsingAccount() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setEmailVerified(FALSE);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(FALSE);

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        service.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getEmailVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(DateTimeZone.UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
        
        verify(mockAccountDao).updateAccount(hibernateAccount, null);
    }

    @Test
    public void verifyEmailUsingAccountNoChangeNecessary() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setEmailVerified(TRUE);

        Account account = Account.create();
        account.setId(USER_ID);
        account.setStatus(ENABLED);
        account.setEmailVerified(TRUE);

        service.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockAccountDao, never()).updateAccount(any(), any());
    }

    @Test
    public void verifyEmailWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        service.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockAccountDao, never()).updateAccount(any(), any());
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void verifyEmailFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(null);

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        try {
            service.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).updateAccount(any(), any());
        assertEquals(account.getStatus(), UNVERIFIED);
        assertNull(account.getEmailVerified());
    }

    @Test
    public void verifyPhoneUsingToken() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setPhoneVerified(FALSE);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(FALSE);

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        service.verifyChannel(ChannelType.PHONE, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getPhoneVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        verify(mockAccountDao).updateAccount(hibernateAccount, null);
    }

    @Test
    public void verifyPhoneUsingAccount() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setPhoneVerified(FALSE);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(FALSE);

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        service.verifyChannel(AuthenticationService.ChannelType.PHONE, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getPhoneVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        verify(mockAccountDao).updateAccount(hibernateAccount, null);
    }

    @Test
    public void verifyPhoneUsingAccountNoChangeNecessary() {
        Account hibernateAccount = Account.create();
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setPhoneVerified(TRUE);

        Account account = Account.create();
        account.setId(USER_ID);
        account.setStatus(ENABLED);
        account.setPhoneVerified(TRUE);

        service.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockAccountDao, never()).updateAccount(any(), any());
    }

    @Test
    public void verifyPhoneWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        service.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void verifyPhoneFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(null);

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        try {
            service.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        assertEquals(account.getStatus(), UNVERIFIED);
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void changePasswordSuccess() throws Exception {
        // mock hibernate
        Account hibernateAccount = Account.create();
        hibernateAccount.setId(USER_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        // Set up test account
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);

        // execute and verify
        service.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));

        Account updatedAccount = accountCaptor.getValue();
        assertEquals(updatedAccount.getId(), USER_ID);
        assertEquals(updatedAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(updatedAccount.getPasswordAlgorithm(), PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        assertEquals(updatedAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertTrue(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);

        // validate password hash
        assertTrue(DEFAULT_PASSWORD_ALGORITHM.checkHash(updatedAccount.getPasswordHash(), DUMMY_PASSWORD));
    }

    @Test
    public void changePasswordForPhone() throws Exception {
        // mock hibernate
        Account hibernateAccount = Account.create();
        hibernateAccount.setId(USER_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        // Set up test account
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);

        // execute and verify
        service.changePassword(account, ChannelType.PHONE, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertTrue(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void changePasswordAccountNotFound() {
        // mock hibernate
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());

        // Set up test account
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);

        // execute
        service.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
    }

    @Test
    public void changePasswordForExternalId() {
        // mock hibernate
        Account hibernateAccount = Account.create();
        hibernateAccount.setId(USER_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        // Set up test account
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);

        // execute and verify
        service.changePassword(account, null, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test
    public void authenticateSuccessWithHealthCode() throws Exception {
        Account hibernateAccount = makeValidHibernateAccount(true);
        when(mockAccountDao.getAccount(PASSWORD_SIGNIN.getAccountId())).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        Account account = service.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(account.getId(), USER_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);
        assertEquals(account.getVersion(), 1); // version not incremented by update
    }

    // This test is just a negative test to verify that the reauth token is not being rotated...
    // regardless of how study.reauthenticationEnabled is set, it will succeed because we don't
    // touch the reauth token
    @Test
    public void authenticateSuccessNoReauthentication() throws Exception {
        Study study = Study.create();
        study.setReauthenticationEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Account account = service.authenticate(study, PASSWORD_SIGNIN);
        // not incremented by reauthentication
        assertEquals(account.getVersion(), 1);

        // No reauthentication token rotation occurs
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        assertNull(account.getReauthToken());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL))
            .thenThrow(new EntityNotFoundException(Account.class));

        Study study = Study.create();
        
        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        service.authenticate(study, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        service.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                ROTATIONS)).thenReturn(Optional.of(secret));

        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        
        Account account = service.reauthenticate(study, REAUTH_SIGNIN);
        assertEquals(account.getId(), USER_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        // Version has not been incremented by an update
        assertEquals(account.getVersion(), 1);

        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
        verify(mockAccountDao, never()).createAccount(any(), any(), any());
        verify(mockAccountDao, never()).updateAccount(any(), any());

        // verify token verification
        verify(mockAccountSecretDao).verifySecret(REAUTH, USER_ID, REAUTH_TOKEN, 3);
    }

    @Test
    public void reauthenticationDisabled() throws Exception {
        Study study = Study.create();
        study.setReauthenticationEnabled(false);

        try {
            service.reauthenticate(study, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).getAccount(any());
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
    }

    // branch coverage
    @Test
    public void reauthenticationFlagNull() {
        Study study = Study.create();
        study.setReauthenticationEnabled(null);

        try {
            service.reauthenticate(study, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).getAccount(any());
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountNotFound() throws Exception {
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.empty());

        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        
        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void reauthenticateAccountUnverified() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                ROTATIONS)).thenReturn(Optional.of(secret));

        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        
        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                ROTATIONS)).thenReturn(Optional.of(secret));

        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        
        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(hibernateAccount));

        // it has no record in the secrets table
        
        Study study = Study.create();
        study.setReauthenticationEnabled(true);
        
        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    // This throws ENFE if password fails, so this just verifies a negative case (account status 
    // doesn't change outcome of test)
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("bad password").build();
        service.authenticate(study, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateBadReauthToken() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(hibernateAccount));

        Study study = Study.create();
        
        service.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getByEmail() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        Account account = service.getAccount(ACCOUNT_ID_WITH_EMAIL);

        assertEquals(account, hibernateAccount);
    }

    @Test
    public void deleteReauthTokenWithEmail() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        service.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);

        verify(mockAccountSecretDao).removeSecrets(REAUTH, USER_ID);
    }

    @Test
    public void deleteReauthTokenNoToken() throws Exception {
        // Return an account with no reauth token.
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(hibernateAccount));

        // Just quietly succeeds without doing any account update.
        service.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));

        // But we do always call this.
        verify(mockAccountSecretDao).removeSecrets(REAUTH, USER_ID);
    }

    @Test
    public void deleteReauthTokenAccountNotFound() throws Exception {
        // Just quietly succeeds without doing any work.
        service.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);

        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        verify(mockAccountSecretDao, never()).removeSecrets(AccountSecretType.REAUTH, USER_ID);
    }

    @Test
    public void createAccountSuccess() throws Exception {
        // Study passed into createAccount() takes precedence over StudyId in the Account object. To test this, make
        // the account have a different study.
        Account account = makeValidHibernateAccount(true);
        account.setStudyId("wrong-study");

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        service.createAccount(study, account, null);

        verify(mockAccountDao).createAccount(eq(study), accountCaptor.capture(), eq(null));

        Account createdHibernateAccount = accountCaptor.getValue();
        assertEquals(createdHibernateAccount.getId(), USER_ID);
        assertEquals(createdHibernateAccount.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createdHibernateAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getStatus(), ENABLED);
        assertEquals(createdHibernateAccount.getMigrationVersion(), MIGRATION_VERSION);
    }

    @Test
    public void updateSuccess() throws Exception {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        Account persistedAccount = Account.create();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(new DateTime(1234L));
        persistedAccount.setPasswordModifiedOn(new DateTime(5678L));
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(new DateTime(5678L));

        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount));

        Account account = makeValidHibernateAccount(true);
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);

        // Execute. Identifiers not allows to change.
        service.updateAccount(account, null);

        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));

        Account updatedHibernateAccount = accountCaptor.getValue();
        assertEquals(updatedHibernateAccount.getId(), USER_ID);
        assertEquals(updatedHibernateAccount.getStudyId(), "persisted-study");
        assertEquals(updatedHibernateAccount.getEmail(), OTHER_EMAIL);
        assertEquals(updatedHibernateAccount.getPhone().getNationalFormat(), OTHER_PHONE.getNationalFormat());
        assertEquals(updatedHibernateAccount.getEmailVerified(), Boolean.FALSE);
        assertEquals(updatedHibernateAccount.getPhoneVerified(), Boolean.FALSE);
        assertEquals(updatedHibernateAccount.getCreatedOn().getMillis(), 1234);
        assertEquals(updatedHibernateAccount.getPasswordModifiedOn().getMillis(), 5678);
        assertEquals(updatedHibernateAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
    }

    @Test
    public void updateDoesNotChangePassword() throws Exception {
        HibernateAccount persistedAccount = makeValidHibernateAccount(true);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setPasswordAlgorithm(STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("bad password hash");
        account.setPasswordModifiedOn(MOCK_DATETIME);

        service.updateAccount(account, null);

        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));

        // These values were loaded, have not been changed, and were persisted as is.
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getPasswordAlgorithm(), persistedAccount.getPasswordAlgorithm());
        assertEquals(captured.getPasswordHash(), persistedAccount.getPasswordHash());
        assertEquals(captured.getPasswordModifiedOn(), persistedAccount.getPasswordModifiedOn());
    }
    

    @Test
    public void getByIdSuccessWithHealthCode() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(hibernateAccount));

        Account account = service.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(account.getId(), USER_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), "original-" + HEALTH_CODE);
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
    }
/*
    @Test
    public void getByIdSuccessCreateNewHealthCode() throws Exception {
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);

        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByIdNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertNull(account);
    }

    @Test
    public void getByIdWrongStudy() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode(null);
        hibernateAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate
        AccountId wrongStudy = AccountId.forId("wrong-study", ACCOUNT_ID);
        Account account = dao.getAccount(wrongStudy);
        assertNull(account);
        
        verify(mockHibernateHelper).getById(HibernateAccount.class, wrongStudy.getUnguardedAccountId().getId());
    }
    
    @Test
    public void getByEmailSuccessWithHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.email=:email GROUP BY acct.id";

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), "original-" + HEALTH_CODE);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We don't create a new health code mapping nor update the account.
        verify(mockHibernateHelper, never()).update(any(), eq(null));
    }

    @Test
    public void getByEmailSuccessCreateNewHealthCode() throws Exception {
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);

        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId "
                + "WHERE acct.studyId = :studyId AND acct.email=:email GROUP BY acct.id";

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByEmailNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertNull(account);
    }

    @Test
    public void getByPhone() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.phone.number=:number AND acct.phone.regionCode=:regionCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, PHONE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByPhoneNotFound() {
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertNull(account);
    }

    @Test
    public void getSynapseUserId() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND " 
                + "acct.synapseUserId=:synapseUserId GROUP BY acct.id"; 
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, SYNAPSE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_SYNID);
        assertEquals(account.getId(), ACCOUNT_ID);
    }
    
    @Test
    public void getSynapseUserIdNotFound() {
        Account account = dao.getAccount(ACCOUNT_ID_WITH_SYNID);
        assertNull(account);
    }

    // ACCOUNT_ID_WITH_HEALTHCODE
    @Test
    public void getByHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.healthCode=:healthCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE);
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByHealthCodeNotFound() {
        Account account = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE);
        assertNull(account);
    }

    // ACCOUNT_ID_WITH_EXTID
    @Test
    public void getByExternalId() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId "
                + "AND acctSubstudy.externalId=:externalId GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, EXTID_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EXTID);
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByExternalIdNotFound() {
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EXTID);
        assertNull(account);
    }
*/    
    
    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(USER_ID);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        hibernateAccount.setPhone(PHONE);
        hibernateAccount.setPhoneVerified(true);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setEmailVerified(true);
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setMigrationVersion(MIGRATION_VERSION);
        hibernateAccount.setVersion(1);

        if (generatePasswordHash) {
            // Password hashes are expensive to generate. Only generate them if the test actually needs them.
            hibernateAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
            hibernateAccount.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(DUMMY_PASSWORD));
        }
        return hibernateAccount;
    }      
}
