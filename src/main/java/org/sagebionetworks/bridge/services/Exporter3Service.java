package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.EntityView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.studies.Study;
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
    static final String CONFIG_KEY_ADMIN_SYNAPSE_ID = "admin.synapse.id";
    static final String CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID = "downstream.etl.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_ID = "exporter.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_USER = "exporter.synapse.user";
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final String CONFIG_KEY_SYNAPSE_TRACKING_VIEW = "synapse.tracking.view";
    static final String CONFIG_KEY_TEAM_BRIDGE_ADMIN = "team.bridge.admin";
    static final String CONFIG_KEY_TEAM_BRIDGE_STAFF = "team.bridge.staff";
    static final String FOLDER_NAME_BRIDGE_RAW_DATA = "Bridge Raw Data";
    static final String TABLE_NAME_PARTICIPANT_VERSIONS = "Participant Versions";
    static final String WORKER_NAME_EXPORTER_3 = "Exporter3Worker";

    static final List<ColumnModel> PARTICIPANT_VERSION_COLUMN_MODELS;
    static {
        ImmutableList.Builder<ColumnModel> listBuilder = ImmutableList.builder();

        // Original healthcodes are GUIDs. These are 36 characters long.
        ColumnModel healthCodeColumn = new ColumnModel();
        healthCodeColumn.setName("healthCode");
        healthCodeColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(36L);
        listBuilder.add(healthCodeColumn);

        ColumnModel participantVersionColumn = new ColumnModel();
        participantVersionColumn.setName("participantVersion");
        participantVersionColumn.setColumnType(ColumnType.INTEGER);
        listBuilder.add(participantVersionColumn);

        ColumnModel createdOnColumn = new ColumnModel();
        createdOnColumn.setName("createdOn");
        createdOnColumn.setColumnType(ColumnType.DATE);
        listBuilder.add(createdOnColumn);

        ColumnModel modifiedOnColumn = new ColumnModel();
        modifiedOnColumn.setName("modifiedOn");
        modifiedOnColumn.setColumnType(ColumnType.DATE);
        listBuilder.add(modifiedOnColumn);

        // We have pre-existing validation that checks that the total length of all data groups is less than
        // 250 characters. This validation predates the use of string lists in Synapse. Additionally, some apps have
        // 30+ data groups, and some apps have data groups with 20+ characters. Synapse table row width is valuable, so
        // instead of reserving potentially 600+ characters that will be sparsely used, we will keep to the
        // 250 character limit.
        ColumnModel dataGroupsColumn = new ColumnModel();
        dataGroupsColumn.setName("dataGroups");
        dataGroupsColumn.setColumnType(ColumnType.STRING);
        dataGroupsColumn.setMaximumSize(250L);
        listBuilder.add(dataGroupsColumn);

        // These are 5-character language codes (eg "en-US", "es-ES"). Let's generously allow participants to export up to
        // 10 languages, which is 50 characters at most, and should be more than enough for nearly all participants.
        ColumnModel languagesColumn = new ColumnModel();
        languagesColumn.setName("languages");
        languagesColumn.setColumnType(ColumnType.STRING_LIST);
        languagesColumn.setMaximumSize(5L);
        languagesColumn.setMaximumListLength(10L);
        listBuilder.add(languagesColumn);

        // The longest sharing scope is ALL_QUALIFIED_RESEARCHERS, which is 25 characters long.
        ColumnModel sharingScopeColumn = new ColumnModel();
        sharingScopeColumn.setName("sharingScope");
        sharingScopeColumn.setColumnType(ColumnType.STRING);
        sharingScopeColumn.setMaximumSize(25L);
        listBuilder.add(sharingScopeColumn);

        // Synapse doesn't have maps as a type in their Table model. For now, we serialize it as a string. In 2.0, we
        // set a max string length of 250 characters, and that seems to be fine.
        ColumnModel studyMembershipsColumn = new ColumnModel();
        studyMembershipsColumn.setName("studyMemberships");
        studyMembershipsColumn.setColumnType(ColumnType.STRING);
        studyMembershipsColumn.setMaximumSize(250L);
        listBuilder.add(studyMembershipsColumn);

        // The longest time zone I could find was 28 characters long. Let's add a bit of a buffer and say 40
        // characters. As long as Llanfair PG, Wales doesn't have its own time zone, this should be fine.
        ColumnModel timeZoneColumn = new ColumnModel();
        timeZoneColumn.setName("clientTimeZone");
        timeZoneColumn.setColumnType(ColumnType.STRING);
        timeZoneColumn.setMaximumSize(40L);
        listBuilder.add(timeZoneColumn);

        PARTICIPANT_VERSION_COLUMN_MODELS = listBuilder.build();
    }

    // Config attributes.
    private Long bridgeAdminTeamId;
    private Long bridgeStaffTeamId;
    private Long adminSynapseId;
    private Long downstreamEtlSynapseId;
    private Long exporterSynapseId;
    private String exporterSynapseUser;
    private String rawHealthDataBucket;
    private String synapseTrackingViewId;
    private String workerQueueUrl;

    private AccountService accountService;
    private AppService appService;
    private HealthDataEx3Service healthDataEx3Service;
    private ParticipantVersionService participantVersionService;
    private S3Helper s3Helper;
    private AmazonSQSClient sqsClient;
    private StudyService studyService;
    private SynapseHelper synapseHelper;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        bridgeAdminTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_ADMIN);
        bridgeStaffTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_STAFF);
        exporterSynapseId = (long) config.getInt(CONFIG_KEY_EXPORTER_SYNAPSE_ID);
        exporterSynapseUser = config.getProperty(CONFIG_KEY_EXPORTER_SYNAPSE_USER);
        rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
        synapseTrackingViewId = config.getProperty(CONFIG_KEY_SYNAPSE_TRACKING_VIEW);
        workerQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL);

        String adminSynapseIdStr = config.get(CONFIG_KEY_ADMIN_SYNAPSE_ID);
        if (StringUtils.isNotBlank(adminSynapseIdStr)) {
            adminSynapseId = Long.valueOf(adminSynapseIdStr);
        } else {
            adminSynapseId = null;
        }

        String downstreamEtlSynapseIdStr = config.get(CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID);
        if (StringUtils.isNotBlank(downstreamEtlSynapseIdStr)) {
            downstreamEtlSynapseId = Long.valueOf(downstreamEtlSynapseIdStr);
        } else {
            downstreamEtlSynapseId = null;
        }
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

    @Autowired
    public final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Autowired
    final void setSqsClient(AmazonSQSClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
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
    public Exporter3Configuration initExporter3(String appId) throws BridgeSynapseException, IOException,
            SynapseException {
        boolean isAppModified = false;
        App app = appService.getApp(appId);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = app.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            app.setExporter3Configuration(ex3Config);
            isAppModified = true;
        }

        boolean isConfigModified = initExporter3Internal(app.getName(), appId, ex3Config);
        if (isConfigModified) {
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

    /**
     * Initializes configs and Synapse resources for Exporter 3.0 for a study. This follows the same rules as
     * initializing for apps.
     */
    public Exporter3Configuration initExporter3ForStudy(String appId, String studyId) throws BridgeSynapseException,
            IOException, SynapseException {
        boolean isStudyModified = false;
        Study study = studyService.getStudy(appId, studyId, true);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = study.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            study.setExporter3Configuration(ex3Config);
            isStudyModified = true;
        }

        boolean isConfigModified = initExporter3Internal(study.getName(), appId + '/' + studyId, ex3Config);
        if (isConfigModified) {
            isStudyModified = true;
        }

        // Finally, enable Exporter 3.0 if it's not already enabled.
        if (!study.isExporter3Enabled()) {
            study.setExporter3Enabled(true);
            isStudyModified = true;
        }

        // Update study if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config and exporter3enabled.
        if (isStudyModified) {
            studyService.updateStudy(appId, study);
        }

        return ex3Config;
    }

    // Package-scoped for unit tests.
    // Parent name is the name of the app or study this is being initialized for.
    // Base key is the base S3 key for all files in S3. This is either [appID] or [appId]/[studyId] if this is for a
    // study.
    // This modifies the passed in Exporter3Config. Returns true if ex3Config was modified.
    boolean initExporter3Internal(String parentName, String baseKey, Exporter3Configuration ex3Config)
            throws BridgeSynapseException, IOException, SynapseException {
        boolean isModified = false;

        // Name in Synapse are globally unique, so we add a random token to the name to ensure it
        // doesn't conflict with an existing name. Also, Synapse names can only contain a certain
        // subset of characters. We've verified this name is acceptable for this transformation.
        String synapseName = BridgeUtils.toSynapseFriendlyName(parentName);
        String nameScopingToken = getNameScopingToken();

        // Create data access team.
        Long dataAccessTeamId = ex3Config.getDataAccessTeamId();
        if (dataAccessTeamId == null) {
            Team team = new Team();
            team.setName(synapseName + " Access Team " + nameScopingToken);
            team = synapseHelper.createTeamWithRetry(team);
            dataAccessTeamId = Long.parseLong(team.getId());
            LOG.info("Created Synapse team " + dataAccessTeamId);

            // As a temporary measure, add the admin ID as a manager of this team. This will allow us to manage
            // permissions manually until we have a solution for https://sagebionetworks.jira.com/browse/BRIDGE-3154
            if (adminSynapseId != null) {
                synapseHelper.inviteToTeam(dataAccessTeamId, adminSynapseId, true);
            }

            ex3Config.setDataAccessTeamId(dataAccessTeamId);
            isModified = true;
        }

        Set<Long> dataAdminIds = ImmutableSet.of(exporterSynapseId, bridgeAdminTeamId);

        Set<Long> dataReadOnlyIds = new HashSet<>();
        dataReadOnlyIds.add(bridgeStaffTeamId);
        dataReadOnlyIds.add(dataAccessTeamId);

        Set<Long> projectAdminIds = new HashSet<>();
        projectAdminIds.add(exporterSynapseId);
        projectAdminIds.add(bridgeAdminTeamId);

        Set<Long> projectReadOnlyIds = ImmutableSet.of(bridgeStaffTeamId, dataAccessTeamId);

        // Note: At the moment, we are not able to create additional accounts in Synapse Dev. Some environments might
        // not have these accounts set up yet, so we need to be able to handle these configs not present.
        if (adminSynapseId != null) {
            projectAdminIds.add(adminSynapseId);
        }
        if (downstreamEtlSynapseId != null) {
            dataReadOnlyIds.add(downstreamEtlSynapseId);
            projectAdminIds.add(downstreamEtlSynapseId);
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
            synapseHelper.createAclWithRetry(projectId, projectAdminIds, projectReadOnlyIds);

            ex3Config.setProjectId(projectId);
            isModified = true;

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

        // Create Participant Version Table.
        String participantVersionTableId = ex3Config.getParticipantVersionTableId();
        if (participantVersionTableId == null) {
            participantVersionTableId = synapseHelper.createTableWithColumnsAndAcls(PARTICIPANT_VERSION_COLUMN_MODELS,
                    dataReadOnlyIds, dataAdminIds, projectId, TABLE_NAME_PARTICIPANT_VERSIONS);
            LOG.info("Created Synapse table " + participantVersionTableId);

            ex3Config.setParticipantVersionTableId(participantVersionTableId);
            isModified = true;
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
            synapseHelper.createAclWithRetry(rawDataFolderId, dataAdminIds, dataReadOnlyIds);

            ex3Config.setRawDataFolderId(rawDataFolderId);
            isModified = true;
        }

        // Create storage location.
        Long storageLocationId = ex3Config.getStorageLocationId();
        if (storageLocationId == null) {
            // Create owner.txt so that we can create the storage location.
            s3Helper.writeLinesToS3(rawHealthDataBucket, baseKey + "/owner.txt",
                    ImmutableList.of(exporterSynapseUser));

            // Create storage location.
            ExternalS3StorageLocationSetting storageLocation = new ExternalS3StorageLocationSetting();
            storageLocation.setBaseKey(baseKey);
            storageLocation.setBucket(rawHealthDataBucket);
            storageLocation.setStsEnabled(true);
            storageLocation = synapseHelper.createStorageLocationForEntity(rawDataFolderId, storageLocation);
            storageLocationId = storageLocation.getStorageLocationId();
            LOG.info("Created Synapse storage location " + storageLocationId);

            ex3Config.setStorageLocationId(storageLocationId);
            isModified = true;
        }

        return isModified;
    }

    // Package-scoped for unit tests.
    String getNameScopingToken() {
        return SecureTokenGenerator.NAME_SCOPE_INSTANCE.nextToken();
    }

    /** Complete an upload for Exporter 3.0, and also export that upload. */
    public void completeUpload(App app, Upload upload) {
        String appId = app.getIdentifier();
        String healthCode = upload.getHealthCode();

        // Create record.
        HealthDataRecordEx3 record = HealthDataRecordEx3.createFromUpload(upload);

        // Mark record with sharing scope.
        Account account = accountService.getAccount(AccountId.forHealthCode(appId,
                healthCode)).orElseThrow(() -> {
            // This should never happen. If it does, log a warning and throw.
            LOG.warn("Account disappeared in the middle of upload, healthCode=" + healthCode + ", appId="
                    + appId + ", uploadId=" + upload.getUploadId());
            return new EntityNotFoundException(StudyParticipant.class);
        });
        SharingScope sharingScope = account.getSharingScope();
        record.setSharingScope(sharingScope);

        // Also mark with the latest participant version.
        Optional<ParticipantVersion> participantVersion = participantVersionService
                .getLatestParticipantVersionForHealthCode(appId, healthCode);
        if (participantVersion.isPresent()) {
            record.setParticipantVersion(participantVersion.get().getParticipantVersion());
        }

        // If the record already exists (for example, this is a redrive), we need to set the version attribute properly
        // so we overwrite the old record properly.
        Optional<HealthDataRecordEx3> oldRecord = healthDataEx3Service.getRecord(upload.getUploadId());
        if (oldRecord.isPresent()) {
            record.setVersion(oldRecord.get().getVersion());
        }

        // Save record.
        record = healthDataEx3Service.createOrUpdateRecord(record);

        if (sharingScope != SharingScope.NO_SHARING) {
            exportUpload(appId, record.getId());
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
