package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantVersionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.worker.Ex3ParticipantVersionRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;

public class ParticipantVersionServiceTest {
    private static final String ACCOUNT_ID = "test-account-id";
    private static final DateTime CREATED_ON = DateTime.parse("2021-11-03T18:31:31.693-0700");
    private static final Set<String> DATA_GROUPS = ImmutableSet.of("test-data-group");
    private static final String EXTERNAL_ID_1 = "ext1";
    private static final List<String> LANGUAGES = ImmutableList.of("en-us");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2021-11-09T17:02:38.917-0800").getMillis();
    private static final int PARTICIPANT_VERSION = 23;
    private static final String STUDY_ID_1 = "study1";
    private static final String STUDY_ID_2 = "study2";
    private static final Map<String, String> STUDY_MEMBERSHIPS = ImmutableMap.of(STUDY_ID_1, EXTERNAL_ID_1);
    private static final String TIME_ZONE = "America/Los_Angeles";
    private static final Map<String, Demographic> APP_DEMOGRAPHICS = ImmutableMap.of("category1", new Demographic("id1",
            null, "category1", false, ImmutableList.of(new DemographicValue("value1")), "units"));
    private static final Map<String, Map<String, Demographic>> STUDY_DEMOGRAPHICS = ImmutableMap.of(
            STUDY_ID_1,
            ImmutableMap.of("category2", new Demographic("id2", null, "category2", true,
                    ImmutableList.of(new DemographicValue("value2"), new DemographicValue(new BigDecimal(3)),
                            new DemographicValue(new BigDecimal(-4)), new DemographicValue(true),
                            new DemographicValue("k", "v")),
                    null)));
    private static final String WORKER_QUEUE_URL = "http://example.com/dummy-sqs-url";

    @Mock
    private AppService mockAppService;

    @Mock
    private DemographicService demographicService;

    @Mock
    private ParticipantVersionDao mockParticipantVersionDao;

    @Mock
    private AmazonSQS mockSqsClient;

    @InjectMocks
    private ParticipantVersionService participantVersionService;

    private App app;

