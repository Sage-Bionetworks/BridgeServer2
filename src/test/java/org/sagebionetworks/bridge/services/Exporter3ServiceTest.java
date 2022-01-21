package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.client.exceptions.UnknownSynapseServerException;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.table.TableEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.worker.Exporter3Request;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.synapse.SynapseHelper;

public class Exporter3ServiceTest {

    private static final String APP_NAME = "Test App";
    private static final long BRIDGE_ADMIN_TEAM_ID = 1111L;
    private static final long BRIDGE_STAFF_TEAM_ID = 2222L;
    private static final long DATA_ACCESS_TEAM_ID = 3333L;
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
    private static final String SYNAPSE_TRACKING_VIEW_ID = "syn8888";
    private static final String WORKER_QUEUE_URL = "http://example.com/dummy-sqs-url";

    private static final String EXPECTED_PROJECT_NAME = APP_NAME + " Project " + NAME_SCOPING_TOKEN;
    private static final String EXPECTED_TEAM_NAME = APP_NAME + " Access Team " + NAME_SCOPING_TOKEN;
    private static final Set<Long> ADMIN_PRINCIPAL_ID_SET = ImmutableSet.of(EXPORTER_SYNAPSE_ID, BRIDGE_ADMIN_TEAM_ID);
    private static final Set<Long> READ_ONLY_PRINCIPAL_ID_SET = ImmutableSet.of(BRIDGE_STAFF_TEAM_ID,
            DATA_ACCESS_TEAM_ID);

    private App app;

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
    private AmazonSQSClient mockSqsClient;

    @Mock
    private SynapseHelper mockSynapseHelper;

