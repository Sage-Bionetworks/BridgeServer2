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
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
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
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class AccountServiceTest extends Mockito {

    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL);
    private static final SignIn SIGN_IN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withReauthToken("reauthToken").build();
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE);
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";
    
    private static final String SUBSTUDY_A = "substudyA";
    private static final String SUBSTUDY_B = "substudyB";
    private static final Set<AccountSubstudy> ACCOUNT_SUBSTUDIES = ImmutableSet
            .of(AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_A, USER_ID));
    private static final ImmutableSet<String> CALLER_SUBSTUDIES = ImmutableSet.of(SUBSTUDY_B);
    
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

    @Mock
    Consumer<Account> mockConsumer;

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
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        Account account = Account.create();
        account.setId(USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);
        account.setStatus(ENABLED);
        account.setStudyId("wrong-study");
        Consumer<Account> consumer = (oneAccount) -> {};

        service.createAccount(study, account, consumer);
        verify(mockAccountDao).createAccount(eq(study), accountCaptor.capture(), eq(consumer));
        
        Account createdAccount = accountCaptor.getValue();
        assertEquals(createdAccount.getId(), USER_ID);
        assertEquals(createdAccount.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createdAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getStatus(), ENABLED);
        assertEquals(createdAccount.getMigrationVersion(), MIGRATION_VERSION);
    }

    @Test
    public void updateAccount() {
        Account account = mockGetAccountById();
        Consumer<Account> consumer = (oneAccount) -> {};

        service.updateAccount(account, consumer);
        verify(mockAccountDao).updateAccount(account, consumer);
    }
    
    @Test
    public void updateAccountNotFound() {
        // mock hibernate
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.empty());
        
        // execute
        try {
            service.updateAccount(account, null);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getMessage(), "Account not found.");
        }
    }

    @Test
    public void editAccount() {
        Account account = mockGetAccountById();
        AccountId accountId = AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, HEALTH_CODE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));

        service.editAccount(TEST_STUDY, HEALTH_CODE, mockConsumer);

        verify(mockAccountDao).updateAccount(account, mockConsumer);
    }

    @Test
    public void editAccountWhenAccountNotFound() throws Exception {
        service.editAccount(TEST_STUDY, "bad-health-code", mockConsumer);

        verify(mockConsumer, never()).accept(any());
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
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
        verify(mockAccountDao).deleteAccount(USER_ID);
    }
    
    @Test
    public void deleteAccountNotFound() {
        service.deleteAccount(ACCOUNT_ID);
        verify(mockAccountDao, never()).deleteAccount(any());
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
        Account hibernateAccount = makeValidAccount(true);
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

        Account persistedAccount = makeValidAccount(true);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Account account = service.authenticate(study, PASSWORD_SIGNIN);
        // not incremented by reauthentication
        assertEquals(account.getVersion(), 1);

        // No reauthentication token rotation occurs
        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        assertNull(account.getReauthToken());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenThrow(new EntityNotFoundException(Account.class));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        persistedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        Account persistedAccount = makeValidAccount(true);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

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
        Account persistedAccount = makeValidAccount(false);
        persistedAccount.setStatus(UNVERIFIED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(persistedAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        Study study = Study.create();
        study.setReauthenticationEnabled(true);

        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        persistedAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(persistedAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        Study study = Study.create();
        study.setReauthenticationEnabled(true);

        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        persistedAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.of(persistedAccount));

        // it has no record in the secrets table

        Study study = Study.create();
        study.setReauthenticationEnabled(true);

        service.reauthenticate(study, REAUTH_SIGNIN);
    }

    // This throws ENFE if password fails, so this just verifies a negative case (account status
    // doesn't change outcome of test)
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setStatus(DISABLED);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("bad password").build();
        service.authenticate(study, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateBadReauthToken() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();

        service.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getByEmail() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Account account = service.getAccount(ACCOUNT_ID_WITH_EMAIL);

        assertEquals(account, persistedAccount);
    }

    @Test
    public void deleteReauthTokenWithEmail() throws Exception {
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        service.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);

        verify(mockAccountSecretDao).removeSecrets(REAUTH, USER_ID);
    }

    @Test
    public void deleteReauthTokenNoToken() throws Exception {
        // Return an account with no reauth token.
        Account persistedAccount = makeValidAccount(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

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
        Account account = makeValidAccount(true);
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

        Account account = makeValidAccount(true);
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
        Account persistedAccount = makeValidAccount(true);
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

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedEmailFails() throws Exception {
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setEmailVerified(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();
        study.setVerifyChannelOnSignInEnabled(true);
        study.setEmailVerificationEnabled(true);

        service.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedPhoneFails() throws Exception {
        // mock hibernate
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setPhoneVerified(null);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();
        study.setVerifyChannelOnSignInEnabled(true);

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();
        service.authenticate(study, phoneSignIn);
    }
    
    @Test
    public void authenticateAccountEmailUnverifiedWithoutEmailVerificationOK() throws Exception {
        // mock hibernate 
        Account persistedAccount = makeValidAccount(true); 
        persistedAccount.setEmailVerified(false);
        when(mockAccountDao.getAccount(PASSWORD_SIGNIN.getAccountId())).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();
        study.setEmailVerificationEnabled(false);

        service.authenticate(study, PASSWORD_SIGNIN); 
    }

    @Test
    public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
        // mock hibernate 
        Account persistedAccount = makeValidAccount(true); 
        persistedAccount.setEmailVerified(false);
        when(mockAccountDao.getAccount(PASSWORD_SIGNIN.getAccountId())).thenReturn(Optional.of(persistedAccount));

        Study study = Study.create();
        study.setVerifyChannelOnSignInEnabled(false);

        service.authenticate(study, PASSWORD_SIGNIN);
    }
    
    @Test
    public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();

        // mock hibernate
        Account persistedAccount = makeValidAccount(true);
        persistedAccount.setPhoneVerified(null);
        when(mockAccountDao.getAccount(phoneSignIn.getAccountId())).thenReturn(Optional.of(persistedAccount));
        
        Study study = Study.create();
        study.setVerifyChannelOnSignInEnabled(false);

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough. 
        service.authenticate(study, phoneSignIn);
    }
    
    @Test
    public void editAccountFailsAcrossSubstudies() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(CALLER_SUBSTUDIES).build());

        Account persistedAccount = makeValidAccount(false);
        persistedAccount.setAccountSubstudies(ACCOUNT_SUBSTUDIES);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        service.editAccount(TEST_STUDY, HEALTH_CODE, (account) -> fail("Should have thrown exception"));

        verify(mockAccountDao, never()).updateAccount(any(), eq(null));
        BridgeUtils.setRequestContext(null);
    }

    private Account mockGetAccountById() {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return account;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static Account makeValidAccount(boolean generatePasswordHash) throws Exception {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setId(USER_ID);
        account.setHealthCode(HEALTH_CODE);
        account.setPhone(PHONE);
        account.setPhoneVerified(true);
        account.setEmail(EMAIL);
        account.setEmailVerified(true);
        account.setStatus(ENABLED);
        account.setMigrationVersion(MIGRATION_VERSION);
        account.setVersion(1);

        if (generatePasswordHash) {
            // Password hashes are expensive to generate. Only generate them if the test actually needs them.
            account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
            account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(DUMMY_PASSWORD));
        }
        return account;
    }
}
