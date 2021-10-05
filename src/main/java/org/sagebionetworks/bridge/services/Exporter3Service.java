package org.sagebionetworks.bridge.services;

import java.io.IOException;
import javax.annotation.Resource;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.EntityView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.worker.Exporter3Request;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.synapse.SynapseHelper;

/**
 * <p>
 * Service that supports Exporter 3.0. Includes methods to initialize Exporter resources and to complete and export
 * uploads for 3.0.
 * </p>
 * <p>
 * We specifically want to have code separation between Exporter 2.0 and Exporter 3.0, to allow us to make updates to
 * Exporter 3.0 without getting bogged down by legacy code.
 * </p>
 */
@Component
public class Exporter3Service {
    private static final Logger LOG = LoggerFactory.getLogger(Exporter3Service.class);

    // Package-scoped to be available in unit tests.
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_ID = "exporter.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_USER = "exporter.synapse.user";
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final String CONFIG_KEY_SYNAPSE_TRACKING_VIEW = "synapse.tracking.view";
    static final String CONFIG_KEY_TEAM_BRIDGE_ADMIN = "team.bridge.admin";
    static final String CONFIG_KEY_TEAM_BRIDGE_STAFF = "team.bridge.staff";
    static final String CONFIG_KEY_WORKER_SQS_URL = "workerPlatform.request.sqs.queue.url";
    static final String FOLDER_NAME_BRIDGE_RAW_DATA = "Bridge Raw Data";
    static final String WORKER_NAME_EXPORTER_3 = "Exporter3Worker";

    // Config attributes.
    private Long bridgeAdminTeamId;
    private Long bridgeStaffTeamId;
    private Long exporterSynapseId;
    private String exporterSynapseUser;
    private String rawHealthDataBucket;
    private String synapseTrackingViewId;
    private String workerQueueUrl;

