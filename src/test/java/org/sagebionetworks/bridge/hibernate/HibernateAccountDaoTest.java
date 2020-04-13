package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
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
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

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
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(API_APP_ID, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(API_APP_ID, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(API_APP_ID, PHONE);
    private static final AccountId ACCOUNT_ID_WITH_HEALTHCODE = AccountId.forHealthCode(API_APP_ID,
            HEALTH_CODE);
    private static final AccountId ACCOUNT_ID_WITH_EXTID = AccountId.forExternalId(API_APP_ID, EXTERNAL_ID);
    private static final AccountId ACCOUNT_ID_WITH_SYNID = AccountId.forSynapseUserId(API_APP_ID,
            SYNAPSE_USER_ID);

    private static final String SUBSTUDY_A = "substudyA";
    private static final String SUBSTUDY_B = "substudyB";
    private static final Map<String, Object> STUDY_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).build();
    private static final Map<String, Object> EMAIL_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).put("email", EMAIL).build();
    private static final Map<String, Object> HEALTHCODE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).put("healthCode", HEALTH_CODE).build();
    private static final Map<String, Object> PHONE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).put("number", PHONE.getNumber())
            .put("regionCode", PHONE.getRegionCode()).build();
    private static final Map<String, Object> EXTID_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).put("externalId", EXTERNAL_ID).build();
    private static final Map<String, Object> SYNAPSE_QUERY_PARAMS = new ImmutableMap.Builder<String, Object>()
            .put("studyId", API_APP_ID).put("synapseUserId", SYNAPSE_USER_ID).build();

    @Captor
    ArgumentCaptor<Map<String, Object>> paramCaptor;

    @Mock
    Consumer<Account> accountConsumer;

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

        study = Study.create();
        study.setIdentifier(API_APP_ID);
        study.setReauthenticationEnabled(true);
        study.setEmailVerificationEnabled(true);
    }

    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(null);
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
        dao.createAccount(study, account, null);
        
        verify(mockHibernateHelper).create(eq(account), any());
    }

    @Test
    public void updateSuccess() {
        Account account = Account.create();
        Consumer<Account> consumer = (oneAccount) -> {};
        
        dao.updateAccount(account, consumer);
        
        verify(mockHibernateHelper).update(account, consumer);
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
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), API_APP_ID);
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
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), API_APP_ID);
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
    public void getByIdWrongStudy() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        hibernateAccount.setHealthCode(null);
        hibernateAccount.setStudyId(API_APP_ID);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate
        AccountId wrongStudy = AccountId.forId("wrong-study", ACCOUNT_ID);
        Optional<Account> opt = dao.getAccount(wrongStudy);
        assertFalse(opt.isPresent());
        
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
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), API_APP_ID);
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
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL).get();
        assertEquals(account.getId(), ACCOUNT_ID);
        assertEquals(account.getStudyId(), API_APP_ID);
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
        Optional<Account> opt = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertFalse(opt.isPresent());
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
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND " 
                + "acct.synapseUserId=:synapseUserId GROUP BY acct.id"; 
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, SYNAPSE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

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
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "acct.healthCode=:healthCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

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
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "
                + "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId "
                + "AND acctSubstudy.externalId=:externalId GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, EXTID_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

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
                (HibernateAccountSubstudy) AccountSubstudy.create(API_APP_ID, SUBSTUDY_A,
                        ACCOUNT_ID),
                (HibernateAccountSubstudy) AccountSubstudy.create(API_APP_ID, SUBSTUDY_B,
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
        assertEquals(accountSummaryList.get(0).getStudyId(), API_APP_ID);
        assertEquals(accountSummaryList.get(0).getEmail(), "email1@example.com");
        assertEquals(accountSummaryList.get(0).getSubstudyIds(), ImmutableSet.of(SUBSTUDY_A, SUBSTUDY_B));

        assertEquals(accountSummaryList.get(1).getId(), "account-2");
        assertEquals(accountSummaryList.get(1).getStudyId(), API_APP_ID);
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
                (HibernateAccountSubstudy) AccountSubstudy.create(API_APP_ID, SUBSTUDY_A,
                        ACCOUNT_ID),
                (HibernateAccountSubstudy) AccountSubstudy.create(API_APP_ID, SUBSTUDY_B,
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
        params.put("studyId", API_APP_ID);
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
        assertEquals(capturedParams.get("studyId"), API_APP_ID);
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
        assertEquals(capturedParams.get("studyId"), API_APP_ID);
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
            assertEquals(params.get("studyId"), API_APP_ID);
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
        params.put("studyId", API_APP_ID);
        params.put("email", "%" + EMAIL + "%");
        params.put("number", "%" + phoneString + "%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("language", "de");

        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5),
                eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());

        Map<String, Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(capturedParams.get("studyId"), API_APP_ID);
        assertEquals(capturedParams.get("email"), "%" + EMAIL + "%");
        assertEquals(capturedParams.get("number"), "%" + phoneString + "%");
        assertEquals(capturedParams.get("startTime"), startDate);
        assertEquals(capturedParams.get("endTime"), endDate);
        assertEquals(capturedParams.get("language"), "de");

        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(capturedParams.get("studyId"), API_APP_ID);
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
        hibernateAccount.setStudyId(API_APP_ID);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setPhone(PHONE);
        hibernateAccount.setFirstName(FIRST_NAME);
        hibernateAccount.setLastName(LAST_NAME);
        hibernateAccount.setCreatedOn(CREATED_ON);
        hibernateAccount.setStatus(ENABLED);

        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(API_APP_ID, "substudyA", ACCOUNT_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(API_APP_ID, "substudyB", ACCOUNT_ID);
        as2.setExternalId("externalIdB");

        when(mockHibernateHelper.queryGet("FROM HibernateAccountSubstudy WHERE accountId=:accountId",
                ImmutableMap.of("accountId", hibernateAccount.getId()), null, null, HibernateAccountSubstudy.class))
                        .thenReturn(ImmutableList.of(as1, as2));

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
        assertEquals(accountSummary.getStudyId(), API_APP_ID);
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
        hibernateAccount.setStudyId(API_APP_ID);
        hibernateAccount.setStatus(ENABLED);

        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(API_APP_ID, "substudyA", ACCOUNT_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(API_APP_ID, "substudyB", ACCOUNT_ID);
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
        hibernateAccount.setStudyId(API_APP_ID);
        hibernateAccount.setStatus(ENABLED);

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(accountSummary.getId(), ACCOUNT_ID);
    }

    @Test
    public void noLanguageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "
                + "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
    }

    @Test
    public void languageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "
                + "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + ":language IN ELEMENTS(acct.languages) GROUP BY acct.id";
        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
        assertEquals(builder.getParameters().get("language"), "en");
    }

    @Test
    public void groupClausesGroupedCorrectly() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("sdk-int-1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
    }

    @Test
    public void oneAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withAllOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
    }

    @Test
    public void twoAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:IN1 IN elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("IN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("IN2"), "group1");
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
    }

    @Test
    public void oneNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(Sets.newHashSet("group1"))
                .build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "group1");
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
    }

    @Test
    public void twoNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();

        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, API_APP_ID, null,
                search, false);

        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "
                + "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "
                + "(:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups)) "
                + "GROUP BY acct.id";

        assertEquals(builder.getQuery(), finalQuery);
        assertEquals(builder.getParameters().get("NOTIN1"), "sdk-int-1");
        assertEquals(builder.getParameters().get("NOTIN2"), "group1");
        assertEquals(builder.getParameters().get("studyId"), API_APP_ID);
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
        genericAccount.setStudyId(API_APP_ID);
        genericAccount.setEmail(EMAIL);
        genericAccount.setStatus(UNVERIFIED);
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setStudyId(API_APP_ID);
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