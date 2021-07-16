package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.hibernate.HibernateAccountDao.FULL_QUERY;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.StringSearchPosition.INFIX;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.WITHDRAWN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

public class HibernateAccountDaoTest extends Mockito {
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
    private static final String EXTERNAL_ID = "an-external-id";
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_APP_ID, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_APP_ID, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_APP_ID, PHONE);
    private static final AccountId ACCOUNT_ID_WITH_HEALTHCODE = AccountId.forHealthCode(TEST_APP_ID,
            HEALTH_CODE);
    private static final AccountId ACCOUNT_ID_WITH_EXTID = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
    private static final AccountId ACCOUNT_ID_WITH_SYNID = AccountId.forSynapseUserId(TEST_APP_ID,
            SYNAPSE_USER_ID);

    private static final String STUDY_A = "studyA";
    private static final String STUDY_B = "studyB";
    private static final Map<String, Object> APP_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("orgId", TEST_ORG_ID).build();
    private static final Map<String, Object> EMAIL_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("email", EMAIL).build();
    private static final Map<String, Object> HEALTHCODE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("healthCode", HEALTH_CODE).build();
    private static final Map<String, Object> PHONE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("number", PHONE.getNumber())
            .put("regionCode", PHONE.getRegionCode()).build();
    private static final Map<String, Object> EXTID_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("externalId", EXTERNAL_ID).build();
    private static final Map<String, Object> SYNAPSE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("appId", TEST_APP_ID).put("synapseUserId", SYNAPSE_USER_ID).build();

    private static final String EXTID_FULL_QUERY = "SELECT en from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL AND en.externalId LIKE :idFilter ORDER BY en.externalId";
    private static final String EXTID_FULL_COUNT_QUERY = "SELECT count(en) from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL AND en.externalId LIKE :idFilter ORDER BY en.externalId";
    private static final String EXTID_QUERY = "SELECT en from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL ORDER BY en.externalId";
    private static final String EXTID_COUNT_QUERY = "SELECT count(en) from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL ORDER BY en.externalId";

    @Captor
    ArgumentCaptor<Map<String, Object>> paramCaptor;

    @Mock
    Consumer<Account> accountConsumer;

    @Mock
    private HibernateHelper mockHibernateHelper;

    private App app;
    
    @InjectMocks
    @Spy
    private HibernateAccountDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        DateTimeUtils.setCurrentMillisFixed(MOCK_DATETIME.getMillis());
        // Mock successful update.
        when(mockHibernateHelper.update(any())).thenAnswer(invocation -> {
            HibernateAccount account = invocation.getArgument(0);
            if (account != null) {
                account.setVersion(account.getVersion() + 1);
            }
            return account;
        });

        dao = spy(new HibernateAccountDao());
        dao.setHibernateHelper(mockHibernateHelper);

        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setReauthenticationEnabled(true);
        app.setEmailVerificationEnabled(true);
    }

    @AfterMethod
    public void after() {
        RequestContext.set(null);
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void getByEmail() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL).get();

        assertEquals(account, hibernateAccount);
    }

    @Test
    public void createAccountSuccess() {
        Account account = makeValidGenericAccount();

        // execute - We generate a new account ID.
        dao.createAccount(app, account);
        
        verify(mockHibernateHelper).create(account);
    }

    @Test
    public void updateSuccess() {
        Account account = Account.create();
        
        dao.updateAccount(account);
        
        verify(mockHibernateHelper).update(account);
    }

    @Test
    public void updateAccountAllowsIdentifierUpdate() {
        // This call will allow identifiers/verification status to be updated.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setAppId("persisted-app");
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
        dao.updateAccount(account);

        // Capture the update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor
                .forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture());

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
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), eq(ACCOUNT_ID))).thenReturn(hibernateAccount);

        // execute and validate - just validate ID, app, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), "original-" + HEALTH_CODE);
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByIdSuccessCreateNewHealthCode() throws Exception {
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), eq(ACCOUNT_ID))).thenReturn(hibernateAccount);
        when(mockHibernateHelper.update(any())).thenReturn(hibernateAccount);
        
        // execute and validate - just validate ID, app, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
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
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertFalse(opt.isPresent());
    }

    @Test
    public void getByIdWrongApp() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode(null);
        hibernateAccount.setAppId(TEST_APP_ID);
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), eq(ACCOUNT_ID))).thenReturn(hibernateAccount);

        // execute and validate
        AccountId wrongApp = AccountId.forId("wrong-app", ACCOUNT_ID);
        Optional<Account> opt = dao.getAccount(wrongApp);
        assertFalse(opt.isPresent());
        
        verify(mockHibernateHelper).getById(eq(HibernateAccount.class), eq(wrongApp.getUnguardedAccountId().getId()));
    }
    
    @Test
    public void getByEmailSuccessWithHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "acct.email=:email GROUP BY acct.id";

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, app, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), "original-" + HEALTH_CODE);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(eq(expQuery), eq(EMAIL_QUERY_PARAMS), isNull(), isNull(),
                eq(HibernateAccount.class));

        // We don't create a new health code mapping nor update the account.
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByEmailSuccessCreateNewHealthCode() throws Exception {
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);

        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                + "WHERE acct.appId = :appId AND acct.email=:email GROUP BY acct.id";

        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        when(mockHibernateHelper.update(any())).thenReturn(hibernateAccount);
        
        // execute and validate - just validate ID, app, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getHealthCode(), HEALTH_CODE);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(eq(expQuery), eq(EMAIL_QUERY_PARAMS), isNull(), isNull(), eq(HibernateAccount.class));
        
        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByEmailNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute and validate
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertFalse(opt.isPresent());
    }

    @Test
    public void getByPhone() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "acct.phone.number=:number AND acct.phone.regionCode=:regionCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), eq(PHONE_QUERY_PARAMS), isNull(), isNull(),
                eq(HibernateAccount.class))).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE).get();
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByPhoneNotFound() {
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertFalse(opt.isPresent());
    }
    
    @Test
    public void getSynapseUserId() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND " 
                + "acct.synapseUserId=:synapseUserId GROUP BY acct.id"; 
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), eq(SYNAPSE_QUERY_PARAMS), isNull(), isNull(),
                eq(HibernateAccount.class))).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_SYNID).get();
        assertEquals(account.getId(), ACCOUNT_ID);
    }
    
    @Test
    public void getSynapseUserIdNotFound() {
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_SYNID);
        assertFalse(opt.isPresent());
    }

    // ACCOUNT_ID_WITH_HEALTHCODE
    @Test
    public void getByHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments AS "
                + "enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "acct.healthCode=:healthCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), eq(HEALTHCODE_QUERY_PARAMS), isNull(), isNull(),
                eq(HibernateAccount.class))).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE).get();
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByHealthCodeNotFound() {
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE);
        assertFalse(opt.isPresent());
    }

    // ACCOUNT_ID_WITH_EXTID
    @Test
    public void getByExternalId() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments AS "
                + "enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId "
                + "AND enrollment.externalId=:externalId GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), eq(EXTID_QUERY_PARAMS), isNull(), isNull(),
                eq(HibernateAccount.class))).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EXTID).get();
        assertEquals(account.getEmail(), hibernateAccount.getEmail());
    }

    @Test
    public void getByExternalIdNotFound() {
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_EXTID);
        assertFalse(opt.isPresent());
    }

    @Test
    public void deleteWithId() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Directly deletes with the ID it has
        dao.deleteAccount(ACCOUNT_ID);

        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }

    @Test
    public void getPaged() throws Exception {
        // Researchers can see all the studies the summaries are associated to.
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        String expQuery = "SELECT acct.id FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                + "WHERE acct.appId = :appId AND size(acct.roles) > 0 AND acct.orgMembership "
                + "= :orgId GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct "
                + "LEFT JOIN acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                +"WHERE acct.appId = :appId AND size(acct.roles) > 0 AND acct.orgMembership = :orgId";
        
        Set<Enrollment> set = ImmutableSet.of(
                Enrollment.create(TEST_APP_ID, STUDY_A, ACCOUNT_ID),
                Enrollment.create(TEST_APP_ID, STUDY_B, ACCOUNT_ID));

        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");
        hibernateAccount1.setEnrollments(set);

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");
        hibernateAccount2.setEnrollments(set);

        when(mockHibernateHelper.queryGet(expQuery, APP_QUERY_PARAMS, 10, 5, String.class))
                .thenReturn(ImmutableList.of("account-1", "account-2"));
        when(mockHibernateHelper.getById(HibernateAccount.class, "account-1")).thenReturn(hibernateAccount1);        
        when(mockHibernateHelper.getById(HibernateAccount.class, "account-2")).thenReturn(hibernateAccount2);
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(12);

        // execute and validate
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10)
                .withAdminOnly(true).withOrgMembership(TEST_ORG_ID).withPageSize(5).build();

        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(TEST_APP_ID, search);
        assertEquals(accountSummaryResourceList.getRequestParams().get("offsetBy"), 10);
        assertEquals(accountSummaryResourceList.getRequestParams().get("pageSize"), 5);
        assertEquals(accountSummaryResourceList.getRequestParams().get("orgMembership"), TEST_ORG_ID);
        assertTrue((Boolean)accountSummaryResourceList.getRequestParams().get("adminOnly"));
        assertEquals(accountSummaryResourceList.getTotal(), (Integer) 12);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("pageSize"), 5);
        assertEquals(paramsMap.get("adminOnly"), Boolean.TRUE);
        assertEquals(paramsMap.get("orgMembership"), TEST_ORG_ID);

        // just ID, app, and email is sufficient
        List<AccountSummary> accountSummaryList = accountSummaryResourceList.getItems();
        assertEquals(accountSummaryList.size(), 2);

        assertEquals(accountSummaryList.get(0).getId(), "account-1");
        assertEquals(accountSummaryList.get(0).getAppId(), TEST_APP_ID);
        assertEquals(accountSummaryList.get(0).getEmail(), "email1@example.com");
        assertEquals(accountSummaryList.get(0).getStudyIds(), ImmutableSet.of(STUDY_A, STUDY_B));

        assertEquals(accountSummaryList.get(1).getId(), "account-2");
        assertEquals(accountSummaryList.get(1).getAppId(), TEST_APP_ID);
        assertEquals(accountSummaryList.get(1).getEmail(), "email2@example.com");
        assertEquals(accountSummaryList.get(1).getStudyIds(), ImmutableSet.of(STUDY_A, STUDY_B));

        // verify hibernate calls
        verify(mockHibernateHelper).queryGet(eq(expQuery), eq(APP_QUERY_PARAMS), eq(10), eq(5), eq(String.class));
        verify(mockHibernateHelper).getById(HibernateAccount.class, "account-1");
        verify(mockHibernateHelper).getById(HibernateAccount.class, "account-2");
        verify(mockHibernateHelper).queryCount(expCountQuery, APP_QUERY_PARAMS);
    }
    
    @Test
    public void getPagedReturnsQueryParams() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withAdminOnly(true)
                .withOrgMembership(TEST_ORG_ID)
                .withPageSize(5)
                .withEmailFilter("emailFilter")
                .withPhoneFilter("phoneFilter")
                .withAllOfGroups(ImmutableSet.of("all"))
                .withNoneOfGroups(ImmutableSet.of("none"))
                .withLanguage("en")
                .withStartTime(CREATED_ON)
                .withEndTime(MODIFIED_ON)
                .withEnrolledInStudyId(TEST_STUDY_ID)
                .withExternalIdFilter("externalIdFilter")
                .withStatus(AccountStatus.DISABLED)
                .withEnrollment(EnrollmentFilter.WITHDRAWN)
                .withAttributeKey("key")
                .withAttributeValueFilter("value")
                .build();

        PagedResourceList<AccountSummary> retValue = dao.getPagedAccountSummaries(TEST_APP_ID, search);
        
        Map<String, Object> rp = retValue.getRequestParams();
        assertEquals(rp.get("offsetBy"), 10);
        assertEquals(rp.get("pageSize"), 5);
        assertEquals(rp.get("orgMembership"), TEST_ORG_ID);
        assertTrue((Boolean)rp.get("adminOnly"));
        assertEquals(rp.get("emailFilter"), "emailFilter");
        assertEquals(rp.get("phoneFilter"), "phoneFilter");
        assertEquals(rp.get("allOfGroups"), ImmutableSet.of("all"));
        assertEquals(rp.get("noneOfGroups"), ImmutableSet.of("none"));
        assertEquals(rp.get("language"), "en");
        assertEquals(rp.get("startTime"), CREATED_ON.toString());
        assertEquals(rp.get("endTime"), MODIFIED_ON.toString());
        assertEquals(rp.get("enrolledInStudyId"), TEST_STUDY_ID);
        assertEquals(rp.get("externalIdFilter"), "externalIdFilter");
        assertEquals(rp.get("status"), AccountStatus.DISABLED);
        assertEquals(rp.get("enrollment"), EnrollmentFilter.WITHDRAWN);
        assertEquals(rp.get("attributeKey"), "key");
        assertEquals(rp.get("attributeValueFilter"), "value");
    }

    @Test
    public void getPagedRemovesStudiesNotInCaller() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID) // it's not the same as ACCOUNT_ID
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(STUDY_A)).build());
        
        Set<Enrollment> set = ImmutableSet.of(
                Enrollment.create(TEST_APP_ID, STUDY_A, ACCOUNT_ID),
                Enrollment.create(TEST_APP_ID, STUDY_B, ACCOUNT_ID));

        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEnrollments(set);
        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEnrollments(set);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of("account-1", "account-2"));
        when(mockHibernateHelper.getById(HibernateAccount.class, "account-1")).thenReturn(hibernateAccount1);
        when(mockHibernateHelper.getById(HibernateAccount.class, "account-2")).thenReturn(hibernateAccount2);

        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(TEST_APP_ID, search);
        List<AccountSummary> accountSummaryList = accountSummaryResourceList.getItems();

        // study B is not there
        assertEquals(accountSummaryList.get(0).getStudyIds(), ImmutableSet.of(STUDY_A));
        assertEquals(accountSummaryList.get(1).getStudyIds(), ImmutableSet.of(STUDY_A));
    }

    @Test
    public void getPagedWithOptionalParams() throws Exception {
        String expQuery = "SELECT acct.id FROM HibernateAccount AS acct LEFT JOIN acct.enrollments AS "
                + "enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId "
                + "AND size(acct.roles) > 0 AND acct.orgMembership = :orgId AND acct.email LIKE :email "
                + "AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND acct.createdOn "
                + "<= :endTime AND :language IN ELEMENTS(acct.languages) AND (:IN1 IN elements(acct.dataGroups) "
                + "AND :IN2 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) "
                + "AND :NOTIN2 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.enrollments AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = "
                + ":appId AND size(acct.roles) > 0 AND acct.orgMembership = :orgId AND acct.email LIKE "
                + ":email AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND "
                + "acct.createdOn <= :endTime AND :language IN ELEMENTS(acct.languages) AND (:IN1 IN "
                + "elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN "
                + "elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups))";

        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(ACCOUNT_ID));
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID))
                .thenReturn(makeValidHibernateAccount(false));
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5)
                .withEmailFilter(EMAIL).withPhoneFilter(PHONE.getNationalFormat())
                .withAllOfGroups(Sets.newHashSet("a", "b")).withNoneOfGroups(Sets.newHashSet("c", "d"))
                .withLanguage("de").withStartTime(startDate).withEndTime(endDate).withAdminOnly(true)
                .withOrgMembership(TEST_ORG_ID).build();

        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(TEST_APP_ID, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.size(), 14);
        assertEquals(paramsMap.get("pageSize"), 5);
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("emailFilter"), EMAIL);
        assertEquals(paramsMap.get("phoneFilter"), PHONE.getNationalFormat());
        assertEquals(paramsMap.get("startTime"), startDate.toString());
        assertEquals(paramsMap.get("endTime"), endDate.toString());
        assertEquals(paramsMap.get("allOfGroups"), Sets.newHashSet("a", "b"));
        assertEquals(paramsMap.get("noneOfGroups"), Sets.newHashSet("c", "d"));
        assertEquals(paramsMap.get("language"), "de");
        assertEquals(paramsMap.get("orgMembership"), TEST_ORG_ID);
        assertEquals(paramsMap.get("stringSearchPosition"), INFIX);
        assertEquals(paramsMap.get("predicate"), AND);
        assertTrue((Boolean)paramsMap.get("adminOnly"));
        assertEquals(paramsMap.get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*", "");

        // verify hibernate calls
        Map<String, Object> params = new HashMap<>();
        params.put("appId", TEST_APP_ID);
        params.put("email", "%" + EMAIL + "%");
        params.put("number", "%" + phoneString + "%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("in1", "a");
        params.put("in2", "b");
        params.put("notin1", "c");
        params.put("notin2", "d");
        params.put("language", "de");
        params.put("orgId", TEST_ORG_ID);

        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5), eq(String.class));
        verify(mockHibernateHelper).getById(HibernateAccount.class, ACCOUNT_ID);
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());

        Map<String, Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(capturedParams.get("appId"), TEST_APP_ID);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("IN1"), "a");
        assertEquals(capturedParams.get("IN2"), "b");
        assertEquals(capturedParams.get("NOTIN1"), "d");
        assertEquals(capturedParams.get("NOTIN2"), "c");
        assertEquals(capturedParams.get("language"), "de");
        assertEquals(capturedParams.get("orgId"), TEST_ORG_ID);

        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(capturedParams.get("appId"), TEST_APP_ID);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("IN1"), "a");
        assertEquals(capturedParams.get("IN2"), "b");
        assertEquals(capturedParams.get("NOTIN1"), "d");
        assertEquals(capturedParams.get("NOTIN2"), "c");
        assertEquals(capturedParams.get("language"), "de");
        assertEquals(capturedParams.get("orgId"), TEST_ORG_ID);
    }

    @Test
    public void getPagedWithOptionalParamsRespectsStudy() throws Exception {
        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.enrollments AS enrollment WITH acct.id = enrollment.accountId WHERE "
                + "acct.appId = :appId AND enrollment.studyId IN (:studies)";
        Set<String> studyIds = ImmutableSet.of("studyA", "studyB");
        try {
            RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                    .withOrgSponsoredStudies(studyIds).build());

            AccountSummarySearch search = new AccountSummarySearch.Builder().build();
            dao.getPagedAccountSummaries(TEST_APP_ID, search);

            verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());
            Map<String, Object> params = paramCaptor.getValue();
            assertEquals(params.get("studies"), studyIds);
            assertEquals(params.get("appId"), TEST_APP_ID);
        } finally {
            RequestContext.set(null);
        }
    }

    @Test
    public void getPagedWithOptionalEmptySetParams() throws Exception {
        String expQuery = "SELECT acct.id FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "acct.email LIKE :email AND acct.phone.number LIKE :number AND acct.createdOn >= "
                + ":startTime AND acct.createdOn <= :endTime AND :language IN ELEMENTS(acct.languages) "
                + "GROUP BY acct.id";

        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "
                + "acct.enrollments AS enrollment WITH acct.id = enrollment.accountId WHERE "
                + "acct.appId = :appId AND acct.email LIKE :email AND acct.phone.number LIKE "
                + ":number AND acct.createdOn >= :startTime AND acct.createdOn <= :endTime AND :language "
                + "IN ELEMENTS(acct.languages)";

        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(ACCOUNT_ID));
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID))
            .thenReturn(makeValidHibernateAccount(false));
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5)
                .withEmailFilter(EMAIL).withPhoneFilter(PHONE.getNationalFormat()).withLanguage("de")
                .withStartTime(startDate).withEndTime(endDate).build();
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(TEST_APP_ID, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(paramsMap.size(), 12);
        assertEquals(paramsMap.get("pageSize"), 5);
        assertEquals(paramsMap.get("offsetBy"), 10);
        assertEquals(paramsMap.get("emailFilter"), EMAIL);
        assertEquals(paramsMap.get("phoneFilter"), PHONE.getNationalFormat());
        assertEquals(paramsMap.get("startTime"), startDate.toString());
        assertEquals(paramsMap.get("endTime"), endDate.toString());
        assertEquals(paramsMap.get("allOfGroups"), Sets.newHashSet());
        assertEquals(paramsMap.get("noneOfGroups"), Sets.newHashSet());
        assertEquals(paramsMap.get("language"), "de");
        assertEquals(paramsMap.get("stringSearchPosition"), INFIX);
        assertEquals(paramsMap.get("predicate"), AND);
        assertEquals(paramsMap.get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*", "");

        // verify hibernate calls
        Map<String, Object> params = new HashMap<>();
        params.put("appId", TEST_APP_ID);
        params.put("email", "%" + EMAIL + "%");
        params.put("number", "%" + phoneString + "%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("language", "de");

        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5), eq(String.class));
        verify(mockHibernateHelper).getById(HibernateAccount.class, ACCOUNT_ID);
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());

        Map<String, Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(capturedParams.get("appId"), TEST_APP_ID);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("language"), "de");

        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(capturedParams.get("appId"), TEST_APP_ID);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("language"), "de");
    }
    
    @Test
    public void getPagedScopedToOrgStudies() {
        String queryExpr = "SELECT acct.id FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "enrollment.studyId IN (:studies) GROUP BY acct.id";
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("A", "B")).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        dao.getPagedAccountSummaries(TEST_APP_ID, search);

        verify(mockHibernateHelper).queryGet(eq(queryExpr), paramCaptor.capture(), eq(0), eq(50), eq(String.class));
        assertEquals(paramCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramCaptor.getValue().get("studies"), ImmutableSet.of("A", "B"));
    }

    @Test
    public void getPagedNotScopedToOrgStudiesForAdmin() {
        String queryExpr = "SELECT acct.id FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId "
                + "GROUP BY acct.id";
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("A", "B")).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        dao.getPagedAccountSummaries(TEST_APP_ID, search);

        verify(mockHibernateHelper).queryGet(eq(queryExpr), paramCaptor.capture(), eq(0), eq(50), eq(String.class));
        assertEquals(paramCaptor.getValue().get("appId"), TEST_APP_ID);
        assertNull(paramCaptor.getValue().get("studies"));
    }
    
    @Test
    public void unmarshallAccountSummarySuccess() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", ACCOUNT_ID, "externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", ACCOUNT_ID, "externalIdB");
        
        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setAppId(TEST_APP_ID);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setEmailVerified(TRUE);
        hibernateAccount.setPhone(PHONE);
        hibernateAccount.setFirstName(FIRST_NAME);
        hibernateAccount.setLastName(LAST_NAME);
        hibernateAccount.setCreatedOn(CREATED_ON);
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setEnrollments(ImmutableSet.of(en1, en2));
        hibernateAccount.setOrgMembership(TEST_ORG_ID);
        hibernateAccount.setNote(TEST_NOTE);

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
        assertEquals(accountSummary.getAppId(), TEST_APP_ID);
        assertEquals(accountSummary.getEmail(), EMAIL);
        assertEquals(accountSummary.getPhone(), PHONE);
        assertEquals(accountSummary.getExternalIds(), ImmutableMap.of("studyA", "externalIdA", "studyB", "externalIdB"));
        assertEquals(accountSummary.getFirstName(), FIRST_NAME);
        assertEquals(accountSummary.getLastName(), LAST_NAME);
        assertEquals(accountSummary.getStatus(), ENABLED);
        assertEquals(accountSummary.getOrgMembership(), TEST_ORG_ID);
        assertEquals(accountSummary.getNote(), TEST_NOTE);

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
    public void unmarshallAccountSummaryFiltersStudies() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID) // it's not the same as ACCOUNT_ID
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("studyB", "studyC")).build());

        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", ACCOUNT_ID, "externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", ACCOUNT_ID, "externalIdB");
        
        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setAppId(TEST_APP_ID);
        hibernateAccount.setStatus(ENABLED);
        hibernateAccount.setEnrollments(ImmutableSet.of(en1, en2));

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getExternalIds(), ImmutableMap.of("studyB", "externalIdB"));
        assertEquals(accountSummary.getStudyIds(), ImmutableSet.of("studyB"));
    }

    @Test
    public void unmarshallAccountSummaryStillReturnsOldExternalId() throws Exception {
        RequestContext.set(
                new RequestContext.Builder().withCallerEnrolledStudies(ImmutableSet.of("studyB", "studyC")).build());

        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setAppId(TEST_APP_ID);
        hibernateAccount.setStatus(ENABLED);

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
    }

    @Test
    public void noLanguageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments AS enrollment "
                + "WITH acct.id = enrollment.accountId WHERE acct.appId = :appId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }

    @Test
    public void languageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments AS enrollment "
                + "WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + ":language IN ELEMENTS(acct.languages) GROUP BY acct.id";
        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
        assertEquals(builder.getParameters().get("language"), "en");
    }

    @Test
    public void groupClausesGroupedCorrectly() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("sdk-int-1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "(:IN1 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }

    @Test
    public void oneAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withAllOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "(:IN1 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }

    @Test
    public void twoAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "(:IN1 IN elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN2"), "group1");
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }

    @Test
    public void oneNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "group1");
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }

    @Test
    public void twoNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("NOTIN2"), "group1");
        assertEquals(builder.getParameters().get("appId"), TEST_APP_ID);
    }
    
    @Test
    public void externalIdFilterQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withExternalIdFilter("filter").build();
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND enrollment.externalId LIKE :extId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("extId"), "%filter%");
    }
    
    @Test
    public void statusFilterQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withStatus(ENABLED).build();
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "acct.status = :status GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("status"), ENABLED);
    }

    @Test
    public void enrollmentFilterWithdrawnQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withEnrollment(WITHDRAWN).build();
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "enrollment.withdrawnOn IS NOT NULL GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
    }

    @Test
    public void enrollmentFilterEnrolledQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withEnrollment(ENROLLED).build();
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND "
                + "enrollment.withdrawnOn IS NULL GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
    }
    
    @Test
    public void attributeKeyQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAttributeKey("foo").withAttributeValueFilter("bar").build();
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.enrollments "
                + "AS enrollment WITH acct.id = enrollment.accountId WHERE acct.appId = :appId AND " 
                + "acct.attributes['foo'] LIKE :attValue GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("attValue"), "%bar%");
    }
    
    @Test
    public void orgMembershipQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOrgMembership(TEST_ORG_ID).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "
                +"acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                +"WHERE acct.appId = :appId AND acct.orgMembership = :orgId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("orgId"), TEST_ORG_ID);
    }
    
    @Test
    public void orgMembershipNoneQueryCorrect() { 
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOrgMembership("<none>").build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "
                +"acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                +"WHERE acct.appId = :appId AND acct.orgMembership IS NULL GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertNull(builder.getParameters().get("orgId"));
    }
    
    @Test
    public void adminQueryCorrect() { 
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAdminOnly(true).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "
                +"acct.enrollments AS enrollment WITH acct.id = enrollment.accountId "
                +"WHERE acct.appId = :appId AND size(acct.roles) > 0 GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertNull(builder.getParameters().get("orgId"));
    }
    
    @Test
    public void queryForEnrollmentInStudyCorrect() {
        RequestContext.set(new RequestContext.Builder().withOrgSponsoredStudies(
                ImmutableSet.of(TEST_STUDY_ID, "studyA", "studyB")).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withEnrolledInStudyId(TEST_STUDY_ID).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct " 
                + "LEFT JOIN acct.enrollments AS enrollment WITH acct.id = " 
                + "enrollment.accountId WHERE acct.appId = :appId AND " 
                + "enrollment.studyId = :studyId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
    }

    @Test
    public void queryForEnrollmentInStudyFilters() {
        RequestContext.set(new RequestContext.Builder().withOrgSponsoredStudies(
                ImmutableSet.of("studyA", "studyB")).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withEnrolledInStudyId(TEST_STUDY_ID).build();

        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct " 
                + "LEFT JOIN acct.enrollments AS enrollment WITH acct.id = " 
                + "enrollment.accountId WHERE acct.appId = :appId AND " 
                + "enrollment.studyId = :studyId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
    }
    
    @Test
    public void researcherQueryCorrect() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyB")).build());
        
        QueryBuilder builder = dao.makeQuery(FULL_QUERY, TEST_APP_ID, null, 
               new AccountSummarySearch.Builder().build(), false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct " 
                + "LEFT JOIN acct.enrollments AS enrollment WITH acct.id = " 
                + "enrollment.accountId WHERE acct.appId = :appId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertNull(builder.getParameters().get("studies"));

    }
    
    @Test
    public void getAppIdsForUser() throws Exception {
        List<String> queryResult = ImmutableList.of("appA", "appB");
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(String.class))).thenReturn(queryResult);
        
        List<String> results = dao.getAppIdForUser(SYNAPSE_USER_ID);
        assertEquals(results, queryResult);
        
        verify(mockHibernateHelper).queryGet(eq("SELECT DISTINCT acct.appId FROM HibernateAccount AS acct WHERE "+
                "synapseUserId = :synapseUserId"), paramCaptor.capture(), eq(null), eq(null), eq(String.class));
        Map<String,Object> params = paramCaptor.getValue();
        assertEquals(params.get("synapseUserId"), SYNAPSE_USER_ID);
    }
    
    @Test
    public void getAppIdsForUserNoSynapseUserId() throws Exception {
        List<String> results = dao.getAppIdForUser(null);
        assertTrue(results.isEmpty());
    }
    
    @Test
    public void getPagedExternalIds() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAppId(TEST_APP_ID);
        en1.setStudyId(TEST_STUDY_ID);
        en1.setExternalId("extId1");
        
        HibernateEnrollment en2 = new HibernateEnrollment();
        en2.setAppId(TEST_APP_ID);
        en2.setStudyId(TEST_STUDY_ID);
        en2.setExternalId("extId2");
        
        List<HibernateEnrollment> list = ImmutableList.of(en1, en2);
        
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class)))
            .thenReturn(list);
        
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(100);
        
        PagedResourceList<ExternalIdentifierInfo> retValue = dao.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, "idFilter", 100, 50);
        assertEquals(retValue.getTotal(), new Integer(100));
        
        ExternalIdentifierInfo info1 = retValue.getItems().get(0);
        assertEquals(info1.getIdentifier(), "extId1");
        assertEquals(info1.getStudyId(), TEST_STUDY_ID);
        assertTrue(info1.isAssigned());
        
        ExternalIdentifierInfo info2 = retValue.getItems().get(1);
        assertEquals(info2.getIdentifier(), "extId2");
        assertEquals(info2.getStudyId(), TEST_STUDY_ID);
        assertTrue(info2.isAssigned());
        
        verify(mockHibernateHelper).queryGet(eq(EXTID_FULL_QUERY), paramCaptor.capture(), eq(100), eq(50), eq(HibernateEnrollment.class));
        verify(mockHibernateHelper).queryCount(eq(EXTID_FULL_COUNT_QUERY), paramCaptor.capture());
        
        Map<String,Object> params1 = paramCaptor.getAllValues().get(0);
        assertEquals(params1.get("appId"), TEST_APP_ID);
        assertEquals(params1.get("studyId"), TEST_STUDY_ID);
        assertEquals(params1.get("idFilter"), "idFilter%");
        
        Map<String,Object> params2 = paramCaptor.getAllValues().get(1);
        assertEquals(params2.get("appId"), TEST_APP_ID);
        assertEquals(params2.get("studyId"), TEST_STUDY_ID);
        assertEquals(params2.get("idFilter"), "idFilter%");
    }
    
    @Test
    public void getPagedExternalIdsNoIdFilter() {
        List<HibernateEnrollment> list = ImmutableList.of();
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class)))
            .thenReturn(list);
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(100);
        
        dao.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, null, 100, 50);
        
        verify(mockHibernateHelper).queryGet(eq(EXTID_QUERY), paramCaptor.capture(), eq(100), eq(50), eq(HibernateEnrollment.class));
        verify(mockHibernateHelper).queryCount(eq(EXTID_COUNT_QUERY), paramCaptor.capture());
        
        Map<String,Object> params1 = paramCaptor.getAllValues().get(0);
        assertEquals(params1.get("appId"), TEST_APP_ID);
        assertEquals(params1.get("studyId"), TEST_STUDY_ID);
        assertNull(params1.get("idFilter"));
        
        Map<String,Object> params2 = paramCaptor.getAllValues().get(1);
        assertEquals(params2.get("appId"), TEST_APP_ID);
        assertEquals(params2.get("studyId"), TEST_STUDY_ID);
        assertNull(params1.get("idFilter"));        
    }

    private void verifyCreatedHealthCode() {
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(updatedAccount.getId(), ACCOUNT_ID);
        assertEquals(updatedAccount.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedAccount.getModifiedOn().getMillis(), MOCK_DATETIME.getMillis());
    }

    // Create minimal generic account for everything that will be used by HibernateAccountDao.
    private static Account makeValidGenericAccount() {
        Account genericAccount = Account.create();
        genericAccount.setId(ACCOUNT_ID);
        genericAccount.setAppId(TEST_APP_ID);
        genericAccount.setEmail(EMAIL);
        genericAccount.setStatus(UNVERIFIED);
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setAppId(TEST_APP_ID);
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