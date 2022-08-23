package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.UnknownSynapseServerException;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.table.TableEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.exporter.ExporterCreateStudyNotification;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionResult;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.worker.Exporter3Request;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.synapse.SynapseHelper;

public class Exporter3ServiceTest {
    private static final String APP_NAME = "Test App";
    private static final long ADMIN_SYNAPSE_ID = 555L;
    private static final long BRIDGE_ADMIN_TEAM_ID = 1111L;
    private static final long BRIDGE_STAFF_TEAM_ID = 2222L;
    private static final String CREATE_STUDY_TOPIC_ARN = "arn:aws:sns:us-east-1:111111111111:create-study-topic";
    private static final long DATA_ACCESS_TEAM_ID = 3333L;
    private static final long DOWNSTREAM_ETL_SYNAPSE_ID = 3888L;
    private static final long EXPORTER_SYNAPSE_ID = 4444L;
    private static final String EXPORTER_SYNAPSE_USER = "unit-test-user";
    private static final String NAME_SCOPING_TOKEN = "dummy-token";
    private static final int PARTICIPANT_VERSION = 42;
    private static final String PARTICIPANT_VERSION_TABLE_ID = "syn4999";
    private static final String PROJECT_ID = "syn5555";
    private static final String PROJECT_ID_WITHOUT_PREFIX = "5555";
    private static final String RAW_FOLDER_ID = "syn6666";
    private static final String RAW_HEALTH_DATA_BUCKET = "raw-health-data-bucket";
    private static final String RECORD_ID = "test-record";
    private static final long STORAGE_LOCATION_ID = 7777L;
    private static final Map<String, String> SUBSCRIPTION_ATTRIBUTES = ImmutableMap.of("test-attr-name",
            "test-attr-value");
    private static final String SUBSCRIPTION_ARN = "arn:aws:sns:us-east-1:111111111111:create-study-topic:subscription-id";
    private static final String SUBSCRIPTION_ENDPOINT = "arn:aws:sqs:us-east-1:222222222222:subscription-queue";
    private static final String SUBSCRIPTION_PROTOCOL = "sqs";
    private static final String SYNAPSE_TRACKING_VIEW_ID = "syn8888";
    private static final String WORKER_QUEUE_URL = "http://example.com/dummy-sqs-url";

