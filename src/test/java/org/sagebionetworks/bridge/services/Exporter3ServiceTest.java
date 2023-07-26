package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.UnknownSynapseServerException;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.file.CloudProviderFileHandleInterface;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.table.MaterializedView;
import org.sagebionetworks.repo.model.table.TableEntity;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExportToStudyNotification;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;
import org.sagebionetworks.bridge.models.exporter.ExporterCreateStudyNotification;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionResult;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
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
    private static final String BUCKET_SUFFIX = "bucket-suffix";
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(TestConstants.UA);
    private static final long DATA_ACCESS_TEAM_ID = 3333L;
    private static final long DOWNSTREAM_ETL_SYNAPSE_ID = 3888L;
    private static final long EXPORTER_SYNAPSE_ID = 4444L;
    private static final String EXPORTER_SYNAPSE_USER = "unit-test-user";
    private static final String NAME_SCOPING_TOKEN = "dummy-token";
    private static final int PARTICIPANT_VERSION = 42;
    private static final String PARTICIPANT_VERSION_TABLE_ID = "syn4999";
    private static final String PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID = "syn5999";
    private static final String PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID = "syn6999";
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
    private static final String TOPIC_ARN_CREATE_STUDY = "arn:aws:sns:us-east-1:111111111111:create-study-topic";
    private static final String TOPIC_ARN_EXPORT_FOR_APP = "arn:aws:sns:us-east-1:111111111111:export-for-app";
    private static final String TOPIC_ARN_EXPORT_FOR_STUDY = "arn:aws:sns:us-east-1:111111111111:export-for-study";
    private static final String USER_ID = "test-user";
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

    // Parameters for export notifications.
    private static final String EXPORT_NOTIFICATION_FOR_APP_ARN =
            "arn:aws:sns:us-east-1:111111111111:export-for-app-topic";
    private static final String EXPORT_NOTIFICATION_FOR_STUDY_1_ARN =
            "arn:aws:sns:us-east-1:111111111111:export-for-study1-topic";
    private static final String EXPORT_NOTIFICATION_FOR_STUDY_2_ARN =
            "arn:aws:sns:us-east-1:111111111111:export-for-study2-topic";

    private static final String APP_FILE_ENTITY_ID = "syn1111";
    private static final String APP_PARENT_PROJECT_ID = "syn1222";
    private static final String APP_RAW_FOLDER_ID = "syn1333";
    private static final String APP_S3_BUCKET = "app-bucket";
    private static final String APP_S3_KEY = "app-record-key";

    private static final String STUDY_1_ID = "study1";
    private static final String STUDY_1_FILE_ENTITY_ID = "syn2111";
    private static final String STUDY_1_PARENT_PROJECT_ID = "syn2222";
    private static final String STUDY_1_RAW_FOLDER_ID = "syn2333";
    private static final String STUDY_1_S3_BUCKET = "study1-bucket";
    private static final String STUDY_1_S3_KEY = "study1-record-key";

    private static final String STUDY_2_ID = "study2";
    private static final String STUDY_2_FILE_ENTITY_ID = "syn3111";
    private static final String STUDY_2_PARENT_PROJECT_ID = "syn3222";
    private static final String STUDY_2_RAW_FOLDER_ID = "syn3333";
    private static final String STUDY_2_S3_BUCKET = "study2-bucket";
    private static final String STUDY_2_S3_KEY = "study2-record-key";

    private App app;
    private Study study;
    private Schedule2 schedule;

    @Mock
    private AccountService mockAccountService;

    @Mock
    private AppService mockAppService;

    @Mock
    private HealthDataEx3Service mockHealthDataEx3Service;

    @Mock
    private ParticipantVersionService mockParticipantVersionService;

    @Mock
    private RequestInfoService mockRequestInfoService;

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

    @Mock
    private SynapseClient mockSynapseClient;

    @Mock
    private Schedule2Service mockSchedule2Service;

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

        // Mock Schedule
        schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(TestConstants.SCHEDULE_GUID);
    }

    @AfterClass
    public static void afterClass() {
        // Reset Request Context.
        RequestContext.set(RequestContext.NULL_INSTANCE);
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

        // Verify created participant version demographics table.
        verify(mockSynapseHelper).createTableWithColumnsAndAcls(
                Exporter3Service.PARTICIPANT_VERSION_DEMOGRAPHICS_COLUMN_MODELS,
                ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID),
                ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID), PROJECT_ID,
                Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS);

        ArgumentCaptor<Entity> materializedViewCaptor = ArgumentCaptor.forClass(Entity.class);
        verify(mockSynapseHelper, atLeastOnce()).createEntityWithRetry(materializedViewCaptor.capture());
        MaterializedView capturedMaterializedView = null;
        for (Entity entity : materializedViewCaptor.getAllValues()) {
            if (entity instanceof MaterializedView) {
                capturedMaterializedView = (MaterializedView) entity;
            }
        }
        if (capturedMaterializedView == null) {
            fail("should have called createEntityWithRetry with a MaterializedView");
        }
        assertEquals(capturedMaterializedView.getName(), Exporter3Service.VIEW_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS);
        assertEquals(capturedMaterializedView.getParentId(), PROJECT_ID);
        assertEquals(capturedMaterializedView.getDefiningSQL(), String.format(Exporter3Service.VIEW_DEFINING_SQL,
                PARTICIPANT_VERSION_TABLE_ID, PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID));
        verify(mockSynapseHelper).createAclWithRetry(PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID,
                ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID),
                ImmutableSet.of(BRIDGE_STAFF_TEAM_ID, DATA_ACCESS_TEAM_ID));

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
        appEx3Config.setCreateStudyNotificationTopicArn(TOPIC_ARN_CREATE_STUDY);
        app.setExporter3Configuration(appEx3Config);

        mockSynapseResourceCreation();

        // Mock SNS publish.
        when(mockSnsClient.publish(any(), any())).thenReturn(new PublishResult());

        // Execute. We only care about the notification (or lack thereof).
        exporter3Service.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);

        ArgumentCaptor<String> notificationJsonTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSnsClient).publish(eq(TOPIC_ARN_CREATE_STUDY), notificationJsonTextCaptor.capture());

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
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_BUCKET_SUFFIX)).thenReturn(BUCKET_SUFFIX);
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
        when(mockSynapseHelper.getEntityWithRetry(SYNAPSE_TRACKING_VIEW_ID, EntityView.class)).thenReturn(trackingView);

        TableEntity createdParticipantVersionsTable = new TableEntity();
        createdParticipantVersionsTable.setId(PARTICIPANT_VERSION_TABLE_ID);
        when(mockSynapseHelper.createTableWithColumnsAndAcls(anyList(), anySet(), anySet(), anyString(),
                eq(Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS)))
                .thenReturn(PARTICIPANT_VERSION_TABLE_ID);

        TableEntity createdParticipantVersionsDemographicsTable = new TableEntity();
        createdParticipantVersionsDemographicsTable.setId(PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID);
        when(mockSynapseHelper.createTableWithColumnsAndAcls(anyList(), anySet(), anySet(), anyString(),
                eq(Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS)))
                .thenReturn(PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID);

        when(mockSynapseHelper.createEntityWithRetry(any(MaterializedView.class))).thenAnswer(invocation -> {
            MaterializedView materializedView = invocation.getArgument(0);
            materializedView.setId(PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID);
            return materializedView;
        });

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
        assertEquals(ex3Config.getParticipantVersionDemographicsTableId(), PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID);
        assertEquals(ex3Config.getParticipantVersionDemographicsViewId(), PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID);
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
        verify(mockSynapseHelper, times(3)).createEntityWithRetry(entitiesToCreateCaptor
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

        // Verify created participant version demographics table.
        verify(mockSynapseHelper).createTableWithColumnsAndAcls(
                Exporter3Service.PARTICIPANT_VERSION_DEMOGRAPHICS_COLUMN_MODELS,
                DATA_READ_ONLY_ID_SET, DATA_ADMIN_ID_SET, PROJECT_ID,
                Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS);

        // Verify created participant version demographics view.
        MaterializedView capturedMaterializedView = (MaterializedView) entitiesToCreateList.get(1);
        assertEquals(capturedMaterializedView.getName(), Exporter3Service.VIEW_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS);
        assertEquals(capturedMaterializedView.getParentId(), PROJECT_ID);
        assertEquals(capturedMaterializedView.getDefiningSQL(), String.format(Exporter3Service.VIEW_DEFINING_SQL,
                PARTICIPANT_VERSION_TABLE_ID, PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID));
        verify(mockSynapseHelper).createAclWithRetry(PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID, DATA_ADMIN_ID_SET,
                DATA_READ_ONLY_ID_SET);

        // Verify created folder.
        Folder folderToCreate = (Folder) entitiesToCreateList.get(2);
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
        ex3Config.setParticipantVersionDemographicsTableId(PARTICIPANT_VERSION_DEMOGRAPHICS_TABLE_ID);
        ex3Config.setParticipantVersionDemographicsViewId(PARTICIPANT_VERSION_DEMOGRAPHICS_VIEW_ID);
        ex3Config.setProjectId(PROJECT_ID);
        ex3Config.setRawDataFolderId(RAW_FOLDER_ID);
        ex3Config.setStorageLocationId(STORAGE_LOCATION_ID);
        return ex3Config;
    }

    @Test
    public void sendExportNotifications_sendNotifications() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verifyExportToAppNotification();
        verifyExportToStudy1Notification();
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_errorHandling() throws Exception {
        // Test case: Error in app and study1 notifications, study2 notification still sends.

        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        when(mockSnsClient.publish(eq(EXPORT_NOTIFICATION_FOR_APP_ARN), any())).thenThrow(RuntimeException.class);
        when(mockSnsClient.publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any())).thenThrow(RuntimeException.class);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verify(mockSnsClient).publish(eq(EXPORT_NOTIFICATION_FOR_APP_ARN), any());
        verify(mockSnsClient).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any());
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void sendExportNotifications_invalidNotification() {
        // Make a blank export notification, which is invalid.
        exporter3Service.sendExportNotifications(new ExportToAppNotification());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void sendExportNotifications_exportNotEnabled() {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        app.setExporter3Enabled(false);

        // Execute - Throws.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());
    }

    @Test
    public void sendExportNotifications_noAppEx3Config() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        app.setExporter3Configuration(null);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_APP_ARN), any());
        verifyExportToStudy1Notification();
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_noAppTopicArn() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        app.getExporter3Configuration().setExportNotificationTopicArn(null);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_APP_ARN), any());
        verifyExportToStudy1Notification();
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_studyNotFound() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        when(mockStudyService.getStudy(eq(TEST_APP_ID), eq(STUDY_1_ID), anyBoolean())).thenReturn(null);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verifyExportToAppNotification();
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any());
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_studyExportNotEnabled() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        study1.setExporter3Enabled(false);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verifyExportToAppNotification();
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any());
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_studyNoEx3Config() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        study1.setExporter3Configuration(null);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verifyExportToAppNotification();
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any());
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    @Test
    public void sendExportNotifications_studyNoTopicArn() throws Exception {
        // Setup.
        App app = App.create();
        Study study1 = Study.create();
        Study study2 = Study.create();
        setupExportNotificationTest(app, study1, study2);

        study1.getExporter3Configuration().setExportNotificationTopicArn(null);

        // Execute.
        exporter3Service.sendExportNotifications(makeExportToAppNotification());

        // Validate SNS.
        verifyExportToAppNotification();
        verify(mockSnsClient, never()).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), any());
        verifyExportToStudy2Notification();
        verifyNoMoreInteractions(mockSnsClient);
    }

    private void setupExportNotificationTest(App app, Study study1, Study study2) {
        // Set up app.
        Exporter3Configuration appEx3Config = makeConfiguredEx3Config();
        appEx3Config.setExportNotificationTopicArn(EXPORT_NOTIFICATION_FOR_APP_ARN);

        app.setIdentifier(TEST_APP_ID);
        app.setName(APP_NAME);
        app.setExporter3Enabled(true);
        app.setExporter3Configuration(appEx3Config);

        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // Setup study1.
        Exporter3Configuration study1Ex3Config = makeConfiguredEx3Config();
        study1Ex3Config.setExportNotificationTopicArn(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN);

        study1.setIdentifier(STUDY_1_ID);
        study1.setName(STUDY_1_ID);
        study1.setExporter3Enabled(true);
        study1.setExporter3Configuration(study1Ex3Config);

        when(mockStudyService.getStudy(eq(TEST_APP_ID), eq(STUDY_1_ID), anyBoolean())).thenReturn(study1);

        // Setup study2.
        Exporter3Configuration study2Ex3Config = makeConfiguredEx3Config();
        study2Ex3Config.setExportNotificationTopicArn(EXPORT_NOTIFICATION_FOR_STUDY_2_ARN);

        study2.setIdentifier(STUDY_2_ID);
        study2.setName(STUDY_2_ID);
        study2.setExporter3Enabled(true);
        study2.setExporter3Configuration(study2Ex3Config);

        when(mockStudyService.getStudy(eq(TEST_APP_ID), eq(STUDY_2_ID), anyBoolean())).thenReturn(study2);

        // Mock SNS publish. The result is only used for logging, but it has to exist.
        when(mockSnsClient.publish(any(), any())).thenReturn(new PublishResult());
    }

    private static ExportToAppNotification makeExportToAppNotification() {
        ExportToAppNotification notification = new ExportToAppNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setRecordId(RECORD_ID);

        ExportedRecordInfo appRecordInfo = new ExportedRecordInfo();
        appRecordInfo.setParentProjectId(APP_PARENT_PROJECT_ID);
        appRecordInfo.setRawFolderId(APP_RAW_FOLDER_ID);
        appRecordInfo.setFileEntityId(APP_FILE_ENTITY_ID);
        appRecordInfo.setS3Bucket(APP_S3_BUCKET);
        appRecordInfo.setS3Key(APP_S3_KEY);
        notification.setRecord(appRecordInfo);

        ExportedRecordInfo study1RecordInfo = new ExportedRecordInfo();
        study1RecordInfo.setParentProjectId(STUDY_1_PARENT_PROJECT_ID);
        study1RecordInfo.setRawFolderId(STUDY_1_RAW_FOLDER_ID);
        study1RecordInfo.setFileEntityId(STUDY_1_FILE_ENTITY_ID);
        study1RecordInfo.setS3Bucket(STUDY_1_S3_BUCKET);
        study1RecordInfo.setS3Key(STUDY_1_S3_KEY);

        ExportedRecordInfo study2RecordInfo = new ExportedRecordInfo();
        study2RecordInfo.setParentProjectId(STUDY_2_PARENT_PROJECT_ID);
        study2RecordInfo.setRawFolderId(STUDY_2_RAW_FOLDER_ID);
        study2RecordInfo.setFileEntityId(STUDY_2_FILE_ENTITY_ID);
        study2RecordInfo.setS3Bucket(STUDY_2_S3_BUCKET);
        study2RecordInfo.setS3Key(STUDY_2_S3_KEY);

        Map<String, ExportedRecordInfo> studyRecordMap =
                new ImmutableMap.Builder<String, ExportedRecordInfo>()
                        .put(STUDY_1_ID, study1RecordInfo)
                        .put(STUDY_2_ID, study2RecordInfo)
                        .build();
        notification.setStudyRecords(studyRecordMap);

        return notification;
    }

    private void verifyExportToAppNotification() throws JsonProcessingException {
        ArgumentCaptor<String> appNotificationJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSnsClient).publish(eq(EXPORT_NOTIFICATION_FOR_APP_ARN), appNotificationJsonCaptor.capture());

        ExportToAppNotification appNotification = BridgeObjectMapper.get().readValue(
                appNotificationJsonCaptor.getValue(), ExportToAppNotification.class);
        assertEquals(appNotification.getAppId(), TEST_APP_ID);
        assertEquals(appNotification.getRecordId(), RECORD_ID);

        ExportedRecordInfo appRecordInfo = appNotification.getRecord();
        assertEquals(appRecordInfo.getParentProjectId(), APP_PARENT_PROJECT_ID);
        assertEquals(appRecordInfo.getRawFolderId(), APP_RAW_FOLDER_ID);
        assertEquals(appRecordInfo.getFileEntityId(), APP_FILE_ENTITY_ID);
        assertEquals(appRecordInfo.getS3Bucket(), APP_S3_BUCKET);
        assertEquals(appRecordInfo.getS3Key(), APP_S3_KEY);

        Map<String, ExportedRecordInfo> studyRecordMap = appNotification.getStudyRecords();

        ExportedRecordInfo study1RecordInfo = studyRecordMap.get(STUDY_1_ID);
        assertEquals(study1RecordInfo.getParentProjectId(), STUDY_1_PARENT_PROJECT_ID);
        assertEquals(study1RecordInfo.getRawFolderId(), STUDY_1_RAW_FOLDER_ID);
        assertEquals(study1RecordInfo.getFileEntityId(), STUDY_1_FILE_ENTITY_ID);
        assertEquals(study1RecordInfo.getS3Bucket(), STUDY_1_S3_BUCKET);
        assertEquals(study1RecordInfo.getS3Key(), STUDY_1_S3_KEY);

        ExportedRecordInfo study2RecordInfo = studyRecordMap.get(STUDY_2_ID);
        assertEquals(study2RecordInfo.getParentProjectId(), STUDY_2_PARENT_PROJECT_ID);
        assertEquals(study2RecordInfo.getRawFolderId(), STUDY_2_RAW_FOLDER_ID);
        assertEquals(study2RecordInfo.getFileEntityId(), STUDY_2_FILE_ENTITY_ID);
        assertEquals(study2RecordInfo.getS3Bucket(), STUDY_2_S3_BUCKET);
        assertEquals(study2RecordInfo.getS3Key(), STUDY_2_S3_KEY);
    }

    private void verifyExportToStudy1Notification() throws JsonProcessingException {
        ArgumentCaptor<String> study1NotificationJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSnsClient).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_1_ARN), study1NotificationJsonCaptor.capture());

        ExportToStudyNotification study1Notification = BridgeObjectMapper.get().readValue(
                study1NotificationJsonCaptor.getValue(), ExportToStudyNotification.class);
        assertEquals(study1Notification.getAppId(), TEST_APP_ID);
        assertEquals(study1Notification.getStudyId(), STUDY_1_ID);
        assertEquals(study1Notification.getRecordId(), RECORD_ID);
        assertEquals(study1Notification.getParentProjectId(), STUDY_1_PARENT_PROJECT_ID);
        assertEquals(study1Notification.getRawFolderId(), STUDY_1_RAW_FOLDER_ID);
        assertEquals(study1Notification.getFileEntityId(), STUDY_1_FILE_ENTITY_ID);
        assertEquals(study1Notification.getS3Bucket(), STUDY_1_S3_BUCKET);
        assertEquals(study1Notification.getS3Key(), STUDY_1_S3_KEY);
    }

    private void verifyExportToStudy2Notification() throws JsonProcessingException {
        ArgumentCaptor<String> study2NotificationJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSnsClient).publish(eq(EXPORT_NOTIFICATION_FOR_STUDY_2_ARN), study2NotificationJsonCaptor.capture());

        ExportToStudyNotification study2Notification = BridgeObjectMapper.get().readValue(
                study2NotificationJsonCaptor.getValue(), ExportToStudyNotification.class);
        assertEquals(study2Notification.getAppId(), TEST_APP_ID);
        assertEquals(study2Notification.getStudyId(), STUDY_2_ID);
        assertEquals(study2Notification.getRecordId(), RECORD_ID);
        assertEquals(study2Notification.getParentProjectId(), STUDY_2_PARENT_PROJECT_ID);
        assertEquals(study2Notification.getRawFolderId(), STUDY_2_RAW_FOLDER_ID);
        assertEquals(study2Notification.getFileEntityId(), STUDY_2_FILE_ENTITY_ID);
        assertEquals(study2Notification.getS3Bucket(), STUDY_2_S3_BUCKET);
        assertEquals(study2Notification.getS3Key(), STUDY_2_S3_KEY);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void subscribeToCreateStudyNotifications_InvalidSubscription() {
        exporter3Service.subscribeToCreateStudyNotifications(TestConstants.TEST_APP_ID,
                new ExporterSubscriptionRequest());
    }

    @Test
    public void subscribeToCreateStudyNotifications_ConfigureTopic() {
        // App has no exporter3config.
        setupSnsSubscribeTest(TOPIC_ARN_CREATE_STUDY);
        app.setExporter3Configuration(null);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service.subscribeToCreateStudyNotifications(
                TestConstants.TEST_APP_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        String topicName = Exporter3Service.TOPIC_PREFIX_CREATE_STUDY + '-' + TEST_APP_ID + '-' + BUCKET_SUFFIX;
        verify(mockSnsClient).createTopic(topicName);
        verifySnsSubscribe(TOPIC_ARN_CREATE_STUDY);

        ArgumentCaptor<App> updatedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppService).updateApp(updatedAppCaptor.capture(), eq(true));
        assertEquals(updatedAppCaptor.getValue().getExporter3Configuration().getCreateStudyNotificationTopicArn(),
                TOPIC_ARN_CREATE_STUDY);
    }

    @Test
    public void subscribeToCreateStudyNotifications_ExistingTopic() {
        // App has an exporter3config with a topic arn.
        setupSnsSubscribeTest(TOPIC_ARN_CREATE_STUDY);
        app.getExporter3Configuration().setCreateStudyNotificationTopicArn(TOPIC_ARN_CREATE_STUDY);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service.subscribeToCreateStudyNotifications(
                TestConstants.TEST_APP_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        verify(mockSnsClient, never()).createTopic(anyString());
        verify(mockAppService, never()).updateApp(any(), anyBoolean());
        verifySnsSubscribe(TOPIC_ARN_CREATE_STUDY);
    }

    @Test
    public void subscribeToExportNotificationsForApp_ConfigureTopic() {
        // App has no exporter3config.
        setupSnsSubscribeTest(TOPIC_ARN_EXPORT_FOR_APP);
        app.setExporter3Configuration(null);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service.subscribeToExportNotificationsForApp(
                TestConstants.TEST_APP_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        String topicName = Exporter3Service.TOPIC_PREFIX_EXPORT + '-' + TEST_APP_ID + '-' + BUCKET_SUFFIX;
        verify(mockSnsClient).createTopic(topicName);
        verifySnsSubscribe(TOPIC_ARN_EXPORT_FOR_APP);

        ArgumentCaptor<App> updatedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppService).updateApp(updatedAppCaptor.capture(), eq(true));
        assertEquals(updatedAppCaptor.getValue().getExporter3Configuration().getExportNotificationTopicArn(),
                TOPIC_ARN_EXPORT_FOR_APP);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void subscribeToExportNotificationsForStudy_InvalidSubscription() {
        exporter3Service.subscribeToExportNotificationsForStudy(TEST_APP_ID, TEST_STUDY_ID,
                new ExporterSubscriptionRequest());
    }

    @Test
    public void subscribeToExportNotificationsForStudy_CreateTopic() {
        // Study has no EX3 config.
        setupSnsSubscribeTest(TOPIC_ARN_EXPORT_FOR_STUDY);
        study.setExporter3Configuration(null);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service
                .subscribeToExportNotificationsForStudy(TEST_APP_ID, TEST_STUDY_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        String topicName = Exporter3Service.TOPIC_PREFIX_EXPORT + '-' + TEST_APP_ID + '-' + TEST_STUDY_ID + '-'
                + BUCKET_SUFFIX;
        verify(mockSnsClient).createTopic(topicName);
        verifySnsSubscribe(TOPIC_ARN_EXPORT_FOR_STUDY);

        ArgumentCaptor<Study> updatedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyService).updateStudy(eq(TEST_APP_ID), updatedStudyCaptor.capture());
        assertEquals(updatedStudyCaptor.getValue().getExporter3Configuration().getExportNotificationTopicArn(),
                TOPIC_ARN_EXPORT_FOR_STUDY);
    }

    @Test
    public void subscribeToExportNotificationsForStudy_ExistingTopic() {
        // Study has an exporter3config with a topic arn.
        setupSnsSubscribeTest(TOPIC_ARN_EXPORT_FOR_STUDY);
        study.getExporter3Configuration().setExportNotificationTopicArn(TOPIC_ARN_EXPORT_FOR_STUDY);

        // Execute.
        ExporterSubscriptionResult exporterSubscriptionResult = exporter3Service
                .subscribeToExportNotificationsForStudy(TEST_APP_ID, TEST_STUDY_ID, makeSubscriptionRequest());
        assertEquals(exporterSubscriptionResult.getSubscriptionArn(), SUBSCRIPTION_ARN);

        // Verify backends.
        verify(mockSnsClient, never()).createTopic(anyString());
        verify(mockStudyService, never()).updateStudy(any(), any());
        verifySnsSubscribe(TOPIC_ARN_EXPORT_FOR_STUDY);
    }

    private void setupSnsSubscribeTest(String topicArn) {
        // Set up app.
        app.setIdentifier(TEST_APP_ID);
        app.setName(APP_NAME);
        app.setExporter3Configuration(makeConfiguredEx3Config());

        // Setup study.
        study.setIdentifier(TEST_STUDY_ID);
        study.setName(TEST_STUDY_ID);
        study.setExporter3Enabled(true);
        study.setExporter3Configuration(makeConfiguredEx3Config());

        // Mock SNS.
        CreateTopicResult createTopicResult = new CreateTopicResult();
        createTopicResult.setTopicArn(topicArn);
        when(mockSnsClient.createTopic(anyString())).thenReturn(createTopicResult);

        SubscribeResult subscribeResult = new SubscribeResult();
        subscribeResult.setSubscriptionArn(SUBSCRIPTION_ARN);
        when(mockSnsClient.subscribe(any())).thenReturn(subscribeResult);
    }

    private static ExporterSubscriptionRequest makeSubscriptionRequest() {
        ExporterSubscriptionRequest request = new ExporterSubscriptionRequest();
        request.setAttributes(SUBSCRIPTION_ATTRIBUTES);
        request.setEndpoint(SUBSCRIPTION_ENDPOINT);
        request.setProtocol(SUBSCRIPTION_PROTOCOL);
        return request;
    }

    private void verifySnsSubscribe(String expectedTopicArn) {
        ArgumentCaptor<SubscribeRequest> snsSubscribeRequestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(mockSnsClient).subscribe(snsSubscribeRequestCaptor.capture());
        SubscribeRequest snsSubscribeRequest = snsSubscribeRequestCaptor.getValue();
        assertEquals(snsSubscribeRequest.getAttributes(), SUBSCRIPTION_ATTRIBUTES);
        assertEquals(snsSubscribeRequest.getEndpoint(), SUBSCRIPTION_ENDPOINT);
        assertEquals(snsSubscribeRequest.getProtocol(), SUBSCRIPTION_PROTOCOL);
        assertEquals(snsSubscribeRequest.getTopicArn(), expectedTopicArn);
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
    public void completeUpload_ClientInfo_FromUpload() throws Exception {
        // Set up inputs. For simplicity, just write the client info as a string, since this bypasses the JSON
        // serialization.
        Upload upload = Upload.create();
        upload.setClientInfo("dummy upload client info string");
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);
        upload.setUserAgent(TestConstants.UA);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Ensure there is no client info in the Request Context.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId(USER_ID)
                .withUserAgent(null).build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        assertEquals(recordToCreate.getClientInfo(), "dummy upload client info string");
        assertEquals(recordToCreate.getUserAgent(), TestConstants.UA);
    }

    @Test
    public void completeUpload_ClientInfo_FromUploaderNull() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Set RequestContext.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId(USER_ID)
                .withUserAgent(null).build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info. RequestContext automatically fills it in with ClientInfo.UNKNOWN_CLIENT.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        String clientInfoJsonText = recordToCreate.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, ClientInfo.UNKNOWN_CLIENT);
        assertNull(recordToCreate.getUserAgent());
    }

    @Test
    public void completeUpload_ClientInfo_FromUploader() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Set RequestContext.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId(USER_ID)
                .withUserAgent(TestConstants.UA).build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        String clientInfoJsonText = recordToCreate.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, CLIENT_INFO);
        assertEquals(recordToCreate.getUserAgent(), TestConstants.UA);
    }

    @Test
    public void completeUpload_ClientInfo_FromWorkerNullRequestInfo() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Set RequestContext.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId("worker-user").build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info. Defaults to ClientInfo.UNKNOWN_CLIENT.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        String clientInfoJsonText = recordToCreate.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, ClientInfo.UNKNOWN_CLIENT);
        assertNull(recordToCreate.getUserAgent());
    }

    @Test
    public void completeUpload_ClientInfo_FromWorkerNullClientInfo() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Set RequestContext.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId("worker-user").build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock RequestInfoService.
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(new RequestInfo());

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info. Defaults to ClientInfo.UNKNOWN_CLIENT.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        String clientInfoJsonText = recordToCreate.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, ClientInfo.UNKNOWN_CLIENT);
        assertNull(recordToCreate.getUserAgent());
    }

    @Test
    public void completeUpload_ClientInfo_FromWorker() throws Exception {
        // Set up inputs.
        Upload upload = Upload.create();
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);

        // Mock AccountService.
        Account account = Account.create();
        account.setId(USER_ID);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Set RequestContext.
        RequestContext requestContext = new RequestContext.Builder().withCallerUserId("worker-user").build();
        RequestContext.set(requestContext);

        // Mock HealthDataEx3Service.
        HealthDataRecordEx3 createdRecord = HealthDataRecordEx3.create();
        createdRecord.setId(RECORD_ID);
        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenReturn(createdRecord);

        // Mock RequestInfoService.
        RequestInfo requestInfo = new RequestInfo.Builder().withClientInfo(CLIENT_INFO).withUserAgent(TestConstants.UA)
                .build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Verify client info. Defaults to ClientInfo.UNKNOWN_CLIENT.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());

        HealthDataRecordEx3 recordToCreate = recordToCreateCaptor.getValue();
        String clientInfoJsonText = recordToCreate.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, CLIENT_INFO);
        assertEquals(recordToCreate.getUserAgent(), TestConstants.UA);
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

    @Test
    public void exportTimelineForStudy_1stTime() throws Exception {
        // Setup Study.
        study.setScheduleGuid(TestConstants.SCHEDULE_GUID);

        // Study has no exporter3config.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        // Mock Synapse resource creation.
        mockSynapseResourceCreation();

        // Mock Timeline
        Timeline.Builder builder = new Timeline.Builder();
        Timeline timeline = builder.withSchedule(schedule).build();
        when(mockSchedule2Service.getTimelineForSchedule(eq(TEST_APP_ID), eq(TestConstants.SCHEDULE_GUID)))
                .thenReturn(timeline);

        // Mock FileHandle
        CloudProviderFileHandleInterface markdown = new S3FileHandle();
        markdown.setStorageLocationId(STORAGE_LOCATION_ID);
        markdown.setId("exportedTimelineFor" + TestConstants.TEST_STUDY_ID);
        markdown.setFileName("timelineFor" + TestConstants.TEST_STUDY_ID + ".txt");
        ArgumentCaptor<File> fileToUploadCaptor = ArgumentCaptor.forClass(File.class);
        when(mockSynapseClient.multipartUpload(fileToUploadCaptor.capture(),any(), eq(false), eq(false)))
                .thenReturn(markdown);

        // Setup Wiki
        V2WikiPage wiki = new V2WikiPage();
        wiki.setId("wikiFor"  + TestConstants.TEST_STUDY_ID);
        wiki.setTitle("Exported Timeline for " + TestConstants.TEST_STUDY_ID);
        wiki.setMarkdownFileHandleId("exportedTimelineFor" + TestConstants.TEST_STUDY_ID);
        ArgumentCaptor<V2WikiPage> wikiToCreateCaptor = ArgumentCaptor.forClass(V2WikiPage.class);
        when(mockSynapseClient.createV2WikiPage(any(), eq(ObjectType.ENTITY), wikiToCreateCaptor.capture())).thenReturn(wiki);

        // Execute and verify output.
        Exporter3Configuration ex3Config = exporter3Service.exportTimelineForStudy(TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);

        assertEquals(ex3Config, study.getExporter3Configuration());
        verifyEx3ConfigAndSynapse(ex3Config, TestConstants.TEST_APP_ID + '/' +
                TestConstants.TEST_STUDY_ID);

        // Verify call to studyService twice: one in initExporter3ForStudy & one in exportTimelineForStudy.
        verify(mockStudyService, times(2)).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        verify(mockStudyService, times(2)).updateStudy(TEST_APP_ID, study);

        // Verify call to schedule2Service.
        verify(mockSchedule2Service).getTimelineForSchedule(TEST_APP_ID, TestConstants.SCHEDULE_GUID);

        // Verify call to SynapseHelper.
        verify(mockSynapseHelper).checkSynapseWritableOrThrow();
        // Verify call to SynapseClient.

        verify(mockSynapseClient).createV2WikiPage(eq(study.getExporter3Configuration().getProjectId()), eq(ObjectType.ENTITY), wikiToCreateCaptor.capture());

        // Verify file content
        JsonNode node = BridgeObjectMapper.get().valueToTree(timeline);
        String expectedFileContent = node.toString();
        File capturedFile = fileToUploadCaptor.getValue();
        CharSource charSource = Files.asCharSource(capturedFile, Charsets.UTF_8);
        try {
            String content = charSource.read();
            assertEquals(content, expectedFileContent);
        } catch (IOException e) {
            System.out.println("An error occurred while reading the file: " + e.getMessage());
        }

        // Verify WikiPage
        V2WikiPage capturedWikiPage = wikiToCreateCaptor.getValue();
        assertEquals(capturedWikiPage.getTitle(), wiki.getTitle());
        assertEquals(capturedWikiPage.getMarkdownFileHandleId(), wiki.getMarkdownFileHandleId());

        // Verify markdown
        assertTrue((capturedFile.getName().replace(".txt", "")
                 .contains(markdown.getFileName().replace(".txt", ""))));
    }

    @Test
    public void exportTimelineForStudy_2ndTime() throws Exception {
        // Setup Study.
        study.setScheduleGuid(TestConstants.SCHEDULE_GUID);

        // Study has no exporter3config.
        study.setExporter3Configuration(null);
        study.setExporter3Enabled(false);

        // Setup existing S3FileHandle
        CloudProviderFileHandleInterface existingMarkdown = new S3FileHandle();
        existingMarkdown.setStorageLocationId(STORAGE_LOCATION_ID);
        existingMarkdown.setId("1stExportedTimelineFor" + TestConstants.TEST_STUDY_ID);

        // Setup Wiki
        V2WikiPage wiki = new V2WikiPage();
        wiki.setId("wikiFor" + TestConstants.TEST_STUDY_ID);
        wiki.setTitle("Exported Timeline for " + TestConstants.TEST_STUDY_ID);
        wiki.setMarkdownFileHandleId(existingMarkdown.getId());

        // Setup Exporter3Config
        Exporter3Configuration exporter3Config = new Exporter3Configuration();
        exporter3Config.setWikiPageId(wiki.getId());
        exporter3Config.setStorageLocationId(STORAGE_LOCATION_ID);
        exporter3Config.setProjectId(PROJECT_ID);

        // Setup exporter3config for study.
        study.setExporter3Enabled(true);
        study.setExporter3Configuration(exporter3Config);

        // Mock Synapse resource creation.
        mockSynapseResourceCreation();

        // Mock Timeline
        Timeline.Builder builder = new Timeline.Builder();
        Timeline timeline = builder.withSchedule(schedule).build();
        when(mockSchedule2Service.getTimelineForSchedule(eq(TEST_APP_ID), eq(TestConstants.SCHEDULE_GUID)))
                .thenReturn(timeline);

        // Mock S3FileHandle
        CloudProviderFileHandleInterface markdown = new S3FileHandle();
        markdown.setStorageLocationId(8888L);
        markdown.setId("2ndExportedTimelineFor" + TestConstants.TEST_STUDY_ID);
        when(mockSynapseClient.multipartUpload(any(File.class), eq(STORAGE_LOCATION_ID), eq(false), eq(false))).thenReturn(markdown);

        // Mock WikiPageKey
        WikiPageKey key = new WikiPageKey();
        key.setOwnerObjectId(PROJECT_ID);
        key.setOwnerObjectType(ObjectType.ENTITY);
        key.setWikiPageId("wikiFor" + TestConstants.TEST_STUDY_ID);
        when(mockSynapseClient.getV2WikiPage(any(WikiPageKey.class))).thenReturn(wiki);

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.exportTimelineForStudy(TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);

        assertEquals(returnedEx3Config.getWikiPageId(), "wikiFor" + TestConstants.TEST_STUDY_ID);
        assertEquals(returnedEx3Config, study.getExporter3Configuration());

        // Verify call to schedule2Service.
        verify(mockSchedule2Service, times(1)).getTimelineForSchedule(TEST_APP_ID, TestConstants.SCHEDULE_GUID);

        // Verify call to SynapseHelper.
        verify(mockSynapseHelper, times(1)).checkSynapseWritableOrThrow();

        // Verify call to SynapseClient.
        ArgumentCaptor<WikiPageKey> WikiPageKeyCaptor = ArgumentCaptor.forClass(WikiPageKey.class);
        verify(mockSynapseClient).getV2WikiPage(WikiPageKeyCaptor.capture());
        ArgumentCaptor<V2WikiPage> wikiToCreateCaptor = ArgumentCaptor.forClass(V2WikiPage.class);
        verify(mockSynapseClient).updateV2WikiPage(eq(study.getExporter3Configuration().getProjectId()), eq(ObjectType.ENTITY), wikiToCreateCaptor.capture());

        // Verify WikiPageKey
        WikiPageKey capturedWikiPageKey = WikiPageKeyCaptor.getValue();
        assertEquals(capturedWikiPageKey.getOwnerObjectId(), key.getOwnerObjectId());
        assertEquals(capturedWikiPageKey.getOwnerObjectType(), key.getOwnerObjectType());
        assertEquals(capturedWikiPageKey.getWikiPageId(), key.getWikiPageId());

        // Verify updated WikiPage
        V2WikiPage capturedWikiPage = wikiToCreateCaptor.getValue();
        assertEquals(capturedWikiPage.getMarkdownFileHandleId(), markdown.getId());
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