    private AccountService accountService;
    private AppService appService;
    private HealthDataEx3Service healthDataEx3Service;
    private S3Helper s3Helper;
    private AmazonSQSClient sqsClient;
    private SynapseHelper synapseHelper;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        bridgeAdminTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_ADMIN);
        bridgeStaffTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_STAFF);
        exporterSynapseId = (long) config.getInt(CONFIG_KEY_EXPORTER_SYNAPSE_ID);
        exporterSynapseUser = config.getProperty(CONFIG_KEY_EXPORTER_SYNAPSE_USER);
        rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
        synapseTrackingViewId = config.getProperty(CONFIG_KEY_SYNAPSE_TRACKING_VIEW);
        workerQueueUrl = config.getProperty(CONFIG_KEY_WORKER_SQS_URL);
    }

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Autowired
    final void setSqsClient(AmazonSQSClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Resource(name="exporterSynapseHelper")
    public final void setSynapseHelper(SynapseHelper synapseHelper) {
        this.synapseHelper = synapseHelper;
    }

    /**
     * Initializes configs and Synapse resources for Exporter 3.0. Note that if any config already exists, this API
     * will simply ignore them. This allows for two notable scenarios
     * (a) Advanced users can use existing projects or data access teams for Exporter 3.0.
     * (b) If in the future, we add something new (like a notification queue, or a default view), we can re-run this
     * API to create the new stuff without affecting the old stuff.
     */
    public Exporter3Configuration initExporter3(String appId) throws IOException, SynapseException {
        boolean isAppModified = false;
        App app = appService.getApp(appId);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = app.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            app.setExporter3Configuration(ex3Config);
            isAppModified = true;
        }

        // Name in Synapse are globally unique, so we add a random token to the name to ensure it
        // doesn't conflict with an existing name. Also, Synapse names can only contain a certain
        // subset of characters. We've verified this name is acceptable for this transformation.
        String synapseName = BridgeUtils.toSynapseFriendlyName(app.getName());
        String nameScopingToken = getNameScopingToken();

        // Create data access team.
        Long dataAccessTeamId = ex3Config.getDataAccessTeamId();
        if (dataAccessTeamId == null) {
            Team team = new Team();
            team.setName(synapseName + " Access Team " + nameScopingToken);
            team = synapseHelper.createTeamWithRetry(team);
            dataAccessTeamId = Long.parseLong(team.getId());
            LOG.info("Created Synapse team " + dataAccessTeamId);

            ex3Config.setDataAccessTeamId(dataAccessTeamId);
            isAppModified = true;
        }

        // Create project.
        String projectId = ex3Config.getProjectId();
        if (projectId == null) {
            Project project = new Project();
            project.setName(synapseName + " Project " + nameScopingToken);
            project = synapseHelper.createEntityWithRetry(project);
            projectId = project.getId();
            LOG.info("Created Synapse project " + projectId);

            // Create ACLs for project.
            synapseHelper.createAclWithRetry(projectId, ImmutableSet.of(exporterSynapseId, bridgeAdminTeamId),
                    ImmutableSet.of(bridgeStaffTeamId, dataAccessTeamId));

            ex3Config.setProjectId(projectId);
            isAppModified = true;

            // We also need to add this project to the tracking view.
            if (StringUtils.isNotBlank(synapseTrackingViewId)) {
                try {
                    EntityView view = synapseHelper.getEntityWithRetry(synapseTrackingViewId, EntityView.class);
                    if (view != null) {
                        // For whatever reason, view.getScopes() doesn't include the "syn" prefix.
                        view.getScopeIds().add(projectId.substring(3));
                        synapseHelper.updateEntityWithRetry(view);
                    }
                } catch (SynapseException ex) {
                    LOG.error("Error adding new project " + projectId + " to tracking view " + synapseTrackingViewId +
                            ": " + ex.getMessage(), ex);
                }
            }
        }

        // Create Folder for raw data.
        String rawDataFolderId = ex3Config.getRawDataFolderId();
        if (rawDataFolderId == null) {
            Folder folder = new Folder();
            folder.setName(FOLDER_NAME_BRIDGE_RAW_DATA);
            folder.setParentId(projectId);
            folder = synapseHelper.createEntityWithRetry(folder);
            rawDataFolderId = folder.getId();
            LOG.info("Created Synapse folder " + rawDataFolderId);

            // Create ACLs for folder. This is a separate ACL because we don't want to allow people to modify the
            // raw data.
            synapseHelper.createAclWithRetry(rawDataFolderId, ImmutableSet.of(exporterSynapseId, bridgeAdminTeamId),
                    ImmutableSet.of(bridgeStaffTeamId, dataAccessTeamId));

            ex3Config.setRawDataFolderId(rawDataFolderId);
            isAppModified = true;
        }

        // Create storage location.
        Long storageLocationId = ex3Config.getStorageLocationId();
        if (storageLocationId == null) {
            // Create owner.txt so that we can create the storage location.
            s3Helper.writeLinesToS3(rawHealthDataBucket, appId + "/owner.txt",
                    ImmutableList.of(exporterSynapseUser));

            // Create storage location.
            ExternalS3StorageLocationSetting storageLocation = new ExternalS3StorageLocationSetting();
            storageLocation.setBaseKey(appId);
            storageLocation.setBucket(rawHealthDataBucket);
            storageLocation.setStsEnabled(true);
            storageLocation = synapseHelper.createStorageLocationForEntity(rawDataFolderId, storageLocation);
            storageLocationId = storageLocation.getStorageLocationId();
            LOG.info("Created Synapse storage location " + storageLocationId);

            ex3Config.setStorageLocationId(storageLocationId);
            isAppModified = true;
        }

        // Finally, enable Exporter 3.0 if it's not already enabled.
        if (!app.isExporter3Enabled()) {
            app.setExporter3Enabled(true);
            isAppModified = true;
        }

        // Update app if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config and exporter3enabled.
        if (isAppModified) {
            appService.updateApp(app, true);
        }

        return ex3Config;
    }

    // Package-scoped for unit tests.
    String getNameScopingToken() {
        return SecureTokenGenerator.NAME_SCOPE_INSTANCE.nextToken();
    }

    /** Complete an upload for Exporter 3.0, and also export that upload. */
    public void completeUpload(App app, Upload upload) {
        // Create record.
        HealthDataRecordEx3 record = HealthDataRecordEx3.createFromUpload(upload);

        // Mark record with sharing scope.
        Account account = accountService.getAccount(AccountId.forHealthCode(app.getIdentifier(),
                upload.getHealthCode())).orElseThrow(() -> {
            // This should never happen. If it does, log a warning and throw.
            LOG.warn("Account disappeared in the middle of upload, healthCode=" + upload.getHealthCode() + ", appId="
                    + app.getIdentifier() + ", uploadId=" + upload.getUploadId());
            return new EntityNotFoundException(StudyParticipant.class);
        });
        SharingScope sharingScope = account.getSharingScope();
        record.setSharingScope(sharingScope);

        // Save record.
        record = healthDataEx3Service.createOrUpdateRecord(record);

        if (sharingScope != SharingScope.NO_SHARING) {
            exportUpload(app.getIdentifier(), record.getId());
        }
    }

    // This is separate because we might need a separate redrive process in the future.
    private void exportUpload(String appId, String recordId) {
        // Create request.
        Exporter3Request exporter3Request = new Exporter3Request();
        exporter3Request.setAppId(appId);
        exporter3Request.setRecordId(recordId);

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(WORKER_NAME_EXPORTER_3);
        workerRequest.setBody(exporter3Request);

        // Convert request to JSON.
        ObjectMapper objectMapper = BridgeObjectMapper.get();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(workerRequest);
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch and re-throw for code hygiene.
            throw new BridgeServiceException("Error creating export request for app " + appId + " record " + recordId,
                    ex);
        }

        // Sent to SQS.
        SendMessageResult sqsResult = sqsClient.sendMessage(workerQueueUrl, requestJson);
        LOG.info("Sent export request for app " + appId + " record " + recordId + "; received message ID=" +
                sqsResult.getMessageId());
    }
}
