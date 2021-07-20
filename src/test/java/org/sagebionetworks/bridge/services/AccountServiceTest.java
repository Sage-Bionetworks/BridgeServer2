package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.STORMPATH_HMAC_SHA_256;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.services.AccountService.ROTATIONS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class AccountServiceTest extends Mockito {

    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_APP_ID, EMAIL);
    private static final SignIn SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withReauthToken("reauthToken").build();
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_APP_ID, PHONE);
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593Z");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";
    private static final String OTHER_USER_ID = "other-user-id";
    
    private static final String STUDY_A = "studyA";
    private static final String STUDY_B = "studyB";
    private static final Set<Enrollment> ACCOUNT_ENROLLMENTS = ImmutableSet
            .of(Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID));
    private static final ImmutableSet<String> CALLER_STUDIES = ImmutableSet.of(STUDY_B);    
    
    private static final SignIn PASSWORD_SIGNIN = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withPassword(DUMMY_PASSWORD).build();
    private static final SignIn REAUTH_SIGNIN = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withReauthToken(REAUTH_TOKEN).build();

    @Mock
    AccountDao mockAccountDao;

    @Mock
    AccountSecretDao mockAccountSecretDao;
    
    @Mock
    AppService appService;
    
    @Mock
    StudyActivityEventService studyActivityEventService;

    @Mock
    ActivityEventService activityEventService;
    
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
    
    @Captor
    ArgumentCaptor<StudyActivityEventRequest> requestCaptor;

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
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }

    @Test
    public void getAppIdsForUser() {
        List<String> apps = ImmutableList.of("app1", "app2");
        when(mockAccountDao.getAppIdForUser(SYNAPSE_USER_ID)).thenReturn(apps);

        List<String> returnVal = service.getAppIdsForUser(SYNAPSE_USER_ID);
        assertEquals(returnVal, apps);
        verify(mockAccountDao).getAppIdForUser(SYNAPSE_USER_ID);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_NullSynapseUserId() {
        service.getAppIdsForUser(null);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_EmptySynapseUserId() {
        service.getAppIdsForUser("");
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_BlankSynapseUserId() {
        service.getAppIdsForUser("   ");
    }

    @Test
    public void verifyChannel() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setEmailVerified(false);
        
        service.verifyChannel(ChannelType.EMAIL, account);
        verify(mockAccountDao).updateAccount(account);
    }

    @Test
    public void changePassword() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setStatus(UNVERIFIED);
        
        service.changePassword(account, ChannelType.PHONE, "asdf");
        verify(mockAccountDao).updateAccount(account);
    }

    @Test
    public void authenticate() throws Exception {
        App app = App.create();
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(account));
        doNothing().when(service).verifyPassword(any(), any());

        Account returnVal = service.authenticate(app, SIGN_IN);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
    }

    @Test
    public void reauthenticate() throws Exception {
        App app = App.create();
        app.setReauthenticationEnabled(true);
        
        Account account = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);
        when(mockAccountSecretDao.verifySecret(REAUTH, TEST_USER_ID, "reauthToken", ROTATIONS))
                .thenReturn(Optional.of(mockSecret));

        Account returnVal = service.reauthenticate(app, SIGN_IN);
        assertEquals(returnVal, account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
    }

    @Test
    public void deleteReauthToken() throws Exception {
        Account account = Account.create();
        account.setId(TEST_USER_ID);

        service.deleteReauthToken(account);
        
        verify(mockAccountSecretDao).removeSecrets(REAUTH, TEST_USER_ID);
    }

    @Test
    public void createAccount() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);
        account.setAppId("wrong-app");
        account.setNote(TEST_NOTE);

        service.createAccount(app, account);
        verify(mockAccountDao).createAccount(eq(app), accountCaptor.capture());
        
        Account createdAccount = accountCaptor.getValue();
        assertEquals(createdAccount.getId(), TEST_USER_ID);
        assertEquals(createdAccount.getAppId(), TEST_APP_ID);
        assertEquals(createdAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getStatus(), UNVERIFIED);
        assertEquals(createdAccount.getMigrationVersion(), MIGRATION_VERSION);
        assertEquals(createdAccount.getNote(), TEST_NOTE);
    }

    @Test
    public void updateAccount() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);

        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
    }
    
    @Test
    public void updateAccountNotFound() {
        // mock hibernate
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.empty());
        
        // execute
        try {
            service.updateAccount(account);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getMessage(), "Account not found.");
        }
    }

    @Test
    public void editAccount() throws Exception {
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, HEALTH_CODE);
        Account account = mockGetAccountById(accountId, false);

        service.editAccount(TEST_APP_ID, HEALTH_CODE, mockConsumer);

        InOrder inOrder = inOrder(mockConsumer, mockAccountDao);
        inOrder.verify(mockConsumer).accept(account);
        inOrder.verify(mockAccountDao).updateAccount(account);
    }

    @Test
    public void editAccountWhenAccountNotFound() throws Exception {
        service.editAccount(TEST_APP_ID, "bad-health-code", mockConsumer);

        verify(mockConsumer, never()).accept(any());
        verify(mockAccountDao, never()).updateAccount(any());
    }

    @Test
    public void getAccount() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);

        Optional<Account> returnVal = service.getAccount(ACCOUNT_ID);
        assertEquals(returnVal.get(), account);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }

    @Test
    public void deleteAccount() throws Exception {
        mockGetAccountById(ACCOUNT_ID, false);

        service.deleteAccount(ACCOUNT_ID);
        verify(mockAccountDao).deleteAccount(TEST_USER_ID);
    }
    
    @Test
    public void deleteAccountNotFound() {
        service.deleteAccount(ACCOUNT_ID);
        verify(mockAccountDao, never()).deleteAccount(any());
    }

    @Test
    public void getPagedAccountSummaries() {
        when(mockAccountDao.getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH)).thenReturn(mockAccountSummaries);

        PagedResourceList<AccountSummary> returnVal = service.getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH);
        assertEquals(returnVal, mockAccountSummaries);
        verify(mockAccountDao).getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH);
    }

    @Test
    public void getAccountHealthCode() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setHealthCode(HEALTH_CODE);

        Optional<String> healthCode = service.getAccountHealthCode(TEST_APP_ID, TEST_USER_ID);
        assertEquals(healthCode.get(), HEALTH_CODE);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void getAccountHealthCodeNoAccount() {
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        
        Optional<String> healthCode = service.getAccountHealthCode(TEST_APP_ID, TEST_USER_ID);
        assertFalse(healthCode.isPresent());
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }    

    @Test
    public void getAccountId() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setId(TEST_USER_ID);

        Optional<String> userId = service.getAccountId(TEST_APP_ID, TEST_USER_ID);
        assertEquals(userId.get(), TEST_USER_ID);
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void getAccountIdNoAccount() {
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        
        Optional<String> userId = service.getAccountId(TEST_APP_ID, TEST_USER_ID);
        assertFalse(userId.isPresent());
        verify(mockAccountDao).getAccount(ACCOUNT_ID);
    }
    
    @Test
    public void verifyEmailUsingToken() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(FALSE);

        service.verifyChannel(ChannelType.EMAIL, account);

        verify(mockAccountDao).updateAccount(account);
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(account.getModifiedOn().toString(), MOCK_DATETIME.withZone(UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
    }

    @Test
    public void verifyEmailUsingAccountNoChangeNecessary() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setStatus(ENABLED);
        account.setEmailVerified(TRUE);

        service.verifyChannel(ChannelType.EMAIL, account);
        
        verify(mockAccountDao, never()).updateAccount(any());
    }

    @Test
    public void verifyEmailWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        service.verifyChannel(ChannelType.EMAIL, account);
        
        verify(mockAccountDao, never()).updateAccount(any());
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void verifyPhoneUsingToken() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setPhone(PHONE);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(FALSE);

        service.verifyChannel(ChannelType.PHONE, account);

        verify(mockAccountDao).updateAccount(account);
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(account.getModifiedOn().toString(), MOCK_DATETIME.withZone(UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        verify(mockAccountDao).updateAccount(account);
    }

    @Test
    public void verifyPhoneUsingAccountNoChangeNecessary() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setStatus(ENABLED);
        account.setPhoneVerified(TRUE);

        service.verifyChannel(ChannelType.PHONE, account);
        verify(mockAccountDao, never()).updateAccount(any());
    }

    @Test
    public void verifyPhoneWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        service.verifyChannel(ChannelType.PHONE, account);
        verify(mockAccountDao, never()).updateAccount(any());
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void changePasswordSuccess() throws Exception {
        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);

        // execute and verify
        service.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture());

        Account updatedAccount = accountCaptor.getValue();
        assertEquals(updatedAccount.getId(), TEST_USER_ID);
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
        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setPhone(PHONE);
        account.setStatus(UNVERIFIED);

        // execute and verify
        service.changePassword(account, ChannelType.PHONE, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertTrue(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }
    
    @Test
    public void changePasswordForExternalId() {
        Enrollment en = Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID);
        en.setExternalId("anExternalId");
        
        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setStatus(UNVERIFIED);
        account.getEnrollments().add(en);

        // execute and verify
        service.changePassword(account, null, DUMMY_PASSWORD);
        verify(mockAccountDao).updateAccount(accountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test
    public void authenticateSuccessWithHealthCode() throws Exception {
        mockGetAccountById(PASSWORD_SIGNIN.getAccountId(), true);

        App app = App.create();

        Account account = service.authenticate(app, PASSWORD_SIGNIN);
        assertEquals(account.getId(), TEST_USER_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);
        assertEquals(account.getVersion(), 1); // version not incremented by update
    }

    // This test is just a negative test to verify that the reauth token is not being rotated...
    // regardless of how app.reauthenticationEnabled is set, it will succeed because we don't
    // touch the reauth token
    @Test
    public void authenticateSuccessNoReauthentication() throws Exception {
        App app = App.create();
        app.setReauthenticationEnabled(false);

        mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);

        Account account = service.authenticate(app, PASSWORD_SIGNIN);
        // not incremented by reauthentication
        assertEquals(account.getVersion(), 1);

        // No reauthentication token rotation occurs
        verify(mockAccountDao, never()).updateAccount(any());
        assertNull(account.getReauthToken());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.empty());

        App app = App.create();

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
        persistedAccount.setEmailVerified(false);

        App app = App.create();
        app.setEmailVerificationEnabled(true);

        service.authenticate(app, PASSWORD_SIGNIN);
    }
    
    @Test
    public void authenticateAccountUnverifiedNoEmailVerification() throws Exception {
        // mock hibernate
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
        persistedAccount.setEmailVerified(false);

        App app = App.create();
        app.setEmailVerificationEnabled(false);

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
        persistedAccount.setStatus(DISABLED);

        App app = App.create();

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);

        App app = App.create();

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);
        persistedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);

        App app = App.create();

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);

        App app = App.create();

        service.authenticate(app, new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        App app = App.create();
        app.setReauthenticationEnabled(true);

        Account account = service.reauthenticate(app, REAUTH_SIGNIN);
        assertEquals(account.getId(), TEST_USER_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getEmail(), EMAIL);
        // Version has not been incremented by an update
        assertEquals(account.getVersion(), 1);

        verify(mockAccountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
        verify(mockAccountDao, never()).createAccount(any(), any());
        verify(mockAccountDao, never()).updateAccount(any());

        // verify token verification
        verify(mockAccountSecretDao).verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, 3);
    }

    @Test
    public void reauthenticationDisabled() throws Exception {
        App app = App.create();
        app.setReauthenticationEnabled(false);

        try {
            service.reauthenticate(app, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).getAccount(any());
        verify(mockAccountDao, never()).updateAccount(any());
    }

    // branch coverage
    @Test
    public void reauthenticationFlagNull() {
        App app = App.create();
        app.setReauthenticationEnabled(null);

        try {
            service.reauthenticate(app, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockAccountDao, never()).getAccount(any());
        verify(mockAccountDao, never()).updateAccount(any());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountNotFound() throws Exception {
        when(mockAccountDao.getAccount(REAUTH_SIGNIN.getAccountId())).thenReturn(Optional.empty());

        App app = App.create();
        app.setReauthenticationEnabled(true);

        service.reauthenticate(app, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void reauthenticateAccountUnverified() throws Exception {
        Account persistedAccount = mockGetAccountById(REAUTH_SIGNIN.getAccountId(), false);
        persistedAccount.setEmailVerified(false);

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        App app = App.create();
        app.setReauthenticationEnabled(true);
        app.setEmailVerificationEnabled(true);

        service.reauthenticate(app, REAUTH_SIGNIN);
    }
    
    @Test
    public void reauthenticateAccountUnverifiedNoEmailVerification() throws Exception {
        Account persistedAccount = mockGetAccountById(REAUTH_SIGNIN.getAccountId(), false);
        persistedAccount.setEmailVerified(false);

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        App app = App.create();
        app.setReauthenticationEnabled(true);
        app.setEmailVerificationEnabled(false);

        service.reauthenticate(app, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        Account persistedAccount = mockGetAccountById(REAUTH_SIGNIN.getAccountId(), false);
        persistedAccount.setStatus(DISABLED);

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
                .thenReturn(Optional.of(secret));

        App app = App.create();
        app.setReauthenticationEnabled(true);

        service.reauthenticate(app, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        Account persistedAccount = mockGetAccountById(REAUTH_SIGNIN.getAccountId(), false);
        persistedAccount.setStatus(DISABLED);

        // it has no record in the secrets table

        App app = App.create();
        app.setReauthenticationEnabled(true);

        service.reauthenticate(app, REAUTH_SIGNIN);
    }

    // This throws ENFE if password fails, so this just verifies a negative case (account status
    // doesn't change outcome of test)
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, false);
        persistedAccount.setStatus(DISABLED);

        App app = App.create();

        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
                .withPassword("bad password").build();
        service.authenticate(app, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateBadReauthToken() throws Exception {
        mockGetAccountById(ACCOUNT_ID, false);

        App app = App.create();
        app.setReauthenticationEnabled(true);

        service.reauthenticate(app, new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getByEmail() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);

        Optional<Account> retValue = service.getAccount(ACCOUNT_ID_WITH_EMAIL);

        assertEquals(retValue.get(), persistedAccount);
    }

    @Test
    public void createAccountSuccess() throws Exception {
        // App passed into createAccount() takes precedence over appId in the Account object. To test this, make
        // the account have a different app.
        Account account = mockGetAccountById(ACCOUNT_ID, true);
        account.setAppId("wrong-app");

        App app = App.create();
        app.setIdentifier(TEST_APP_ID);

        service.createAccount(app, account);

        verify(mockAccountDao).createAccount(eq(app), accountCaptor.capture());

        Account createdAccount = accountCaptor.getValue();
        assertEquals(createdAccount.getId(), TEST_USER_ID);
        assertEquals(createdAccount.getAppId(), TEST_APP_ID);
        assertEquals(createdAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdAccount.getMigrationVersion(), MIGRATION_VERSION);
        
        verify(activityEventService, never()).publishEnrollmentEvent(any(), any(), any());
        verify(studyActivityEventService, never()).publishEvent(any());
    }
    
    @Test
    public void createAccountPublishesEnrollmentEvent() throws Exception {
        // App passed into createAccount() takes precedence over appId in the Account object. To test this, make
        // the account have a different app.
        Account account = mockGetAccountById(ACCOUNT_ID, true);
        account.setAppId("wrong-app");
        
        Enrollment enA = Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID);
        Enrollment enB = Enrollment.create(TEST_APP_ID, STUDY_B, TEST_USER_ID);
        account.getEnrollments().add(enA);
        account.getEnrollments().add(enB);
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);

        service.createAccount(app, account);

        verify(mockAccountDao).createAccount(app, account);
        verify(activityEventService).publishEnrollmentEvent(any(), any(), any());
        verify(studyActivityEventService, times(2)).publishEvent(requestCaptor.capture());
        
        StudyActivityEventRequest req1 = requestCaptor.getAllValues().get(0);
        assertEquals(req1.getAppId(), TEST_APP_ID);
        assertEquals(req1.getStudyId(), STUDY_A);
        assertEquals(req1.getUserId(), TEST_USER_ID);
        assertEquals(req1.getObjectType(), ENROLLMENT);
        assertEquals(req1.getTimestamp(), account.getCreatedOn());
        
        StudyActivityEventRequest req2 = requestCaptor.getAllValues().get(1);
        assertEquals(req2.getAppId(), TEST_APP_ID);
        assertEquals(req2.getStudyId(), STUDY_B);
        assertEquals(req2.getUserId(), TEST_USER_ID);
        assertEquals(req2.getObjectType(), ENROLLMENT);
        assertEquals(req2.getTimestamp(), account.getCreatedOn());
    }

    @Test
    public void updateSuccess() throws Exception {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        Account persistedAccount = Account.create();
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setCreatedOn(MOCK_DATETIME);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);
        persistedAccount.setPasswordModifiedOn(MOCK_DATETIME);
        persistedAccount.setModifiedOn(MOCK_DATETIME);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);
        persistedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        persistedAccount.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(DUMMY_PASSWORD));
        persistedAccount.setPasswordModifiedOn(MOCK_DATETIME);
        
        // This is costly to recompute, just get a reference to check against later. 
        String hash = persistedAccount.getPasswordHash();
        
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setCreatedOn(MOCK_DATETIME.plusDays(4));
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(FALSE);
        account.setPhoneVerified(FALSE);
        account.setPasswordAlgorithm(STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("a-hash");
        account.setPasswordModifiedOn(MOCK_DATETIME.plusDays(4));
        account.setModifiedOn(MOCK_DATETIME.plusDays(4));        

        // Execute. Identifiers not allows to change.
        service.updateAccount(account);

        verify(mockAccountDao).updateAccount(accountCaptor.capture());
        Account updatedAccount = accountCaptor.getValue();
        assertEquals(updatedAccount.getAppId(), TEST_APP_ID);
        assertEquals(updatedAccount.getId(), TEST_USER_ID);
        assertEquals(updatedAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(updatedAccount.getEmail(), OTHER_EMAIL);
        assertEquals(updatedAccount.getPhone(), OTHER_PHONE);
        assertFalse(updatedAccount.getEmailVerified());
        assertFalse(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getPasswordAlgorithm(), DEFAULT_PASSWORD_ALGORITHM);
        assertEquals(updatedAccount.getPasswordHash(), hash);
        assertEquals(updatedAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(updatedAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        
        verify(activityEventService, never()).publishEnrollmentEvent(any(), any(), any());
        verify(studyActivityEventService, never()).publishEvent(any());
    }

    @Test
    public void updateAccountPublishesEnrollmentEvent() throws Exception {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        Account persistedAccount = Account.create();
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setHealthCode(HEALTH_CODE);
        persistedAccount.setModifiedOn(MOCK_DATETIME);
        
        Enrollment enA = Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID);
        persistedAccount.getEnrollments().add(enA);
        
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setModifiedOn(MOCK_DATETIME);
        account.getEnrollments().add(enA);
        Enrollment enB = Enrollment.create(TEST_APP_ID, STUDY_B, TEST_USER_ID);
        account.getEnrollments().add(enB);
        
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        when(appService.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute. Identifiers not allows to change.
        service.updateAccount(account);
        
        verify(activityEventService).publishEnrollmentEvent(
                eq(app), eq(HEALTH_CODE), any(DateTime.class));
        verify(studyActivityEventService).publishEvent(requestCaptor.capture());
        StudyActivityEventRequest req = requestCaptor.getValue();
        assertEquals(req.getAppId(), TEST_APP_ID);
        assertEquals(req.getStudyId(), STUDY_B);
        assertEquals(req.getUserId(), TEST_USER_ID);
        assertEquals(req.getTimestamp(), MOCK_DATETIME);
        assertEquals(req.getObjectType(), ENROLLMENT);
    }
    
    @Test
    public void updateDoesNotChangePassword() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setPasswordAlgorithm(STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("bad password hash");
        account.setPasswordModifiedOn(MOCK_DATETIME);

        service.updateAccount(account);

        verify(mockAccountDao).updateAccount(accountCaptor.capture());

        // These values were loaded, have not been changed, and were persisted as is.
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getPasswordAlgorithm(), persistedAccount.getPasswordAlgorithm());
        assertEquals(captured.getPasswordHash(), persistedAccount.getPasswordHash());
        assertEquals(captured.getPasswordModifiedOn(), persistedAccount.getPasswordModifiedOn());
    }
    
    @Test
    public void updateDoesNotChangeOrgMembership() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setOrgMembership(TEST_ORG_ID);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setOrgMembership("some other nonsense");

        service.updateAccount(account);

        verify(mockAccountDao).updateAccount(accountCaptor.capture());

        // These values were loaded, have not been changed, and were persisted as is.
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getOrgMembership(), TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedEmailFails() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
        persistedAccount.setEmailVerified(false);

        App app = App.create();
        app.setVerifyChannelOnSignInEnabled(true);
        app.setEmailVerificationEnabled(true);

        service.authenticate(app, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedPhoneFails() throws Exception {
        // mock hibernate
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_PHONE, true);
        persistedAccount.setPhoneVerified(null);

        App app = App.create();
        app.setVerifyChannelOnSignInEnabled(true);

        // execute and verify - Verify just ID, app, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();
        service.authenticate(app, phoneSignIn);
    }
    
    @Test
    public void authenticateAccountEmailUnverifiedWithoutEmailVerificationOK() throws Exception {
        // mock hibernate 
        Account persistedAccount = mockGetAccountById(PASSWORD_SIGNIN.getAccountId(), true); 
        persistedAccount.setEmailVerified(false);

        App app = App.create();
        app.setEmailVerificationEnabled(false);

        service.authenticate(app, PASSWORD_SIGNIN); 
    }

    @Test
    public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
        // mock hibernate 
        Account persistedAccount = mockGetAccountById(PASSWORD_SIGNIN.getAccountId(), true); 
        persistedAccount.setEmailVerified(false);

        App app = App.create();
        app.setVerifyChannelOnSignInEnabled(false);

        service.authenticate(app, PASSWORD_SIGNIN);
    }
    
    @Test
    public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
        SignIn phoneSignIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();

        // mock hibernate
        Account persistedAccount = mockGetAccountById(phoneSignIn.getAccountId(), true);
        persistedAccount.setPhoneVerified(null);
        
        App app = App.create();
        app.setVerifyChannelOnSignInEnabled(false);

        // execute and verify - Verify just ID, app, and email, and health code mapping is enough. 
        service.authenticate(app, phoneSignIn);
    }
    
    // editAccountFailsAcrossStudies removed because editAccount is now the preferred way
    // for the system to update the accounts table, avoiding security checks.
    
    @Test
    public void getAccountMatchesStudies() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setEnrollments(Sets.newHashSet(ACCOUNT_ENROLLMENTS));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(STUDY_A)).build());

        Optional<Account> retValue = service.getAccount(ACCOUNT_ID);
        assertEquals(persistedAccount, retValue.get());
        
        RequestContext.set(null);
    }
    
    @Test
    public void getAccountFiltersStudies() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setEnrollments(Sets.newHashSet(ACCOUNT_ENROLLMENTS));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(OTHER_USER_ID)
                .withOrgSponsoredStudies(ImmutableSet.of(STUDY_B)).build());

        Optional<Account> retValue = service.getAccount(ACCOUNT_ID);
        assertFalse(retValue.isPresent());
        
        RequestContext.set(null);
    }
    
    @Test
    public void getAccountNoFilter() throws Exception {
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setEnrollments(Sets.newHashSet(ACCOUNT_ENROLLMENTS));
        
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(STUDY_B)).build());

        Optional<Account> account = service.getAccountNoFilter(ACCOUNT_ID);
        assertTrue(account.isPresent());
        
        RequestContext.set(null);
    }

    @Test
    public void updateAccountNoteSuccessfulAsAdmin() throws Exception {
        // RESEARCHER role set in beforeMethod()
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setNote("original note");

        Account copyAccount = Account.create();
        copyAccount.setAppId(persistedAccount.getAppId());
        copyAccount.setId(persistedAccount.getId());
        copyAccount.setNote("updated note");

        service.updateAccount(copyAccount);
        verify(mockAccountDao).updateAccount(accountCaptor.capture());
        Account updatedAccount = accountCaptor.getValue();

        assertEquals(updatedAccount.getNote(), "updated note");
    }

    @Test
    public void updateAccountNoteUnsuccessfulAsNonAdmin() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of()).build());

        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, true);
        persistedAccount.setNote("original note");

        Account copyAccount = Account.create();
        copyAccount.setAppId(persistedAccount.getAppId());
        copyAccount.setId(persistedAccount.getId());
        copyAccount.setNote("updated note");

        service.updateAccount(copyAccount);
        verify(mockAccountDao).updateAccount(accountCaptor.capture());
        Account updatedAccount = accountCaptor.getValue();

        assertEquals(updatedAccount.getNote(), "original note");
    }
    
    @Test
    public void getPagedExternalIds() throws Exception { 
        PagedResourceList<ExternalIdentifierInfo> page = new PagedResourceList<ExternalIdentifierInfo>(
                ImmutableList.of(), 100);
        when(mockAccountDao.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, "idFilter", 10, 50)).thenReturn(page);
        
        PagedResourceList<ExternalIdentifierInfo> retValue = service.getPagedExternalIds(
                TEST_APP_ID, TEST_STUDY_ID, "idFilter", 10, 50);
        assertSame(retValue, page);
    }

    private Account mockGetAccountById(AccountId accountId, boolean generatePasswordHash) throws Exception {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setHealthCode(HEALTH_CODE);
        account.setVersion(1);
        if (generatePasswordHash) {
            // Password hashes are expensive to generate. Only generate them if the test actually needs them.
            account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
            account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(DUMMY_PASSWORD));
        }
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));
        return account;
    }
}
