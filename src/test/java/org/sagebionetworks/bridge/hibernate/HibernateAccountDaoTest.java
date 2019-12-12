package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
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
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class HibernateAccountDaoTest {
    private static final String ACCOUNT_ID = "account-id";
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-19T11:03:50.224-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String EMAIL = "eggplant@example.com";
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";
    private static final String HEALTH_CODE = "health-code";
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593-0700");
    private static final String FIRST_NAME = "Eggplant";
    private static final String LAST_NAME = "McTester";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final String EXTERNAL_ID = "an-external-id";
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE);
    private static final AccountId ACCOUNT_ID_WITH_HEALTHCODE = AccountId.forHealthCode(TEST_STUDY_IDENTIFIER,
            HEALTH_CODE);
    private static final AccountId ACCOUNT_ID_WITH_EXTID = AccountId.forExternalId(TEST_STUDY_IDENTIFIER, EXTERNAL_ID);
    private static final AccountId ACCOUNT_ID_WITH_SYNID = AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER,
            SYNAPSE_USER_ID);

    private static final String SUBSTUDY_A = "substudyA";
    private static final String SUBSTUDY_B = "substudyB";
    private static final Set<AccountSubstudy> ACCOUNT_SUBSTUDIES = ImmutableSet
            .of(AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_A, ACCOUNT_ID));
    private static final ImmutableSet<String> CALLER_SUBSTUDIES = ImmutableSet.of(SUBSTUDY_B);

    private static final SignIn REAUTH_SIGNIN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withReauthToken(REAUTH_TOKEN).build();
    private static final SignIn PASSWORD_SIGNIN = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
            .withPassword(DUMMY_PASSWORD).build();

    private static final Map<String, Object> STUDY_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).build();
    private static final Map<String, Object> EMAIL_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).put("email", EMAIL).build();
    private static final Map<String, Object> HEALTHCODE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).put("healthCode", HEALTH_CODE).build();
    private static final Map<String, Object> PHONE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).put("number", PHONE.getNumber())
            .put("regionCode", PHONE.getRegionCode()).build();
    private static final Map<String, Object> EXTID_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).put("externalId", EXTERNAL_ID).build();
    private static final Map<String, Object> SYNAPSE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", TEST_STUDY_IDENTIFIER).put("synapseUserId", SYNAPSE_USER_ID).build();

    @Captor
    ArgumentCaptor<Map<String, Object>> paramCaptor;

    @Mock
    Consumer<Account> accountConsumer;

    @Mock
    private AccountSecretDao mockAccountSecretDao;

    @Mock
    private HibernateHelper mockHibernateHelper;

    private Study study;
    private HibernateAccountDao dao;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_DATETIME.getMillis());
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        // Mock successful update.
        when(mockHibernateHelper.update(any(), eq(null))).thenAnswer(invocation -> {
            HibernateAccount account = invocation.getArgument(0);
            if (account != null) {
                account.setVersion(account.getVersion() + 1);
            }
            return account;
        });

        dao = spy(new HibernateAccountDao());
        dao.setHibernateHelper(mockHibernateHelper);
        dao.setAccountSecretDao(mockAccountSecretDao);

        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setReauthenticationEnabled(true);
        study.setEmailVerificationEnabled(true);
    }

    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void verifyEmailUsingToken() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setEmailVerified(FALSE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(Boolean.FALSE);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        dao.verifyChannel(ChannelType.EMAIL, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getEmailVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(DateTimeZone.UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
        verify(mockHibernateHelper).update(hibernateAccount, null);
    }

    @Test
    public void verifyEmailUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setEmailVerified(FALSE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(FALSE);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getEmailVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(DateTimeZone.UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getEmailVerified(), TRUE);
        verify(mockHibernateHelper).update(hibernateAccount, null);
    }

    @Test
    public void verifyEmailUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setEmailVerified(TRUE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(ENABLED);
        account.setEmailVerified(TRUE);

        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount, null);
    }

    @Test
    public void verifyEmailWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(any(), eq(null));
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void verifyEmailFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setEmailVerified(null);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);
        try {
            dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).update(any(), eq(null));
        assertEquals(account.getStatus(), UNVERIFIED);
        assertNull(account.getEmailVerified());
    }

    @Test
    public void verifyPhoneUsingToken() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setPhoneVerified(FALSE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(FALSE);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        dao.verifyChannel(ChannelType.PHONE, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getPhoneVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(DateTimeZone.UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        verify(mockHibernateHelper).update(hibernateAccount, null);
    }

    @Test
    public void verifyPhoneUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(UNVERIFIED);
        hibernateAccount.setPhoneVerified(FALSE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(FALSE);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);

        assertEquals(hibernateAccount.getStatus(), ENABLED);
        assertEquals(hibernateAccount.getPhoneVerified(), TRUE);
        // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
        assertEquals(hibernateAccount.getModifiedOn().toString(), MOCK_DATETIME.withZone(DateTimeZone.UTC).toString());
        assertEquals(account.getStatus(), ENABLED);
        assertEquals(account.getPhoneVerified(), TRUE);
        verify(mockHibernateHelper).update(hibernateAccount, null);
    }

    @Test
    public void verifyPhoneUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setPhoneVerified(TRUE);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(ENABLED);
        account.setPhoneVerified(TRUE);

        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount, null);
    }

    @Test
    public void verifyPhoneWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(DISABLED);

        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(any(), eq(null));
        assertEquals(account.getStatus(), DISABLED);
    }

    @Test
    public void verifyPhoneFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(UNVERIFIED);
        account.setPhoneVerified(null);

        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);
        try {
            dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).update(any(), eq(null));
        assertEquals(account.getStatus(), UNVERIFIED);
        assertNull(account.getPhoneVerified());
    }

    @Test
    public void changePasswordSuccess() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture(), eq(null));

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(updatedAccount.getId(), ACCOUNT_ID);
        assertEquals(updatedAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(updatedAccount.getPasswordAlgorithm(), PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        assertEquals(updatedAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertTrue(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(updatedAccount.getPasswordHash(),
                DUMMY_PASSWORD));
    }

    @Test
    public void changePasswordForPhone() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, ChannelType.PHONE, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture(), eq(null));

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertTrue(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void changePasswordAccountNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute
        dao.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
    }

    @Test
    public void changePasswordForExternalId() {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, null, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture(), eq(null));

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test
    public void authenticateSuccessWithHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.email=:email GROUP BY acct.id";
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);
        assertEquals(account.getVersion(), 1); // version not incremented by update

        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We don't create a new health code mapping nor update the account.
        verify(mockHibernateHelper, never()).update(any(), any());
    }

    @Test
    public void authenticateSuccessCreateNewHealthCode() throws Exception {
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);

        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.email=:email GROUP BY acct.id";

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);

        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
        verifyCreatedHealthCode();
    }

    @Test
    public void authenticateSuccessNoReauthentication() throws Exception {
        study.setReauthenticationEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        // not incremented by reauthentication
        assertEquals(account.getVersion(), 1);

        // No reauthentication token rotation occurs
        verify(mockHibernateHelper, never()).update(any(), eq(null));
        assertNull(account.getReauthToken());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false)));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(true)));

        // execute
        dao.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.email=:email GROUP BY acct.id";
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(AccountSecretType.REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                HibernateAccountDao.ROTATIONS)).thenReturn(Optional.of(secret));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.reauthenticate(study, REAUTH_SIGNIN);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        // Version has not been incremented by an update
        assertEquals(account.getVersion(), 1);

        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We update the account twice in this scenario
        verify(mockHibernateHelper, never()).create(any(), any());
        verify(mockHibernateHelper, never()).update(any(), any());

        // verify token verification
        verify(mockAccountSecretDao).verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, REAUTH_TOKEN, 3);
    }

    @Test
    public void reauthenticationDisabled() throws Exception {
        study.setReauthenticationEnabled(false);

        try {
            dao.reauthenticate(study, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).queryGet(any(), any(), any(), any(), eq(HibernateAccount.class));
        verify(mockHibernateHelper, never()).update(any(), eq(null));
    }

    // branch coverage
    @Test
    public void reauthenticationFlagNull() {
        study.setReauthenticationEnabled(null);

        try {
            dao.reauthenticate(study, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).queryGet(any(), any(), any(), any(), eq(HibernateAccount.class));
        verify(mockHibernateHelper, never()).update(any(), eq(null));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void reauthenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setStatus(UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(AccountSecretType.REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                HibernateAccountDao.ROTATIONS)).thenReturn(Optional.of(secret));

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setStatus(DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        AccountSecret secret = AccountSecret.create();
        when(mockAccountSecretDao.verifySecret(AccountSecretType.REAUTH, hibernateAccount.getId(), REAUTH_TOKEN,
                HibernateAccountDao.ROTATIONS)).thenReturn(Optional.of(secret));

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false)));

        // It has no record in the secrets table...

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setStatus(DISABLED);

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("bad password").build();
        dao.authenticate(study, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void reauthenticateBadReauthToken() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getByEmail() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);

        assertEquals(account, hibernateAccount);
    }

    @Test
    public void deleteReauthToken() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);

        verify(mockAccountSecretDao).removeSecrets(AccountSecretType.REAUTH, ACCOUNT_ID);
    }

    @Test
    public void deleteReauthTokenNoToken() throws Exception {
        // Return an account with no reauth token.
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // Just quietly succeeds without doing any account update.
        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);
        verify(mockHibernateHelper, never()).update(any(), eq(null));

        // But we do always call this.
        verify(mockAccountSecretDao).removeSecrets(AccountSecretType.REAUTH, ACCOUNT_ID);
    }

    @Test
    public void deleteReauthTokenAccountNotFound() throws Exception {
        // Just quietly succeeds without doing any work.
        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);

        verify(mockHibernateHelper, never()).update(any(), eq(null));
        verify(mockAccountSecretDao, never()).removeSecrets(AccountSecretType.REAUTH, ACCOUNT_ID);
    }

    @Test
    public void createAccountSuccess() {
        // Study passed into createAccount() takes precedence over StudyId in the Account object. To test this, make
        // the account have a different study.
        Account account = makeValidGenericAccount();
        account.setStatus(ENABLED);
        account.setStudyId("wrong-study");
        account.setId(ACCOUNT_ID);

        // execute - We generate a new account ID.
        dao.createAccount(study, account, null);

        // verify hibernate call
        ArgumentCaptor<HibernateAccount> createdHibernateAccountCaptor = ArgumentCaptor
                .forClass(HibernateAccount.class);
        verify(mockHibernateHelper).create(createdHibernateAccountCaptor.capture(), eq(null));

        HibernateAccount createdHibernateAccount = createdHibernateAccountCaptor.getValue();
        assertEquals(createdHibernateAccount.getId(), ACCOUNT_ID);
        assertEquals(createdHibernateAccount.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createdHibernateAccount.getCreatedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getPasswordModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
        assertEquals(createdHibernateAccount.getStatus(), ENABLED);
        assertEquals(createdHibernateAccount.getMigrationVersion(), MIGRATION_VERSION);
    }

    @Test
    public void updateSuccess() {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(new DateTime(1234L));
        persistedAccount.setPasswordModifiedOn(new DateTime(5678L));
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(new DateTime(5678L));

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        Account account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);

        // Execute. Identifiers not allows to change.
        dao.updateAccount(account, null);

        // verify hibernate update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor
                .forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture(), eq(null));

        HibernateAccount updatedHibernateAccount = updatedHibernateAccountCaptor.getValue();
        assertEquals(updatedHibernateAccount.getId(), ACCOUNT_ID);
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
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setPasswordAlgorithm(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("bad password hash");
        account.setPasswordModifiedOn(MOCK_DATETIME);

        dao.updateAccount(account, null);

        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor
                .forClass(HibernateAccount.class);

        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture(), eq(null));

        // These values were loaded, have not been changed, and were persisted as is.
        HibernateAccount captured = updatedHibernateAccountCaptor.getValue();
        assertEquals(captured.getPasswordAlgorithm(), persistedAccount.getPasswordAlgorithm());
        assertEquals(captured.getPasswordHash(), persistedAccount.getPasswordHash());
        assertEquals(captured.getPasswordModifiedOn(), persistedAccount.getPasswordModifiedOn());
    }

    @Test
    public void updateAccountNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // execute
        try {
            dao.updateAccount(makeValidGenericAccount(), null);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getMessage(), "Account " + ACCOUNT_ID + " not found");
        }
    }

    @Test
    public void updateAccountAllowsIdentifierUpdate() {
        // This call will allow identifiers/verification status to be updated.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(new DateTime(1234L));
        persistedAccount.setPasswordModifiedOn(new DateTime(5678L));
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(new DateTime(5678L));

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        // execute
        Account account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);

        // Identifiers ARE allowed to change here.
        dao.updateAccount(account, null);

        // Capture the update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor
                .forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture(), eq(null));

        HibernateAccount updatedHibernateAccount = updatedHibernateAccountCaptor.getValue();

        assertEquals(updatedHibernateAccount.getEmail(), OTHER_EMAIL);
        assertEquals(updatedHibernateAccount.getPhone().getNationalFormat(), OTHER_PHONE.getNationalFormat());
        assertEquals(updatedHibernateAccount.getEmailVerified(), Boolean.FALSE);
        assertEquals(updatedHibernateAccount.getPhoneVerified(), Boolean.FALSE);
    }

    @Test
    public void getByIdSuccessWithHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = (Account) dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), "original-" + HEALTH_CODE);
        verify(mockHibernateHelper, never()).update(any(), eq(null));
    }

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

    @Test
    public void deleteWithoutId() throws Exception {
        // Can't use email, so it will do a lookup of the account
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.deleteAccount(ACCOUNT_ID_WITH_EMAIL);

        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }

    @Test
    public void deleteWithId() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Directly deletes with the ID it has
        dao.deleteAccount(ACCOUNT_ID_WITH_ID);

        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }

    @Test
    public void getPaged() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, "
                + "acct.firstName, acct.lastName, acct.email, acct.phone, "
                + "acct.id, acct.status, acct.synapseUserId) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId "
                + "WHERE acct.studyId = :studyId GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct "
                + "LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH acct.id = "
                + "acctSubstudy.accountId WHERE acct.studyId = :studyId";
        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");

        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount1, hibernateAccount2));
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(12);

        // Finally, mock the retrieval of substudies to verify this is called to populate the substudies
        List<HibernateAccountSubstudy> list = ImmutableList.of(
                (HibernateAccountSubstudy) AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_A,
                        ACCOUNT_ID),
                (HibernateAccountSubstudy) AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_B,
                        ACCOUNT_ID));
        when(mockHibernateHelper.queryGet(eq("FROM HibernateAccountSubstudy WHERE accountId=:accountId"), any(), any(),
                any(), eq(HibernateAccountSubstudy.class))).thenReturn(list);

        // execute and validate
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5).build();

        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);
        assertEquals(accountSummaryResourceList.getRequestParams().get("offsetBy"), 10);
        assertEquals(accountSummaryResourceList.getRequestParams().get("pageSize"), 5);
        assertEquals(accountSummaryResourceList.getTotal(), (Integer) 12);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("pageSize"), 5);

        // just ID, study, and email is sufficient
        List<AccountSummary> accountSummaryList = accountSummaryResourceList.getItems();
        assertEquals(accountSummaryList.size(), 2);

        assertEquals(accountSummaryList.get(0).getId(), "account-1");
        assertEquals(accountSummaryList.get(0).getStudyIdentifier(), TEST_STUDY);
        assertEquals(accountSummaryList.get(0).getEmail(), "email1@example.com");
        assertEquals(accountSummaryList.get(0).getSubstudyIds(), ImmutableSet.of(SUBSTUDY_A, SUBSTUDY_B));

        assertEquals(accountSummaryList.get(1).getId(), "account-2");
        assertEquals(accountSummaryList.get(1).getStudyIdentifier(), TEST_STUDY);
        assertEquals(accountSummaryList.get(1).getEmail(), "email2@example.com");
        assertEquals(accountSummaryList.get(1).getSubstudyIds(), ImmutableSet.of(SUBSTUDY_A, SUBSTUDY_B));

        // verify hibernate calls
        verify(mockHibernateHelper).queryGet(expQuery, STUDY_QUERY_PARAMS, 10, 5, HibernateAccount.class);
        verify(mockHibernateHelper).queryCount(expCountQuery, STUDY_QUERY_PARAMS);
    }

    @Test
    public void getPagedRemovesSubstudiesNotInCaller() throws Exception {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of(SUBSTUDY_A)).build());

        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false);
        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount1, hibernateAccount2));

        // Finally, mock the retrieval of substudies to verify this is called to populate the substudies
        List<HibernateAccountSubstudy> list = ImmutableList.of(
                (HibernateAccountSubstudy) AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_A,
                        ACCOUNT_ID),
                (HibernateAccountSubstudy) AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_B,
                        ACCOUNT_ID));
        when(mockHibernateHelper.queryGet(eq("FROM HibernateAccountSubstudy WHERE accountId=:accountId"), any(), any(),
                any(), eq(HibernateAccountSubstudy.class))).thenReturn(list);

        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);
        List<AccountSummary> accountSummaryList = accountSummaryResourceList.getItems();

        // substudy B is not there
        assertEquals(accountSummaryList.get(0).getSubstudyIds(), ImmutableSet.of(SUBSTUDY_A));
        assertEquals(accountSummaryList.get(1).getSubstudyIds(), ImmutableSet.of(SUBSTUDY_A));
    }

    @Test
    public void getPagedWithOptionalParams() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, acct.firstName, "
                + "acct.lastName, acct.email, acct.phone, acct.id, acct.status, acct.synapseUserId) FROM "
                + "HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH "
                + "acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND acct.email LIKE "
                + ":email AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND acct.createdOn "
                + "<= :endTime AND :language IN ELEMENTS(acct.languages) AND (:IN1 IN elements(acct.dataGroups) "
                + "AND :IN2 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND "
                + ":NOTIN2 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE "
                + "acct.studyId = :studyId AND acct.email LIKE :email AND acct.phone.number LIKE :number AND "
                + "acct.createdOn >= :startTime AND acct.createdOn <= :endTime AND :language IN "
                + "ELEMENTS(acct.languages) AND (:IN1 IN elements(acct.dataGroups) AND :IN2 IN "
                + "elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT "
                + "IN elements(acct.dataGroups))";

        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false)));
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5)
                .withEmailFilter(EMAIL).withPhoneFilter(PHONE.getNationalFormat())
                .withAllOfGroups(Sets.newHashSet("a", "b")).withNoneOfGroups(Sets.newHashSet("c", "d"))
                .withLanguage("de").withStartTime(startDate).withEndTime(endDate).build();

        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.size(), 10);
        assertEquals(paramsMap.get("pageSize"), 5);
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("emailFilter"), EMAIL);
        assertEquals(paramsMap.get("phoneFilter"), PHONE.getNationalFormat());
        assertEquals(paramsMap.get("startTime"), startDate.toString());
        assertEquals(paramsMap.get("endTime"), endDate.toString());
        assertEquals(paramsMap.get("allOfGroups"), Sets.newHashSet("a", "b"));
        assertEquals(paramsMap.get("noneOfGroups"), Sets.newHashSet("c", "d"));
        assertEquals(paramsMap.get("language"), "de");
        assertEquals(paramsMap.get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*", "");

        // verify hibernate calls
        Map<String, Object> params = new HashMap<>();
        params.put("studyId", TEST_STUDY_IDENTIFIER);
        params.put("email", "%" + EMAIL + "%");
        params.put("number", "%" + phoneString + "%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("in1", "a");
        params.put("in2", "b");
        params.put("notin1", "c");
        params.put("notin2", "d");
        params.put("language", "de");

        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5),
                eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());

        Map<String, Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(capturedParams.get("studyId"), TEST_STUDY_IDENTIFIER);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("IN1"), "a");
        assertEquals(capturedParams.get("IN2"), "b");
        assertEquals(capturedParams.get("NOTIN1"), "d");
        assertEquals(capturedParams.get("NOTIN2"), "c");
        assertEquals(capturedParams.get("language"), "de");

        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(capturedParams.get("studyId"), TEST_STUDY_IDENTIFIER);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("IN1"), "a");
        assertEquals(capturedParams.get("IN2"), "b");
        assertEquals(capturedParams.get("NOTIN1"), "d");
        assertEquals(capturedParams.get("NOTIN2"), "c");
        assertEquals(capturedParams.get("language"), "de");
    }

    @Test
    public void getPagedWithOptionalParamsRespectsSubstudy() throws Exception {
        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE "
                + "acct.studyId = :studyId AND acctSubstudy.substudyId IN (:substudies)";
        Set<String> substudyIds = ImmutableSet.of("substudyA", "substudyB");
        try {
            RequestContext context = new RequestContext.Builder().withCallerSubstudies(substudyIds).build();
            BridgeUtils.setRequestContext(context);

            AccountSummarySearch search = new AccountSummarySearch.Builder().build();
            dao.getPagedAccountSummaries(study, search);

            verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());
            Map<String, Object> params = paramCaptor.getValue();
            assertEquals(params.get("substudies"), substudyIds);
            assertEquals(params.get("studyId"), TEST_STUDY_IDENTIFIER);
        } finally {
            BridgeUtils.setRequestContext(null);
        }
    }

    @Test
    public void getPagedWithOptionalEmptySetParams() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, acct.firstName, "
                + "acct.lastName, acct.email, acct.phone, acct.id, acct.status, acct.synapseUserId) FROM "
                + "HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH "
                + "acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND acct.email LIKE "
                + ":email AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND "
                + "acct.createdOn <= :endTime AND :language IN ELEMENTS(acct.languages) GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE "
                + "acct.studyId = :studyId AND acct.email LIKE :email AND acct.phone.number LIKE "
                + ":number AND acct.createdOn >= :startTime AND acct.createdOn <= :endTime AND :language "
                + "IN ELEMENTS(acct.languages)";

        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false)));
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5)
                .withEmailFilter(EMAIL).withPhoneFilter(PHONE.getNationalFormat()).withLanguage("de")
                .withStartTime(startDate).withEndTime(endDate).build();
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.size(), 10);
        assertEquals(paramsMap.get("pageSize"), 5);
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("emailFilter"), EMAIL);
        assertEquals(paramsMap.get("phoneFilter"), PHONE.getNationalFormat());
        assertEquals(paramsMap.get("startTime"), startDate.toString());
        assertEquals(paramsMap.get("endTime"), endDate.toString());
        assertEquals(paramsMap.get("allOfGroups"), Sets.newHashSet());
        assertEquals(paramsMap.get("noneOfGroups"), Sets.newHashSet());
        assertEquals(paramsMap.get("language"), "de");
        assertEquals(paramsMap.get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*", "");

        // verify hibernate calls
        Map<String, Object> params = new HashMap<>();
        params.put("studyId", TEST_STUDY);
        params.put("email", "%" + EMAIL + "%");
        params.put("number", "%" + phoneString + "%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("language", "de");

        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5),
                eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());

        Map<String, Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(capturedParams.get("studyId"), TEST_STUDY_IDENTIFIER);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("language"), "de");

        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(capturedParams.get("studyId"), TEST_STUDY_IDENTIFIER);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("language"), "de");
    }

    @Test
    public void unmarshallAccountSummarySuccess() {
        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setPhone(PHONE);
        hibernateAccount.setFirstName(FIRST_NAME);
        hibernateAccount.setLastName(LAST_NAME);
        hibernateAccount.setCreatedOn(CREATED_ON);
        hibernateAccount.setStatus(ENABLED);

        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyA", ACCOUNT_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyB", ACCOUNT_ID);
        as2.setExternalId("externalIdB");

        when(mockHibernateHelper.queryGet("FROM HibernateAccountSubstudy WHERE accountId=:accountId",
                ImmutableMap.of("accountId", hibernateAccount.getId()), null, null, HibernateAccountSubstudy.class))
                        .thenReturn(ImmutableList.of(as1, as2));

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
        assertEquals(accountSummary.getStudyIdentifier(), TEST_STUDY);
        assertEquals(accountSummary.getEmail(), EMAIL);
        assertEquals(accountSummary.getPhone(), PHONE);
        assertEquals(accountSummary.getExternalIds(), ImmutableMap.of("substudyA", "externalIdA", "substudyB", "externalIdB"));
        assertEquals(accountSummary.getFirstName(), FIRST_NAME);
        assertEquals(accountSummary.getLastName(), LAST_NAME);
        assertEquals(accountSummary.getStatus(), ENABLED);

        // createdOn is stored as a long, so just compare epoch milliseconds.
        assertEquals(accountSummary.getCreatedOn().getMillis(), CREATED_ON.getMillis());
    }

    // branch coverage, to make sure nothing crashes.
    @Test
    public void unmarshallAccountSummaryBlankAccount() throws Exception {
        AccountSummary accountSummary = dao.unmarshallAccountSummary(new HibernateAccount());
        assertNotNull(accountSummary);
    }

    @Test
    public void unmarshallAccountSummaryFiltersSubstudies() throws Exception {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB", "substudyC")).build());

        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        hibernateAccount.setStatus(ENABLED);

        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyA", ACCOUNT_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyB", ACCOUNT_ID);
        as2.setExternalId("externalIdB");

        when(mockHibernateHelper.queryGet("FROM HibernateAccountSubstudy WHERE accountId=:accountId",
                ImmutableMap.of("accountId", hibernateAccount.getId()), null, null, HibernateAccountSubstudy.class))
                        .thenReturn(ImmutableList.of(as1, as2));

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getExternalIds(), ImmutableMap.of("substudyB", "externalIdB"));
        assertEquals(accountSummary.getSubstudyIds(), ImmutableSet.of("substudyB"));
    }

    @Test
    public void unmarshallAccountSummaryStillReturnsOldExternalId() throws Exception {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB", "substudyC")).build());

        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        hibernateAccount.setStatus(ENABLED);

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
    }

    @Test
    public void editAccountSuccess() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.healthCode=:healthCode GROUP BY acct.id";

        // Spy this to verify that the editor lambda is called
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode("A");
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate
        dao.editAccount(TEST_STUDY, HEALTH_CODE, accountConsumer);

        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        InOrder inOrder = Mockito.inOrder(accountConsumer, mockHibernateHelper);
        inOrder.verify(accountConsumer).accept(hibernateAccount);
        inOrder.verify(mockHibernateHelper).update(updatedAccountCaptor.capture(), eq(null));
    }

    @Test
    public void editAccountWhenAccountNotFound() throws Exception {
        dao.editAccount(TEST_STUDY, "bad-health-code", account -> account.setEmail("JUNK"));

        verify(accountConsumer, never()).accept(any());
        verify(mockHibernateHelper, never()).update(any(), eq(null));
    }

    @Test
    public void noLanguageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "
                + "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_IDENTIFIER);
    }

    @Test
    public void languageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "
                + "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + ":language IN ELEMENTS(acct.languages) GROUP BY acct.id";
        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_IDENTIFIER);
        assertEquals(builder.getParameters().get("language"), "en");
    }

    @Test
    public void groupClausesGroupedCorrectly() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("sdk-int-1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), "api");
    }

    @Test
    public void oneAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withAllOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), "api");
    }

    @Test
    public void twoAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN2"), "group1");
        assertEquals(builder.getParameters().get("studyId"), "api");
    }

    @Test
    public void oneNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), "api");
    }

    @Test
    public void twoNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TEST_STUDY_IDENTIFIER, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("NOTIN2"), "group1");
        assertEquals(builder.getParameters().get("studyId"), "api");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedEmailFails() throws Exception {
        study.setVerifyChannelOnSignInEnabled(true);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setEmailVerified(false);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void authenticateAccountUnverifiedPhoneFails() throws Exception {
        study.setVerifyChannelOnSignInEnabled(true);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setPhoneVerified(null);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();

        dao.authenticate(study, phoneSignIn);
    }

    @Test
    public void authenticateAccountEmailUnverifiedWithoutEmailVerificationOK() throws Exception {
        study.setEmailVerificationEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setEmailVerified(false);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test
    public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
        study.setVerifyChannelOnSignInEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setEmailVerified(false);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test
    public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
        study.setVerifyChannelOnSignInEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true);
        hibernateAccount.setPhoneVerified(null);

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(PHONE)
                .withPassword(DUMMY_PASSWORD).build();

        dao.authenticate(study, phoneSignIn);
    }

    @Test
    public void editAccountFailsAcrossSubstudies() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(CALLER_SUBSTUDIES).build());

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setAccountSubstudies(ACCOUNT_SUBSTUDIES);
        when(mockHibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateAccount.class)))
                .thenReturn(Lists.newArrayList(hibernateAccount));

        dao.editAccount(TEST_STUDY, HEALTH_CODE, (account) -> {
            fail("Should have thrown exception");
        });

        verify(mockHibernateHelper, never()).update(any(), eq(null));
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getStudyIdsForUser() throws Exception {
        List<String> queryResult = ImmutableList.of("studyA", "studyB");
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(String.class))).thenReturn(queryResult);
        
        List<String> results = dao.getStudyIdsForUser(SYNAPSE_USER_ID);
        assertEquals(results, queryResult);
        
        verify(mockHibernateHelper).queryGet(eq("SELECT DISTINCT acct.studyId FROM HibernateAccount AS acct WHERE "+
                "synapseUserId = :synapseUserId"), paramCaptor.capture(), eq(null), eq(null), eq(String.class));
        Map<String,Object> params = paramCaptor.getValue();
        assertEquals(params.get("synapseUserId"), SYNAPSE_USER_ID);
    }
    
    @Test
    public void getStudyIdsForUserNoSynapseUserId() throws Exception {
        List<String> results = dao.getStudyIdsForUser(null);
        assertTrue(results.isEmpty());
    }

    private void verifyCreatedHealthCode() {
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture(), eq(null));

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(updatedAccount.getId(), ACCOUNT_ID);
        assertEquals(updatedAccount.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
    }

    // Create minimal generic account for everything that will be used by HibernateAccountDao.
    private static Account makeValidGenericAccount() {
        Account genericAccount = Account.create();
        genericAccount.setId(ACCOUNT_ID);
        genericAccount.setStudyId(TEST_STUDY_IDENTIFIER);
        genericAccount.setEmail(EMAIL);
        genericAccount.setStatus(UNVERIFIED);
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
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