    @BeforeClass
    public static void beforeClass() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock config. This is done separately because we need to set mock config params.
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL)).thenReturn(WORKER_QUEUE_URL);
        participantVersionService.setConfig(mockConfig);

        // Mock app service.
        app = TestUtils.getValidApp(ParticipantVersionServiceTest.class);
        app.setExporter3Enabled(true);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);
    }

    @AfterClass
    public static void afterClass() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void createParticipantVersionFromAccount() {
        // Mock dependencies. DAO doesn't return anything to make create() logic simpler. We test more of this logic
        // in later tests.
        when(mockParticipantVersionDao.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.empty());
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());
        DemographicUser appDemographicUser = new DemographicUser();
        appDemographicUser.setDemographics(APP_DEMOGRAPHICS);
        when(demographicService.getDemographicUser(TestConstants.TEST_APP_ID, null, ACCOUNT_ID)).thenReturn(Optional.of(appDemographicUser));
        DemographicUser study1DemographicUser = new DemographicUser();
        study1DemographicUser.setDemographics(STUDY_DEMOGRAPHICS.get(STUDY_ID_1));
        when(demographicService.getDemographicUser(TestConstants.TEST_APP_ID, STUDY_ID_1, ACCOUNT_ID)).thenReturn(Optional.of(study1DemographicUser));
        // account is in this study but does not have demographics in it
        when(demographicService.getDemographicUser(TestConstants.TEST_APP_ID, STUDY_ID_2, ACCOUNT_ID)).thenReturn(Optional.empty());

        // Make Account. Populate it with attributes we care about for Participant Versions.
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setAppId(TestConstants.TEST_APP_ID);
        account.setHealthCode(TestConstants.HEALTH_CODE);
        account.setCreatedOn(CREATED_ON);
        account.setDataGroups(DATA_GROUPS);
        account.setLanguages(LANGUAGES);
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setClientTimeZone(TIME_ZONE);

        Enrollment enrollment1 = Enrollment.create(TestConstants.TEST_APP_ID, STUDY_ID_1, ACCOUNT_ID, EXTERNAL_ID_1);
        Enrollment enrollment2 = Enrollment.create(TestConstants.TEST_APP_ID, STUDY_ID_2, ACCOUNT_ID);
        account.setEnrollments(ImmutableSet.of(enrollment1, enrollment2));

        // Execute and validate.
        participantVersionService.createParticipantVersionFromAccount(account);

        ArgumentCaptor<ParticipantVersion> participantVersionCaptor = ArgumentCaptor.forClass(
                ParticipantVersion.class);
        verify(mockParticipantVersionDao).createParticipantVersion(participantVersionCaptor.capture());
        verify(demographicService).getDemographicUser(TestConstants.TEST_APP_ID, null, ACCOUNT_ID);
        verify(demographicService).getDemographicUser(TestConstants.TEST_APP_ID, STUDY_ID_1, ACCOUNT_ID);
        verify(demographicService).getDemographicUser(TestConstants.TEST_APP_ID, STUDY_ID_2, ACCOUNT_ID);

        ParticipantVersion participantVersion = participantVersionCaptor.getValue();
        assertEquals(participantVersion.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersion.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(participantVersion.getCreatedOn(), CREATED_ON.getMillis());
        assertEquals(participantVersion.getDataGroups(), DATA_GROUPS);
        assertEquals(participantVersion.getLanguages(), LANGUAGES);
        assertEquals(participantVersion.getModifiedOn(), MOCK_NOW_MILLIS);
        assertEquals(participantVersion.getParticipantVersion(), 1);
        assertEquals(participantVersion.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(participantVersion.getTimeZone(), TIME_ZONE);
        assertEquals(participantVersion.getAppDemographics(), APP_DEMOGRAPHICS);
        Map<String, Map<String, Demographic>> expectedStudyDemographics = new HashMap<>(STUDY_DEMOGRAPHICS);
        expectedStudyDemographics.put(STUDY_ID_2, ImmutableMap.of());
        assertEquals(participantVersion.getStudyDemographics(), expectedStudyDemographics);

        Map<String, String> studyMembershipMap = participantVersion.getStudyMemberships();
        assertEquals(studyMembershipMap.size(), 2);
        assertEquals(studyMembershipMap.get(STUDY_ID_1), EXTERNAL_ID_1);
        assertEquals(studyMembershipMap.get(STUDY_ID_2), BridgeConstants.EXTERNAL_ID_NONE);
    }

    @Test
    public void createParticipantVersionFromAccount_Ex3NotConfigured() {
        // Set Exporter 3.0 to false.
        app.setExporter3Enabled(false);

        // Execute.
        Account account = Account.create();
        account.setAppId(TestConstants.TEST_APP_ID);
        participantVersionService.createParticipantVersionFromAccount(account);

        // We never call through to the dao.
        verifyZeroInteractions(mockParticipantVersionDao);
    }

    @Test
    public void createParticipantVersionFromAccount_HasRoles() {
        // Make Account with role.
        Account account = Account.create();
        account.setAppId(TestConstants.TEST_APP_ID);
        account.setRoles(ImmutableSet.of(Roles.STUDY_DESIGNER));

        // Execute.
        participantVersionService.createParticipantVersionFromAccount(account);

        // We never call through to the dao.
        verifyZeroInteractions(mockParticipantVersionDao);
    }

    @Test
    public void createParticipantVersionFromAccount_NoSharing() {
        // Make account with no_sharing.
        Account account = Account.create();
        account.setAppId(TestConstants.TEST_APP_ID);
        account.setSharingScope(SharingScope.NO_SHARING);

        // Execute.
        participantVersionService.createParticipantVersionFromAccount(account);

        // We never call through to the dao.
        verifyZeroInteractions(mockParticipantVersionDao);
    }

    @Test
    public void createParticipantFromAccount_noEnrollments() {
        Account account = Account.create();
        account.setAppId(TestConstants.TEST_APP_ID);
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);

        // Add a withdrawn enrollment for good measure.
        Enrollment enrollment = Enrollment.create(TestConstants.TEST_APP_ID, STUDY_ID_1, ACCOUNT_ID);
        enrollment.setWithdrawnOn(CREATED_ON.plusDays(7));

        participantVersionService.createParticipantVersionFromAccount(account);
        verifyZeroInteractions(mockParticipantVersionDao);
    }

    @Test
    public void createParticipantVersionFromAccount_noAppDemographics() {
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());
        when(demographicService.getDemographicUser(TestConstants.TEST_APP_ID, null, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setAppId(TestConstants.TEST_APP_ID);
        account.setHealthCode(TestConstants.HEALTH_CODE);
        account.setCreatedOn(CREATED_ON);
        account.setDataGroups(DATA_GROUPS);
        account.setLanguages(LANGUAGES);
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setClientTimeZone(TIME_ZONE);
        Enrollment enrollment = Enrollment.create(TestConstants.TEST_APP_ID, STUDY_ID_1, ACCOUNT_ID, EXTERNAL_ID_1);
        account.setEnrollments(ImmutableSet.of(enrollment));

        // Execute and validate.
        participantVersionService.createParticipantVersionFromAccount(account);

        ArgumentCaptor<ParticipantVersion> participantVersionCaptor = ArgumentCaptor.forClass(
                ParticipantVersion.class);
        verify(mockParticipantVersionDao).createParticipantVersion(participantVersionCaptor.capture());
        verify(demographicService).getDemographicUser(TestConstants.TEST_APP_ID, null, ACCOUNT_ID);

        ParticipantVersion participantVersion = participantVersionCaptor.getValue();
        assertEquals(participantVersion.getAppDemographics(), null);
    }

    // branch coverage: initial version with no createdOn
    @Test
    public void createParticipantVersion_NoCreatedOn() throws Exception {
        // Mock dependencies.
        when(mockParticipantVersionDao.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.empty());
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());

        // Make input.
        ParticipantVersion toCreate = ParticipantVersion.create();
        toCreate.setAppId(TestConstants.TEST_APP_ID);
        toCreate.setHealthCode(TestConstants.HEALTH_CODE);

        // Execute and validate. Just check the values that we add to the participant version.
        participantVersionService.createParticipantVersion(toCreate);

        ArgumentCaptor<ParticipantVersion> createdCaptor = ArgumentCaptor.forClass(ParticipantVersion.class);
        verify(mockParticipantVersionDao).createParticipantVersion(createdCaptor.capture());

        ParticipantVersion created = createdCaptor.getValue();
        assertEquals(created.getCreatedOn(), MOCK_NOW_MILLIS);
        assertEquals(created.getModifiedOn(), MOCK_NOW_MILLIS);
        assertEquals(created.getParticipantVersion(), 1);

        // Verify call to SQS.
        ArgumentCaptor<String> requestJsonTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSqsClient).sendMessage(eq(WORKER_QUEUE_URL), requestJsonTextCaptor.capture());

        String requestJsonText = requestJsonTextCaptor.getValue();
        WorkerRequest workerRequest = BridgeObjectMapper.get().readValue(requestJsonText, WorkerRequest.class);
        assertEquals(workerRequest.getService(), ParticipantVersionService.WORKER_NAME_EX_3_PARTICIPANT_VERSION);

        // Need to convert WorkerRequest.body again, because it doesn't carry inherent typing information. This is
        // fine, since outside of unit tests, we never actually need to deserialize it.
        Ex3ParticipantVersionRequest participantVersionRequest = BridgeObjectMapper.get().convertValue(workerRequest.getBody(),
                Ex3ParticipantVersionRequest.class);
        assertEquals(participantVersionRequest.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersionRequest.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(participantVersionRequest.getParticipantVersion(), 1);
    }

    @Test
    public void createParticipantVersion_UpdatedVersion() {
        // Mock dependencies. Create existing version.
        ParticipantVersion existing = ParticipantVersion.create();
        existing.setAppId(TestConstants.TEST_APP_ID);
        existing.setHealthCode(TestConstants.HEALTH_CODE);
        existing.setCreatedOn(CREATED_ON.getMillis());
        existing.setParticipantVersion(1);

        when(mockParticipantVersionDao.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.of(existing));
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());

        // Make input.
        // Set createdOn to make sure we can't overwrite existing.
        // Add a sharing scope so they aren't identical versions.
        ParticipantVersion toCreate = ParticipantVersion.create();
        toCreate.setAppId(TestConstants.TEST_APP_ID);
        toCreate.setHealthCode(TestConstants.HEALTH_CODE);
        toCreate.setCreatedOn(MOCK_NOW_MILLIS);
        toCreate.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);

        // Execute and validate. Just check the values that we add to the participant version.
        participantVersionService.createParticipantVersion(toCreate);

        ArgumentCaptor<ParticipantVersion> createdCaptor = ArgumentCaptor.forClass(ParticipantVersion.class);
        verify(mockParticipantVersionDao).createParticipantVersion(createdCaptor.capture());

        ParticipantVersion created = createdCaptor.getValue();
        assertEquals(created.getCreatedOn(), CREATED_ON.getMillis());
        assertEquals(created.getModifiedOn(), MOCK_NOW_MILLIS);
        assertEquals(created.getParticipantVersion(), 2);
    }

    @Test
    public void createParticipantVersion_IdenticalVersion() {
        // Mock dependencies. Create existing version.
        ParticipantVersion existing = ParticipantVersion.create();
        existing.setAppId(TestConstants.TEST_APP_ID);
        existing.setHealthCode(TestConstants.HEALTH_CODE);

        when(mockParticipantVersionDao.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.of(existing));

        // Make input. These have identical fields as existing.
        ParticipantVersion toCreate = ParticipantVersion.create();
        toCreate.setAppId(TestConstants.TEST_APP_ID);
        toCreate.setHealthCode(TestConstants.HEALTH_CODE);

        // Execute and validate. We never save the participant version.
        participantVersionService.createParticipantVersion(toCreate);
        verify(mockParticipantVersionDao, never()).createParticipantVersion(any());
    }

    @Test
    public void isIdenticalParticipantVersion_Identical() {
        // Make participant versions. Make them different in keys and timestamps. Should still be considered identical.
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setParticipantVersion(1);
        participantVersion1.setCreatedOn(1111L);
        participantVersion1.setModifiedOn(2222L);

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setParticipantVersion(2);
        participantVersion2.setCreatedOn(3333L);
        participantVersion2.setModifiedOn(4444L);

        assertTrue(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentDataGroups() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setDataGroups(ImmutableSet.of("datagroup1"));

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setDataGroups(ImmutableSet.of("datagroup2"));

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentLanguages() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setLanguages(ImmutableList.of("en-us"));

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setLanguages(ImmutableList.of("en-gb"));

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentSharingScope() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setSharingScope(SharingScope.NO_SHARING);

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentStudyMemberships() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setStudyMemberships(ImmutableMap.of("study1", "ext1"));

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setStudyMemberships(ImmutableMap.of("study2", "ext2"));

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentTimeZones() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setTimeZone("America/New_York");

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setTimeZone("Australia/Sydney");

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_SameAppDemographics() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setAppDemographics(
                ImmutableMap.of("category1", new Demographic("id1", new DemographicUser(), "category1",
                        false, ImmutableList.of(new DemographicValue("value1")), "units")));

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        // need deep copy so re-create
        // id, demographicUser, and categoryName should be ignored
        participantVersion2.setAppDemographics(
                ImmutableMap.of("category1", new Demographic(null, null, null, false,
                        ImmutableList.of(new DemographicValue("value1")), "units")));

        assertTrue(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentAppDemographics() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setAppDemographics(APP_DEMOGRAPHICS);

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setAppDemographics(ImmutableMap.<String, Demographic>builder()
                .putAll(APP_DEMOGRAPHICS).put("another category", new Demographic()).build());

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_SameStudyDemographics() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setStudyDemographics(ImmutableMap.of(
            STUDY_ID_1,
            ImmutableMap.of("category2", new Demographic("id2", new DemographicUser(), "category2", true,
                    ImmutableList.of(new DemographicValue("value2"), new DemographicValue(new BigDecimal(3)),
                            new DemographicValue(new BigDecimal(-4)), new DemographicValue(true), new DemographicValue("k", "v")),
                    null))));

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        // need deep copy so re-create
        // id, demographicUser, and categoryName should be ignored
        participantVersion2.setStudyDemographics(ImmutableMap.of(
                STUDY_ID_1,
                ImmutableMap.of("category2", new Demographic(null, null, null, true,
                        ImmutableList.of(new DemographicValue("value2"), new DemographicValue(new BigDecimal(3)),
                                new DemographicValue(new BigDecimal(-4)), new DemographicValue(true), new DemographicValue("k", "v")),
                        null))));

        assertTrue(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    @Test
    public void isIdenticalParticipantVersion_DifferentStudyDemographics() {
        ParticipantVersion participantVersion1 = makeParticipantVersion();
        participantVersion1.setStudyDemographics(STUDY_DEMOGRAPHICS);

        ParticipantVersion participantVersion2 = makeParticipantVersion();
        participantVersion2.setStudyDemographics(ImmutableMap.<String, Map<String, Demographic>>builder()
                .putAll(STUDY_DEMOGRAPHICS).put("another category", ImmutableMap.of()).build());

        assertFalse(ParticipantVersionService.isIdenticalParticipantVersion(participantVersion1, participantVersion2));
    }

    private static ParticipantVersion makeParticipantVersion() {
        ParticipantVersion participantVersion = ParticipantVersion.create();
        participantVersion.setAppId(TestConstants.TEST_APP_ID);
        participantVersion.setHealthCode(TestConstants.HEALTH_CODE);
        participantVersion.setParticipantVersion(PARTICIPANT_VERSION);
        participantVersion.setCreatedOn(CREATED_ON.getMillis());
        participantVersion.setModifiedOn(MOCK_NOW_MILLIS);
        participantVersion.setDataGroups(DATA_GROUPS);
        participantVersion.setLanguages(LANGUAGES);
        participantVersion.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        participantVersion.setStudyMemberships(STUDY_MEMBERSHIPS);
        participantVersion.setTimeZone(TIME_ZONE);
        participantVersion.setAppDemographics(APP_DEMOGRAPHICS);
        participantVersion.setStudyDemographics(STUDY_DEMOGRAPHICS);
        return participantVersion;
    }

    @Test
    public void deleteParticipantVersionsForHealthCode() {
        participantVersionService.deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);
        verify(mockParticipantVersionDao).deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);
    }

    @Test
    public void getAllParticipantVersionsForHealthCode() {
        // Mock dependencies.
        ParticipantVersion participantVersion = ParticipantVersion.create();
        when(mockParticipantVersionDao.getAllParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(ImmutableList.of(participantVersion));

        // Execute and validate.
        List<ParticipantVersion> resultList = participantVersionService.getAllParticipantVersionsForHealthCode(
                TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        assertEquals(resultList.size(), 1);
        assertSame(resultList.get(0), participantVersion);
    }

    @Test
    public void getLatestParticipantVersionForHealthCode() {
        // Mock dependencies.
        ParticipantVersion participantVersion = ParticipantVersion.create();
        when(mockParticipantVersionDao.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.of(participantVersion));

        // Execute and validate.
        Optional<ParticipantVersion> result = participantVersionService.getLatestParticipantVersionForHealthCode(
                TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        assertTrue(result.isPresent());
        assertSame(result.get(), participantVersion);
    }

    @Test
    public void getParticipantVersion() {
        // Mock dependencies.
        ParticipantVersion participantVersion = ParticipantVersion.create();
        when(mockParticipantVersionDao.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE,
                PARTICIPANT_VERSION)).thenReturn(Optional.of(participantVersion));

        // Execute and validate.
        ParticipantVersion result = participantVersionService.getParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE, PARTICIPANT_VERSION);
        assertSame(result, participantVersion);

        verify(mockParticipantVersionDao).getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE,
                PARTICIPANT_VERSION);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantVersion_NotFound() {
        when(mockParticipantVersionDao.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE,
                PARTICIPANT_VERSION)).thenReturn(Optional.empty());
        participantVersionService.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE,
                PARTICIPANT_VERSION);
    }
}