    private static final String EXPECTED_PROJECT_NAME = APP_NAME + " Project " + NAME_SCOPING_TOKEN;
    private static final String EXPECTED_TEAM_NAME = APP_NAME + " Access Team " + NAME_SCOPING_TOKEN;
    private static final Set<Long> DATA_ADMIN_ID_SET = ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID);
    private static final Set<Long> DATA_READ_ONLY_ID_SET = ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID,
            DOWNSTREAM_ETL_SYNAPSE_ID);
    private static final Set<Long> PROJECT_ADMIN_ID_SET = ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID,
            ADMIN_SYNAPSE_ID, DOWNSTREAM_ETL_SYNAPSE_ID);
    private static final Set<Long> PROJECT_READ_ONLY_ID_SET = ImmutableSet.of(BRIDGE_STAFF_TEAM_ID,
            DATA_ACCESS_TEAM_ID);

    private App app;
    private Study study;

    @Mock
    private AccountService mockAccountService;

    @Mock
    private AppService mockAppService;

    @Mock
    private HealthDataEx3Service mockHealthDataEx3Service;

    @Mock
    private ParticipantVersionService mockParticipantVersionService;

    @Mock
    private S3Helper mockS3Helper;

    @Mock
    private AmazonSNS mockSnsClient;

    @Mock
    private AmazonSQS mockSqsClient;

    @Mock
    private StudyService mockStudyService;

    @Mock
    private SynapseHelper mockSynapseHelper;

    @InjectMocks
    @Spy
    private Exporter3Service exporter3Service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock config. This is done separately because we need to set mock config params.
        exporter3Service.setConfig(mockConfig());

        // Spy getNameScopingToken to make it easier to test.
        doReturn(NAME_SCOPING_TOKEN).when(exporter3Service).getNameScopingToken();

        // Mock app service. Override some app properties for ease of testing.
        app = TestUtils.getValidApp(Exporter3ServiceTest.class);
        app.setIdentifier(TestConstants.TEST_APP_ID);
        app.setName(APP_NAME);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        // Similarly, study service.
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_ID);
        study.setName(APP_NAME);
        when(mockStudyService.getStudy(eq(TestConstants.TEST_APP_ID), eq(TestConstants.TEST_STUDY_ID), anyBoolean()))
                .thenReturn(study);
    }

    @Test
    public void initExporter3() throws Exception {
        // App has no exporter3config.
        app.setExporter3Configuration(null);
        app.setExporter3Enabled(false);

        mockSynapseResourceCreation();

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3(TestConstants.TEST_APP_ID);
        verifyEx3ConfigAndSynapse(returnedEx3Config, TestConstants.TEST_APP_ID);

        //  Verify updated app.
        ArgumentCaptor<App> appToUpdateCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppService).updateApp(appToUpdateCaptor.capture(), eq((true)));

        App appToUpdate = appToUpdateCaptor.getValue();
        assertTrue(appToUpdate.isExporter3Enabled());

        Exporter3Configuration ex3ConfigToCreate = appToUpdate.getExporter3Configuration();
        assertEquals(ex3ConfigToCreate, returnedEx3Config);
    }

    // Branch coverage for when admin.synapse.id and downstream.etl.synapse.id aren't specified. Long-term, we want to
    // make sure all envs have these configs set up, so this test will eventually go away.
    @Test
    public void initExporter3_NoOptionalConfigs() throws Exception {
        // Re-write BridgeConfig to not include those configs.
        BridgeConfig mockConfig = mockConfig();
        when(mockConfig.get(Exporter3Service.CONFIG_KEY_ADMIN_SYNAPSE_ID)).thenReturn(null);
        when(mockConfig.get(Exporter3Service.CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID)).thenReturn(null);
        exporter3Service.setConfig(mockConfig);

        // App has no exporter3config.
        app.setExporter3Configuration(null);
        app.setExporter3Enabled(false);

        mockSynapseResourceCreation();

        // Execute.
        exporter3Service.initExporter3(TestConstants.TEST_APP_ID);

        // Just verify the things that are different.
        // Verify that we didn't add the admin as a manager to the team.
        verify(mockSynapseHelper, never()).inviteToTeam(anyLong(), anyLong(), anyBoolean());

        // Verify project ACLs.
        verify(mockSynapseHelper).createAclWithRetry(PROJECT_ID, ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID),
                ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID));

        // Verify created participant version table.
        verify(mockSynapseHelper).createTableWithColumnsAndAcls(Exporter3Service.PARTICIPANT_VERSION_COLUMN_MODELS,
                ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID),
                ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID), PROJECT_ID,
                Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS);

        // Verify folder ACLs.
        verify(mockSynapseHelper).createAclWithRetry(RAW_FOLDER_ID, ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID),
                ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID));
    }

    @Test
    public void initExporter3_AlreadyConfigured() throws Exception {
        // App is already configured for Exporter 3.0.
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();

        app.setExporter3Configuration(ex3Config);
        app.setExporter3Enabled(true);

        // Execute and verify. The output returned is the same as the one in the app.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3(TestConstants.TEST_APP_ID);
        assertEquals(returnedEx3Config, ex3Config);

        // Verify we don't call through to back-end services.
        verifyZeroInteractions(mockS3Helper, mockSynapseHelper);
        verify(mockAppService, never()).updateApp(any(), anyBoolean());
    }

    // branch coverage
    @Test
    public void initExporter3_MissingTrackingView() throws Exception {
        // App EX 3 Config doesn't have project, so we can verify the tracking view stuff.
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();
        ex3Config.setProjectId(null);

        app.setExporter3Configuration(ex3Config);
        app.setExporter3Enabled(true);

        // Mock SynapseHelper.
        Project createdProject = new Project();
        createdProject.setId(PROJECT_ID);
        when(mockSynapseHelper.createEntityWithRetry(any(Project.class))).thenReturn(createdProject);

        // For whatever reason, the tracking view does not exist in Synapse.
        when(mockSynapseHelper.getEntityWithRetry(SYNAPSE_TRACKING_VIEW_ID, EntityView.class))
                .thenReturn(null);

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3(TestConstants.TEST_APP_ID);
        assertEquals(returnedEx3Config.getDataAccessTeamId().longValue(), DATA_ACCESS_TEAM_ID);
        assertEquals(returnedEx3Config.getProjectId(), PROJECT_ID);
        assertEquals(returnedEx3Config.getRawDataFolderId(), RAW_FOLDER_ID);
        assertEquals(returnedEx3Config.getStorageLocationId().longValue(), STORAGE_LOCATION_ID);

        // Just verify the tracking view stuff. Specifically, since it doesn't exist, we never wrote it back to
        // Synapse.
        verify(mockSynapseHelper, never()).updateEntityWithRetry(any(EntityView.class));
    }

    // branch coverage
    @Test
    public void initExporter3_ErrorAddingToTrackingView() throws Exception {
        // App EX 3 Config doesn't have project, so we can verify the tracking view stuff.
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();
        ex3Config.setProjectId(null);

        app.setExporter3Configuration(ex3Config);
        app.setExporter3Enabled(true);

        // Mock SynapseHelper.
        Project createdProject = new Project();
        createdProject.setId(PROJECT_ID);
        when(mockSynapseHelper.createEntityWithRetry(any(Project.class))).thenReturn(createdProject);

        EntityView trackingView = new EntityView();
        trackingView.setScopeIds(new ArrayList<>());
        when(mockSynapseHelper.getEntityWithRetry(SYNAPSE_TRACKING_VIEW_ID, EntityView.class))
                .thenReturn(trackingView);

        when(mockSynapseHelper.updateEntityWithRetry(any(EntityView.class)))
                .thenThrow(UnknownSynapseServerException.class);

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3(TestConstants.TEST_APP_ID);
        assertEquals(returnedEx3Config.getDataAccessTeamId().longValue(), DATA_ACCESS_TEAM_ID);
        assertEquals(returnedEx3Config.getProjectId(), PROJECT_ID);
        assertEquals(returnedEx3Config.getRawDataFolderId(), RAW_FOLDER_ID);
        assertEquals(returnedEx3Config.getStorageLocationId().longValue(), STORAGE_LOCATION_ID);

        // Just verify the tracking view stuff. We call update, and then silently swallow the exception.
        verify(mockSynapseHelper).updateEntityWithRetry(any(EntityView.class));
    }

    @Test
    public void initExporter3ForStudy() throws Exception {
        // Study has no exporter3config.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        mockSynapseResourceCreation();

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        verifyEx3ConfigAndSynapse(returnedEx3Config, TestConstants.TEST_APP_ID + '/' +
                TestConstants.TEST_STUDY_ID);

        //  Verify updated study.
        ArgumentCaptor<Study> studyToUpdateCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyService).updateStudy(eq(TestConstants.TEST_APP_ID), studyToUpdateCaptor.capture());

        Study studyToUpdate = studyToUpdateCaptor.getValue();
        assertTrue(studyToUpdate.isExporter3Enabled());

        Exporter3Configuration ex3ConfigToCreate = studyToUpdate.getExporter3Configuration();
        assertEquals(ex3ConfigToCreate, returnedEx3Config);
    }

    @Test
    public void initExporter3ForStudy_AlreadyConfigured() throws Exception {
        // Study is already configured for Exporter 3.0.
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();

        study.setExporter3Configuration(ex3Config);
        study.setExporter3Enabled(true);

        // Execute and verify. The output returned is the same as the one in the app.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertEquals(returnedEx3Config, ex3Config);

        // Verify we don't call through to back-end services.
        verifyZeroInteractions(mockS3Helper, mockSynapseHelper);
        verify(mockAppService, never()).updateApp(any(), anyBoolean());
    }

    // branch coverage
    @Test
    public void initExporter3ForStudy_PartialInit_NoNotification() throws Exception {
        // Study has an ex3Config, but it's blank.
        study.setExporter3Configuration(new Exporter3Configuration());
        study.setExporter3Enabled(false);

        mockSynapseResourceCreation();

        // Execute. We only care about the notification (or lack thereof).
        exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);
        verifyZeroInteractions(mockSnsClient);
    }

    // branch coverage
    @Test
    public void initExporter3ForStudy_NoAppEx3Config_NoNotification() throws Exception {
        // Study has no exporter3config, and neither does app.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        app.setExporter3Configuration(null);

        mockSynapseResourceCreation();

        // Execute. We only care about the notification (or lack thereof).
        exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);
        verifyZeroInteractions(mockSnsClient);
    }

    // branch coverage
    @Test
    public void initExporter3ForStudy_NoTopicArn_NoNotification() throws Exception {
        // Study has no exporter3config. App has one, but it doesn't have the topic arn.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        Exporter3Configuration appEx3Config = new Exporter3Configuration();
        appEx3Config.setCreateStudyNotificationTopicArn(null);
        app.setExporter3Configuration(appEx3Config);

        mockSynapseResourceCreation();

        // Execute. We only care about the notification (or lack thereof).
        exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);
        verifyZeroInteractions(mockSnsClient);
    }

    @Test
    public void initExporter3ForStudy_SendNotification() throws Exception {
        // Study has no exporter3config. App has one, and it also has a topic arn.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        Exporter3Configuration appEx3Config = new Exporter3Configuration();
        appEx3Config.setCreateStudyNotificationTopicArn(CREATE_STUDY_TOPIC_ARN);
        app.setExporter3Configuration(appEx3Config);

        mockSynapseResourceCreation();

        // Mock SNS publish.
        when(mockSnsClient.publish(any(), any())).thenReturn(new PublishResult());

        // Execute. We only care about the notification (or lack thereof).
        exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);

        ArgumentCaptor<String> notificationJsonTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSnsClient).publish(eq(CREATE_STUDY_TOPIC_ARN), notificationJsonTextCaptor.capture());

        String notificationJsonText = notificationJsonTextCaptor.getValue();
        ExporterCreateStudyNotification notification = BridgeObjectMapper.get().readValue(notificationJsonText,
                ExporterCreateStudyNotification.class);
        assertEquals(notification.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(notification.getParentProjectId(), PROJECT_ID);
        assertEquals(notification.getRawFolderId(), RAW_FOLDER_ID);
        assertEquals(notification.getStudyId(), TestConstants.TEST_STUDY_ID);
    }

    private static BridgeConfig mockConfig() {
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.get(Exporter3Service.CONFIG_KEY_ADMIN_SYNAPSE_ID))
                .thenReturn(String.valueOf(ADMIN_SYNAPSE_ID));
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_TEAM_BRIDGE_ADMIN)).thenReturn((int) BRIDGE_ADMIN_TEAM_ID);
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_TEAM_BRIDGE_STAFF)).thenReturn((int) BRIDGE_STAFF_TEAM_ID);
        when(mockConfig.get(Exporter3Service.CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID))
                .thenReturn(String.valueOf(DOWNSTREAM_ETL_SYNAPSE_ID));
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_EXPORTER_SYNAPSE_ID)).thenReturn((int) EXPORTER_SYNAPSE_ID);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_EXPORTER_SYNAPSE_USER))
                .thenReturn(EXPORTER_SYNAPSE_USER);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_RAW_HEALTH_DATA_BUCKET))
                .thenReturn(RAW_HEALTH_DATA_BUCKET);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_SYNAPSE_TRACKING_VIEW))
                .thenReturn(SYNAPSE_TRACKING_VIEW_ID);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL)).thenReturn(WORKER_QUEUE_URL);
        return mockConfig;
    }

    private void mockSynapseResourceCreation() throws Exception {
        Team createdTeam = new Team();
        createdTeam.setId(String.valueOf(DATA_ACCESS_TEAM_ID));
        when(mockSynapseHelper.createTeamWithRetry(any())).thenReturn(createdTeam);

        Project createdProject = new Project();
        createdProject.setId(PROJECT_ID);
        when(mockSynapseHelper.createEntityWithRetry(any(Project.class))).thenReturn(createdProject);

        EntityView trackingView = new EntityView();
        trackingView.setScopeIds(new ArrayList<>());
        when(mockSynapseHelper.getEntityWithRetry(SYNAPSE_TRACKING_VIEW_ID, EntityView.class))
                .thenReturn(trackingView);

        TableEntity createdTable = new TableEntity();
        createdTable.setId(PARTICIPANT_VERSION_TABLE_ID);
        when(mockSynapseHelper.createTableWithColumnsAndAcls(anyList(), anySet(), anySet(), anyString(), anyString()))
                .thenReturn(PARTICIPANT_VERSION_TABLE_ID);

        Folder createdFolder = new Folder();
        createdFolder.setId(RAW_FOLDER_ID);
        when(mockSynapseHelper.createEntityWithRetry(any(Folder.class))).thenReturn(createdFolder);

        ExternalS3StorageLocationSetting createdStorageLocation = new ExternalS3StorageLocationSetting();
        createdStorageLocation.setStorageLocationId(STORAGE_LOCATION_ID);
        when(mockSynapseHelper.createStorageLocationForEntity(eq(RAW_FOLDER_ID),
                any(ExternalS3StorageLocationSetting.class))).thenReturn(createdStorageLocation);
    }

    private void verifyEx3ConfigAndSynapse(Exporter3Configuration ex3Config, String s3BaseKey) throws Exception {
        // Validate returned EX3 Config.
        assertEquals(ex3Config.getDataAccessTeamId().longValue(), DATA_ACCESS_TEAM_ID);
        assertEquals(ex3Config.getParticipantVersionTableId(), PARTICIPANT_VERSION_TABLE_ID);
        assertEquals(ex3Config.getProjectId(), PROJECT_ID);
        assertEquals(ex3Config.getRawDataFolderId(), RAW_FOLDER_ID);
        assertEquals(ex3Config.getStorageLocationId().longValue(), STORAGE_LOCATION_ID);

        // Verify created team.
        ArgumentCaptor<Team> teamToCreateCaptor = ArgumentCaptor.forClass(Team.class);
        verify(mockSynapseHelper).createTeamWithRetry(teamToCreateCaptor.capture());

        Team teamToCreate = teamToCreateCaptor.getValue();
        assertEquals(teamToCreate.getName(), EXPECTED_TEAM_NAME);

        verify(mockSynapseHelper).inviteToTeam(DATA_ACCESS_TEAM_ID, ADMIN_SYNAPSE_ID, true);

        // Verify created project. Note that we call this method again later, which is why we verify it twice now.
        ArgumentCaptor<Entity> entitiesToCreateCaptor = ArgumentCaptor.forClass(Project.class);
        verify(mockSynapseHelper, times(2)).createEntityWithRetry(entitiesToCreateCaptor
                .capture());
        List<Entity> entitiesToCreateList = entitiesToCreateCaptor.getAllValues();

        Project projectToCreate = (Project) entitiesToCreateList.get(0);
        assertEquals(projectToCreate.getName(), EXPECTED_PROJECT_NAME);

        // Verify project ACLs.
        verify(mockSynapseHelper).createAclWithRetry(PROJECT_ID, PROJECT_ADMIN_ID_SET, PROJECT_READ_ONLY_ID_SET);

        // Verify project added to tracking view. For whatever reason, view scope IDs don't include the "syn" prefix.
        ArgumentCaptor<EntityView> viewToUpdateCaptor = ArgumentCaptor.forClass(EntityView.class);
        verify(mockSynapseHelper).updateEntityWithRetry(viewToUpdateCaptor.capture());

        EntityView viewToUpdate = viewToUpdateCaptor.getValue();
        assertTrue(viewToUpdate.getScopeIds().contains(PROJECT_ID_WITHOUT_PREFIX));

        // Verify created participant version table.
        verify(mockSynapseHelper).createTableWithColumnsAndAcls(Exporter3Service.PARTICIPANT_VERSION_COLUMN_MODELS,
                DATA_READ_ONLY_ID_SET, DATA_ADMIN_ID_SET, PROJECT_ID,
                Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS);

        // Verify created folder.
        Folder folderToCreate = (Folder) entitiesToCreateList.get(1);
        assertEquals(folderToCreate.getName(), Exporter3Service.FOLDER_NAME_BRIDGE_RAW_DATA);
        assertEquals(folderToCreate.getParentId(), PROJECT_ID);

        // Verify folder ACLs.
        verify(mockSynapseHelper).createAclWithRetry(RAW_FOLDER_ID, DATA_ADMIN_ID_SET, DATA_READ_ONLY_ID_SET);

        // Verify we write to S3 for the storage location.
        verify(mockS3Helper).writeLinesToS3(RAW_HEALTH_DATA_BUCKET, s3BaseKey + "/owner.txt",
                ImmutableList.of(EXPORTER_SYNAPSE_USER));

        // Verify created storage location.
        ArgumentCaptor<ExternalS3StorageLocationSetting> storageLocationToCreateCaptor =
                ArgumentCaptor.forClass(ExternalS3StorageLocationSetting.class);
        verify(mockSynapseHelper).createStorageLocationForEntity(eq(RAW_FOLDER_ID),
                storageLocationToCreateCaptor.capture());

        ExternalS3StorageLocationSetting storageLocationToCreate = storageLocationToCreateCaptor.getValue();
        assertEquals(storageLocationToCreate.getBaseKey(), s3BaseKey);
        assertEquals(storageLocationToCreate.getBucket(), RAW_HEALTH_DATA_BUCKET);
        assertTrue(storageLocationToCreate.getStsEnabled());
    }

    private static Exporter3Configuration makeConfiguredEx3Config() {
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        ex3Config.setDataAccessTeamId(DATA_ACCESS_TEAM_ID);
        ex3Config.setParticipantVersionTableId(PARTICIPANT_VERSION_TABLE_ID);
        ex3Config.setProjectId(PROJECT_ID);
        ex3Config.setRawDataFolderId(RAW_FOLDER_ID);
        ex3Config.setStorageLocationId(STORAGE_LOCATION_ID);
        return ex3Config;
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void subscribeToCreateStudyNotifications_InvalidSubscription() {
        exporter3Service.subscribeToCreateStudyNotifications(TestConstants.TEST_APP_ID,
                new ExporterSubscriptionRequest());
    }

    @Test
    public void subscribeToCreateStudyNotifications_ConfigureTopic() {
        // App has no exporter3config.
        app.setExporter3Configuration(null);

        // Mock SNS.
        String topicName = String.format(Exporter3Service.FORMAT_CREATE_STUDY_TOPIC_NAME, TestConstants.TEST_APP_ID);
        CreateTopicResult createTopicResult = new CreateTopicResult();
        createTopicResult.setTopicArn(CREATE_STUDY_TOPIC_ARN);
        when(mockSnsClient.createTopic(topicName)).thenReturn(createTopicResult);

        SubscribeResult subscribeResult = new SubscribeResult();
        subscribeResult.setSubscriptionArn(SUBSCRIPTION_ARN);
        when(mockSnsClient.subscribe(any())).thenReturn(subscribeResult);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service.subscribeToCreateStudyNotifications(
                TestConstants.TEST_APP_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        verify(mockSnsClient).createTopic(topicName);

        ArgumentCaptor<App> updatedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppService).updateApp(updatedAppCaptor.capture(), eq(true));
        assertEquals(updatedAppCaptor.getValue().getExporter3Configuration().getCreateStudyNotificationTopicArn(),
                CREATE_STUDY_TOPIC_ARN);

        ArgumentCaptor<SubscribeRequest> snsSubscribeRequestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(mockSnsClient).subscribe(snsSubscribeRequestCaptor.capture());
        SubscribeRequest snsSubscribeRequest = snsSubscribeRequestCaptor.getValue();
        assertEquals(snsSubscribeRequest.getAttributes(), SUBSCRIPTION_ATTRIBUTES);
        assertEquals(snsSubscribeRequest.getEndpoint(), SUBSCRIPTION_ENDPOINT);
        assertEquals(snsSubscribeRequest.getProtocol(), SUBSCRIPTION_PROTOCOL);
        assertEquals(snsSubscribeRequest.getTopicArn(), CREATE_STUDY_TOPIC_ARN);
    }

    @Test
    public void subscribeToCreateStudyNotifications_ExistingTopic() {
        // App has an exporter3config with a topic arn.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        ex3Config.setCreateStudyNotificationTopicArn(CREATE_STUDY_TOPIC_ARN);
        app.setExporter3Configuration(ex3Config);

        // Mock SNS.
        SubscribeResult subscribeResult = new SubscribeResult();
        subscribeResult.setSubscriptionArn(SUBSCRIPTION_ARN);
        when(mockSnsClient.subscribe(any())).thenReturn(subscribeResult);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service.subscribeToCreateStudyNotifications(
                TestConstants.TEST_APP_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        verify(mockSnsClient, never()).createTopic(anyString());
        verify(mockAppService, never()).updateApp(any(), anyBoolean());

        ArgumentCaptor<SubscribeRequest> snsSubscribeRequestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(mockSnsClient).subscribe(snsSubscribeRequestCaptor.capture());
        SubscribeRequest snsSubscribeRequest = snsSubscribeRequestCaptor.getValue();
        assertEquals(snsSubscribeRequest.getAttributes(), SUBSCRIPTION_ATTRIBUTES);
        assertEquals(snsSubscribeRequest.getEndpoint(), SUBSCRIPTION_ENDPOINT);
        assertEquals(snsSubscribeRequest.getProtocol(), SUBSCRIPTION_PROTOCOL);
        assertEquals(snsSubscribeRequest.getTopicArn(), CREATE_STUDY_TOPIC_ARN);
    }

    private static ExporterSubscriptionRequest makeSubscriptionRequest() {
        ExporterSubscriptionRequest request = new ExporterSubscriptionRequest();
        request.setAttributes(SUBSCRIPTION_ATTRIBUTES);
        request.setEndpoint(SUBSCRIPTION_ENDPOINT);
        request.setProtocol(SUBSCRIPTION_PROTOCOL);
        return request;
    }

    @Test
    public void completeUpload() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock ParticipantVersionService.
        ParticipantVersion participantVersion = ParticipantVersion.create();
        participantVersion.setAppId(TestConstants.TEST_APP_ID);
        participantVersion.setHealthCode(TestConstants.HEALTH_CODE);
        participantVersion.setParticipantVersion(PARTICIPANT_VERSION);
        when(mockParticipantVersionService.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.of(participantVersion));

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify call to AccountService.
        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        verify(mockAccountService).getAccount(accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(accountId.getHealthCode(), TestConstants.HEALTH_CODE);

        // Verify call to HealthDataEx3Service. Just verify that the record ID was pass in through the upload is
        // propagated. (And the attributes we set directly.)
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        assertEquals(recordToCreate.getId(), RECORD_ID);
        assertEquals(recordToCreate.getParticipantVersion().intValue(), PARTICIPANT_VERSION);
        assertEquals(recordToCreate.getSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);

        // Verify call to SQS.
        ArgumentCaptor<String> requestJsonTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSqsClient).sendMessage(eq(WORKER_QUEUE_URL), requestJsonTextCaptor.capture());

        String requestJsonText = requestJsonTextCaptor.getValue();
        WorkerRequest workerRequest = BridgeObjectMapper.get().readValue(requestJsonText, WorkerRequest.class);
        assertEquals(workerRequest.getService(), Exporter3Service.WORKER_NAME_EXPORTER_3);

        // Need to convert WorkerRequest.body again, because it doesn't carry inherent typing information. This is
        // fine, since outside of unit tests, we never actually need to deserialize it.
        Exporter3Request ex3Request = BridgeObjectMapper.get().convertValue(workerRequest.getBody(),
                Exporter3Request.class);
        assertEquals(ex3Request.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(ex3Request.getRecordId(), RECORD_ID);
    }

    @Test
    public void completeUpload_Case2() throws Exception {
        // Normal test case with the following changes
        // 1. No participant version
        // 2. Existing record

        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 existingRecord = HealthDataRecordEx3.create();
        existingRecord.setId(RECORD_ID);
        existingRecord.setVersion(1L);
        when(mockHealthDataEx3Service.getRecord(RECORD_ID, false)).thenReturn(Optional.of(existingRecord));

        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        createdRecord.setVersion(2L);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock ParticipantVersionService.
        when(mockParticipantVersionService.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.empty());

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify that the saved record has no participant version and has the version copied from the existing record.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        assertNull(recordToCreate.getParticipantVersion());
        assertEquals(recordToCreate.getVersion().longValue(), 1L);
    }

    @Test
    public void completeUpload_NoSharing() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setSharingScope(SharingScope.NO_SHARING);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // No call to SQS.
        verifyZeroInteractions(mockSqsClient);
    }

    @Test
    public void completeUpload_NoAccount() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());

        // Execute.
        try {
            exporter3Service.completeUpload(app, upload);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "StudyParticipant");
        }

        // No calls to HealthDataEx3Service or SQS.
        verifyZeroInteractions(mockHealthDataEx3Service, mockSqsClient);
    }

    // checks study and app id annotations are not added when the project exists
    @Test
    public void studyMetadataProjectExists() throws BridgeSynapseException, IOException, SynapseException {
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();
        // project id is non-null

        exporter3Service.initExporter3Internal(APP_NAME, TEST_APP_ID, Optional.of(TEST_STUDY_ID), ex3Config);
        verify(mockSynapseHelper, never()).addAnnotationsToEntity(any(), any());
    }

    // checks study and app id annotations for app id only
    @Test
    public void studyMetadataAppOnly() throws BridgeSynapseException, IOException, SynapseException {
        when(mockSynapseHelper.createEntityWithRetry(any())).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(PROJECT_ID);
            return project;
        });
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();
        ex3Config.setProjectId(null);

        Map<String, AnnotationsValue> expectedAppOnly = ImmutableMap.of(
                "appId",
                new AnnotationsValue().setType(AnnotationsValueType.STRING).setValue(ImmutableList.of(TEST_APP_ID)));
        exporter3Service.initExporter3Internal(APP_NAME, TEST_APP_ID, Optional.empty(), ex3Config);
        verify(mockSynapseHelper).addAnnotationsToEntity(eq(PROJECT_ID), eq(expectedAppOnly));
    }

    // checks study and app id annotations for app id + study id
    @Test
    public void studyMetadataAppAndStudy() throws BridgeSynapseException, IOException, SynapseException {
        when(mockSynapseHelper.createEntityWithRetry(any())).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(PROJECT_ID);
            return project;
        });
        Exporter3Configuration ex3Config = makeConfiguredEx3Config();
        ex3Config.setProjectId(null);

        Map<String, AnnotationsValue> expectedAppAndStudy = ImmutableMap.of(
                "appId",
                new AnnotationsValue().setType(AnnotationsValueType.STRING).setValue(ImmutableList.of(TEST_APP_ID)),
                "studyId",
                new AnnotationsValue().setType(AnnotationsValueType.STRING).setValue(ImmutableList.of(TEST_STUDY_ID)));
        exporter3Service.initExporter3Internal(APP_NAME, TEST_APP_ID, Optional.of(TEST_STUDY_ID), ex3Config);
        verify(mockSynapseHelper).addAnnotationsToEntity(eq(PROJECT_ID), eq(expectedAppAndStudy));
    }
}