    @InjectMocks
    @Spy
    private Exporter3Service exporter3Service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock config. This is done separately because we need to set mock config params.
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_TEAM_BRIDGE_ADMIN)).thenReturn((int) BRIDGE_ADMIN_TEAM_ID);
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_TEAM_BRIDGE_STAFF)).thenReturn((int) BRIDGE_STAFF_TEAM_ID);
        when(mockConfig.getInt(Exporter3Service.CONFIG_KEY_EXPORTER_SYNAPSE_ID)).thenReturn((int) EXPORTER_SYNAPSE_ID);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_EXPORTER_SYNAPSE_USER))
                .thenReturn(EXPORTER_SYNAPSE_USER);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_RAW_HEALTH_DATA_BUCKET))
                .thenReturn(RAW_HEALTH_DATA_BUCKET);
        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_SYNAPSE_TRACKING_VIEW))
                .thenReturn(SYNAPSE_TRACKING_VIEW_ID);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL)).thenReturn(WORKER_QUEUE_URL);
        exporter3Service.setConfig(mockConfig);

        // Spy getNameScopingToken to make it easier to test.
        doReturn(NAME_SCOPING_TOKEN).when(exporter3Service).getNameScopingToken();

        // Mock app service. Override some app properties for ease of testing.
        app = TestUtils.getValidApp(Exporter3ServiceTest.class);
        app.setIdentifier(TestConstants.TEST_APP_ID);
        app.setName(APP_NAME);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);
    }

    @Test
    public void initExporter3() throws Exception {
        // App has no exporter3config.
        app.setExporter3Configuration(null);
        app.setExporter3Enabled(false);

        // Mock SynapseHelper.
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

        // Execute and verify output.
        Exporter3Configuration returnedEx3Config = exporter3Service.initExporter3(TestConstants.TEST_APP_ID);
        assertEquals(returnedEx3Config.getDataAccessTeamId().longValue(), DATA_ACCESS_TEAM_ID);
        assertEquals(returnedEx3Config.getParticipantVersionTableId(), PARTICIPANT_VERSION_TABLE_ID);
        assertEquals(returnedEx3Config.getProjectId(), PROJECT_ID);
        assertEquals(returnedEx3Config.getRawDataFolderId(), RAW_FOLDER_ID);
        assertEquals(returnedEx3Config.getStorageLocationId().longValue(), STORAGE_LOCATION_ID);

        // Verify created team.
        ArgumentCaptor<Team> teamToCreateCaptor = ArgumentCaptor.forClass(Team.class);
        verify(mockSynapseHelper).createTeamWithRetry(teamToCreateCaptor.capture());

        Team teamToCreate = teamToCreateCaptor.getValue();
        assertEquals(teamToCreate.getName(), EXPECTED_TEAM_NAME);

        // Verify created project. Note that we call this method again later, which is why we verify it twice now.
        ArgumentCaptor<Entity> entitiesToCreateCaptor = ArgumentCaptor.forClass(Project.class);
        verify(mockSynapseHelper, times(2)).createEntityWithRetry(entitiesToCreateCaptor
                .capture());
        List<Entity> entitiesToCreateList = entitiesToCreateCaptor.getAllValues();

        Project projectToCreate = (Project) entitiesToCreateList.get(0);
        assertEquals(projectToCreate.getName(), EXPECTED_PROJECT_NAME);

        // Verify project ACLs.
        verify(mockSynapseHelper).createAclWithRetry(PROJECT_ID, ADMIN_PRINCIPAL_ID_SET, READ_ONLY_PRINCIPAL_ID_SET);

        // Verify project added to tracking view. For whatever reason, view scope IDs don't include the "syn" prefix.
        ArgumentCaptor<EntityView> viewToUpdateCaptor = ArgumentCaptor.forClass(EntityView.class);
        verify(mockSynapseHelper).updateEntityWithRetry(viewToUpdateCaptor.capture());

        EntityView viewToUpdate = viewToUpdateCaptor.getValue();
        assertTrue(viewToUpdate.getScopeIds().contains(PROJECT_ID_WITHOUT_PREFIX));

        // Verify created participant version table.
        verify(mockSynapseHelper).createTableWithColumnsAndAcls(Exporter3Service.PARTICIPANT_VERSION_COLUMN_MODELS,
                READ_ONLY_PRINCIPAL_ID_SET, ADMIN_PRINCIPAL_ID_SET, PROJECT_ID,
                Exporter3Service.TABLE_NAME_PARTICIPANT_VERSIONS);

        // Verify created folder.
        Folder folderToCreate = (Folder) entitiesToCreateList.get(1);
        assertEquals(folderToCreate.getName(), Exporter3Service.FOLDER_NAME_BRIDGE_RAW_DATA);
        assertEquals(folderToCreate.getParentId(), PROJECT_ID);

        // Verify folder ACLs.
        verify(mockSynapseHelper).createAclWithRetry(RAW_FOLDER_ID, ADMIN_PRINCIPAL_ID_SET,
                READ_ONLY_PRINCIPAL_ID_SET);

        // Verify we write to S3 for the storage location.
        verify(mockS3Helper).writeLinesToS3(RAW_HEALTH_DATA_BUCKET, TestConstants.TEST_APP_ID + "/owner.txt",
                ImmutableList.of(EXPORTER_SYNAPSE_USER));

        // Verify created storage location.
        ArgumentCaptor<ExternalS3StorageLocationSetting> storageLocationToCreateCaptor =
                ArgumentCaptor.forClass(ExternalS3StorageLocationSetting.class);
        verify(mockSynapseHelper).createStorageLocationForEntity(eq(RAW_FOLDER_ID),
                storageLocationToCreateCaptor.capture());

        ExternalS3StorageLocationSetting storageLocationToCreate = storageLocationToCreateCaptor.getValue();
        assertEquals(storageLocationToCreate.getBaseKey(), TestConstants.TEST_APP_ID);
        assertEquals(storageLocationToCreate.getBucket(), RAW_HEALTH_DATA_BUCKET);
        assertTrue(storageLocationToCreate.getStsEnabled());

        //  Verify updated app.
        ArgumentCaptor<App> appToUpdateCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppService).updateApp(appToUpdateCaptor.capture(), eq((true)));

        App appToUpdate = appToUpdateCaptor.getValue();
        assertTrue(appToUpdate.isExporter3Enabled());

        Exporter3Configuration ex3ConfigToCreate = appToUpdate.getExporter3Configuration();
        assertEquals(ex3ConfigToCreate, returnedEx3Config);
    }

    @Test
    public void initExporter3_AlreadyConfigured() throws Exception {
        // App is already configured for Exporter 3.0.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        ex3Config.setDataAccessTeamId(DATA_ACCESS_TEAM_ID);
        ex3Config.setParticipantVersionTableId(PARTICIPANT_VERSION_TABLE_ID);
        ex3Config.setProjectId(PROJECT_ID);
        ex3Config.setRawDataFolderId(RAW_FOLDER_ID);
        ex3Config.setStorageLocationId(STORAGE_LOCATION_ID);

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
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        ex3Config.setDataAccessTeamId(DATA_ACCESS_TEAM_ID);
        ex3Config.setParticipantVersionTableId(PARTICIPANT_VERSION_TABLE_ID);
        ex3Config.setProjectId(null);
        ex3Config.setRawDataFolderId(RAW_FOLDER_ID);
        ex3Config.setStorageLocationId(STORAGE_LOCATION_ID);

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
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        ex3Config.setDataAccessTeamId(DATA_ACCESS_TEAM_ID);
        ex3Config.setParticipantVersionTableId(PARTICIPANT_VERSION_TABLE_ID);
        ex3Config.setProjectId(null);
        ex3Config.setRawDataFolderId(RAW_FOLDER_ID);
        ex3Config.setStorageLocationId(STORAGE_LOCATION_ID);

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
    public void completeUpload_NoParticipantVersion() {
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
        when(mockParticipantVersionService.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.empty());

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute.
        exporter3Service.completeUpload(app, upload);

        // Just verify that the saved record doesn't have a participant version.
        ArgumentCaptor<HealthDataRecordEx3> recordToCreateCaptor = ArgumentCaptor.forClass(HealthDataRecordEx3.class);
        verify(mockHealthDataEx3Service).createOrUpdateRecord(recordToCreateCaptor.capture());
        assertNull(recordToCreateCaptor.getValue().getParticipantVersion());
    }

    @Test
    public void completeUpload_NoSharing() {
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
    public void completeUpload_NoAccount() {
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
}
