package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.getElement;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.STORMPATH_HMAC_SHA_256;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
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
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class AccountServiceTest extends Mockito {

    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_APP_ID, EMAIL);
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593Z");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";
    private static final String OTHER_USER_ID = "other-user-id";
    private static final String OTHER_CLIENT_TIME_ZONE = "Africa/Sao_Tome";
    
    private static final String STUDY_A = "studyA";
    private static final String STUDY_B = "studyB";
    private static final Set<Enrollment> ACCOUNT_ENROLLMENTS = ImmutableSet
            .of(Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID));

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
    CacheProvider mockCacheProvider;

    @Mock
    AccountSecret mockSecret;

    @Mock
    Consumer<Account> mockConsumer;

    @Mock
    ParticipantVersionService mockParticipantVersionService;

    @InjectMocks
    @Spy
    AccountService service;

    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;

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
    public void createAccount() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);
        account.setAppId("wrong-app");
        account.setNote(TEST_NOTE);
        account.setClientTimeZone(TEST_CLIENT_TIME_ZONE);

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
        assertEquals(createdAccount.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        // This was not set because the caller is not definitively a dev account.
        assertEquals(createdAccount.getDataGroups(), ImmutableSet.of());
        
        verify(mockCacheProvider).setObject(CacheKey.etag(DateTimeZone.class, TEST_USER_ID), MOCK_DATETIME);

        // Verify we also create a participant version.
        verify(mockParticipantVersionService).createParticipantVersionFromAccount(same(createdAccount));
    }
    
    @Test
    public void createAccountByDevCreatesTestAccount() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        service.createAccount(app, account);
        verify(mockAccountDao).createAccount(eq(app), accountCaptor.capture());
        
        Account createdAccount = accountCaptor.getValue();
        assertEquals(createdAccount.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test
    public void createAccountByStudyDesignerCreatesTestAccount() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        service.createAccount(app, account);
        verify(mockAccountDao).createAccount(eq(app), accountCaptor.capture());
        
        Account createdAccount = accountCaptor.getValue();
        assertEquals(createdAccount.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }

    @Test
    public void updateAccount() throws Exception {
        mockGetAccountById(ACCOUNT_ID, false);
        
        Account updated = Account.create();
        updated.setAppId(TEST_APP_ID);
        updated.setId(TEST_USER_ID);
        updated.setClientTimeZone("America/Los_Angeles");

        service.updateAccount(updated);
        
        verify(mockAccountDao).updateAccount(updated);
        
        verify(mockCacheProvider).setObject(CacheKey.etag(DateTimeZone.class, TEST_USER_ID), MOCK_DATETIME);

        // Verify we also create a participant version.
        verify(mockParticipantVersionService).createParticipantVersionFromAccount(same(updated));
    }
    
    @Test
    public void updateAccount_noTimeZoneUpdate() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);

        service.updateAccount(account);
        
        verify(mockCacheProvider, never()).setObject(any(), any());
    }
    
    @Test
    public void updateAccountDevCannotRemoveTestFlag() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        // mockGetAccountById() returns the account that is returned from persistence,
        // so to remove the flag you need to create a different account without it
        Account updatedAccount = Account.create();
        updatedAccount.setAppId(TEST_APP_ID);
        updatedAccount.setId(TEST_USER_ID);
        // no test flag

        service.updateAccount(updatedAccount);
        
        // nevertheless it remains
        verify(mockAccountDao).updateAccount(accountCaptor.capture());
        assertEquals(accountCaptor.getValue().getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test
    public void updateAccountSucceedsForDevUpdatingTestAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));

        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
    }
    
    @Test
    public void updateAccountSucceedsForDevUpdatingSelfAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        // not a developer account, but the same user ID
        Account account = mockGetAccountById(ACCOUNT_ID, false);

        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
    }
    
    @Test
    public void updateAccountCannotRemoveTestAccountFlag() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        // not a developer account, but the same user ID
        Account persistedAccount = mockGetAccountById(ACCOUNT_ID, false);
        persistedAccount.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));

        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        // no data groups.
        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
        
        // test flag is restored
        assertEquals(account.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test
    public void updateAccountSucceedsForStudyDesignerUpdatingTestAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));

        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
    }
    
    @Test
    public void updateAccountSucceedsForOrgAdminUpdatingAdminAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setRoles(ImmutableSet.of(STUDY_DESIGNER));

        service.updateAccount(account);
        
        verify(mockAccountDao).updateAccount(account);
    }
    
    @Test
    public void updateAccountNotFound() throws Exception {
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
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        
        // This particular edit updates the time zone, so we reset the etag
        service.editAccount(ACCOUNT_ID, (acct) -> acct.setClientTimeZone("America/Los_Angeles"));

        verify(mockCacheProvider).setObject(CacheKey.etag(DateTimeZone.class, TEST_USER_ID), MOCK_DATETIME);
        // Verify we also create a participant version.
        verify(mockParticipantVersionService).createParticipantVersionFromAccount(same(account));
    }

    @Test
    public void editAccount_noTimeZoneUpdate() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        
        // This edit does not change the default time zone (null), so no etag update.
        service.editAccount(ACCOUNT_ID, mockConsumer);
        
        verify(mockConsumer).accept(account);
        verify(mockCacheProvider, never()).setObject(any(), any());
        // Verify we also create a participant version.
        verify(mockParticipantVersionService).createParticipantVersionFromAccount(same(account));
    }
    
    @Test
    public void editAccountWhenAccountNotFound() throws Exception {
        try {
            AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, "bad-health-code");
            service.editAccount(accountId, mockConsumer);    
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockConsumer, never()).accept(any());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void editAccountFailsForDevelopersOperatingOnProdAccounts() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminId")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        mockGetAccountById(ACCOUNT_ID, false);

        service.editAccount(ACCOUNT_ID, mockConsumer);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void editAccountFailsForStudyDesignerOperatingOnProdAccounts() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminId")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        mockGetAccountById(ACCOUNT_ID, false);

        service.editAccount(ACCOUNT_ID, mockConsumer);
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
        verify(mockCacheProvider).removeObject(CacheKey.etag(DateTimeZone.class, TEST_USER_ID));
    }
    
    @Test
    public void deleteAccountNotFound() {
        service.deleteAccount(ACCOUNT_ID);
        verify(mockAccountDao, never()).deleteAccount(any());
        verify(mockCacheProvider, never()).removeObject(any());
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
        verify(studyActivityEventService, never()).publishEvent(any(), anyBoolean(), anyBoolean());
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
        verify(studyActivityEventService, times(2)).publishEvent(eventCaptor.capture(), eq(false), eq(true));

        StudyActivityEvent event1 = getElement(
                eventCaptor.getAllValues(), StudyActivityEvent::getStudyId, STUDY_A).orElse(null);
        assertNotNull(event1);
        assertEquals(event1.getAppId(), TEST_APP_ID);
        assertEquals(event1.getStudyId(), STUDY_A);
        assertEquals(event1.getUserId(), TEST_USER_ID);
        assertEquals(event1.getEventId(), "enrollment");
        assertEquals(event1.getTimestamp(), account.getCreatedOn());

        StudyActivityEvent event2 = getElement(
                eventCaptor.getAllValues(), StudyActivityEvent::getStudyId, STUDY_B).orElse(null);
        assertNotNull(event2);
        assertEquals(event2.getAppId(), TEST_APP_ID);
        assertEquals(event2.getStudyId(), STUDY_B);
        assertEquals(event2.getUserId(), TEST_USER_ID);
        assertEquals(event2.getEventId(), "enrollment");
        assertEquals(event2.getTimestamp(), account.getCreatedOn());
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
        persistedAccount.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        
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
        account.setClientTimeZone(OTHER_CLIENT_TIME_ZONE);

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
        assertEquals(updatedAccount.getClientTimeZone(), OTHER_CLIENT_TIME_ZONE);
        
        verify(activityEventService, never()).publishEnrollmentEvent(any(), any(), any());
        verify(studyActivityEventService, never()).publishEvent(any(), anyBoolean(), anyBoolean());
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
        verify(studyActivityEventService).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), STUDY_B);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getTimestamp(), MOCK_DATETIME);
        assertEquals(event.getEventId(), "enrollment");
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

    // The editAccountFailsAcrossStudies test was removed because editAccount no longer enforces 
    // authorization checks. It's intended to be used internally, not as a result of a direct
    // operation by an API caller.
    
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

        Optional<Account> account = service.getAccount(ACCOUNT_ID);
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

    @Test
    public void deleteAllAccounts() { 
        service.deleteAllAccounts(TEST_APP_ID);
        verify(mockAccountDao).deleteAllAccounts(TEST_APP_ID);
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
