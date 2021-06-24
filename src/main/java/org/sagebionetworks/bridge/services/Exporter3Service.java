package org.sagebionetworks.bridge.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.EntityView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.synapse.SynapseHelper;
import org.sagebionetworks.bridge.time.DateUtils;

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
    // Helper inner class to store context that needs to be passed around for export.
    private static class ExportContext {
        private String hexMd5;
        private Map<String, String> metadataMap;

        /** Hex MD5. Synapse needs this to create File Handles. */
        public String getHexMd5() {
            return hexMd5;
        }

        public void setHexMd5(String hexMd5) {
            this.hexMd5 = hexMd5;
        }

        /** Metadata map. This lives in S3 as metadata and in Synapse as File Annotations. */
        public Map<String, String> getMetadataMap() {
            return metadataMap;
        }

        public void setMetadataMap(Map<String, String> metadataMap) {
            this.metadataMap = metadataMap;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Exporter3Service.class);

    // Package-scoped to be available in unit tests.
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_ID = "exporter.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_USER = "exporter.synapse.user";
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final String CONFIG_KEY_SYNAPSE_TRACKING_VIEW = "synapse.tracking.view";
    static final String CONFIG_KEY_TEAM_BRIDGE_ADMIN = "team.bridge.admin";
    static final String CONFIG_KEY_TEAM_BRIDGE_STAFF = "team.bridge.staff";
    static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";
    static final String FOLDER_NAME_BRIDGE_RAW_DATA = "Bridge Raw Data";
    static final String METADATA_KEY_CLIENT_INFO = "clientInfo";
    static final String METADATA_KEY_EXPORTED_ON = "exportedOn";
    static final String METADATA_KEY_HEALTH_CODE = "healthCode";
    static final String METADATA_KEY_RECORD_ID = "recordId";
    static final String METADATA_KEY_UPLOADED_ON = "uploadedOn";

    // Config attributes.
    private Long bridgeAdminTeamId;
    private Long bridgeStaffTeamId;
    private Long exporterSynapseId;
    private String exporterSynapseUser;
    private String rawHealthDataBucket;
    private String synapseTrackingViewId;
    private String uploadBucket;

    private AppService appService;
    private FileHelper fileHelper;
    private HealthDataEx3Service healthDataEx3Service;
    private DigestUtils md5DigestUtils;
    private S3Helper s3Helper;
    private SynapseHelper synapseHelper;
    private UploadArchiveService uploadArchiveService;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        bridgeAdminTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_ADMIN);
        bridgeStaffTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_STAFF);
        exporterSynapseId = (long) config.getInt(CONFIG_KEY_EXPORTER_SYNAPSE_ID);
        exporterSynapseUser = config.getProperty(CONFIG_KEY_EXPORTER_SYNAPSE_USER);
        rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
        synapseTrackingViewId = config.getProperty(CONFIG_KEY_SYNAPSE_TRACKING_VIEW);
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);
    }

    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    @Resource(name = "md5DigestUtils")
    public final void setMd5DigestUtils(DigestUtils md5DigestUtils) {
        this.md5DigestUtils = md5DigestUtils;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Resource(name="exporterSynapseHelper")
    public final void setSynapseHelper(SynapseHelper synapseHelper) {
        this.synapseHelper = synapseHelper;
    }

    @Autowired
    public final void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
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

        // Update app if necessary. (Don't need to update any admin fields.)
        if (isAppModified) {
            appService.updateApp(app, false);
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
        record = healthDataEx3Service.createOrUpdateRecord(record);

        // Export upload. Note that we complete the export synchronously in the request thread, because it doesn't take
        // that long, and app developers prefer synchronous.
        exportUpload(app, upload, record);
    }

    // This is separate because in the future, we might need a separate redrive process in the future.
    private void exportUpload(App app, Upload upload, HealthDataRecordEx3 record) {
        // Check that app is configured for export.
        Exporter3Configuration exporter3Config = app.getExporter3Configuration();
        if (exporter3Config == null || !exporter3Config.isConfigured()) {
            // Exporter not enabled. Skip.
            return;
        }

        String appId = app.getIdentifier();
        String uploadId = upload.getUploadId();
        try {
            // Set the exportedOn time on the record. This will be propagated to both S3 and Synapse.
            record.setExportedOn(DateUtils.getCurrentMillisFromEpoch());

            // Copy the file to the raw health data bucket. This includes folderization.
            ExportContext exportContext;
            if (upload.isEncrypted()) {
                exportContext = decryptAndUploadFile(upload, record);
            } else {
                exportContext = copyUploadToHealthDataBucket(upload, record);
            }

            // Upload to Synapse.
            exportToSynapse(exporter3Config, upload, exportContext);

            // Mark record as exported.
            record.setExported(true);
            healthDataEx3Service.createOrUpdateRecord(record);
        } catch (Exception ex) {
            LOG.error("Exporter 3 error processing upload, app=" + appId + ", upload=" + uploadId + ": " +
                    ex.getMessage(), ex);

            // Propagate 4XX erors.
            if (ex instanceof BridgeServiceException) {
                int status = ((BridgeServiceException) ex).getStatusCode();
                if (status >= 400 && status < 500) {
                    throw new BadRequestException(ex);
                }
            }

            throw new BridgeServiceException(ex);
        }
    }

    private ExportContext decryptAndUploadFile(Upload upload, HealthDataRecordEx3 record) throws IOException {
        String appId = upload.getAppId();
        String uploadId = upload.getUploadId();

        File tempDir = fileHelper.createTempDir();
        try {
            // Step 1: Download from S3.
            File downloadedFile = fileHelper.newFile(tempDir, uploadId);
            s3Helper.downloadS3File(uploadBucket, uploadId, downloadedFile);

            // Step 2: Decrypt - Stream from input file to output file.
            // Note: Neither FileHelper nor CmsEncryptor introduce any buffering. Since we're creating and closing
            // streams, it's our responsibility to add the buffered stream.
            File decryptedFile = fileHelper.newFile(tempDir, uploadId + "-decrypted");
            try (InputStream inputFileStream = getBufferedInputStream(fileHelper.getInputStream(downloadedFile));
                    InputStream decryptedInputFileStream = uploadArchiveService.decrypt(appId, inputFileStream);
                    OutputStream outputFileStream = new BufferedOutputStream(fileHelper.getOutputStream(
                            decryptedFile))) {
                ByteStreams.copy(decryptedInputFileStream, outputFileStream);
            }

            // Step 3: Upload it to the raw uploads bucket.
            Map<String, String> metadataMap = makeMetadataFromRecord(record);
            ObjectMetadata s3Metadata = makeS3Metadata(upload, metadataMap);
            String s3Key = getRawS3KeyForUpload(upload);
            s3Helper.writeFileToS3(rawHealthDataBucket, s3Key, decryptedFile, s3Metadata);

            // Step 4: While we have the file on disk, calculate the MD5 (hex-encoded). We'll need this for Synapse.
            byte[] md5 = md5DigestUtils.digest(decryptedFile);
            String hexMd5 = Hex.encodeHexString(md5);

            ExportContext exportContext = new ExportContext();
            exportContext.setHexMd5(hexMd5);
            exportContext.setMetadataMap(metadataMap);
            return exportContext;
        } finally {
            // Cleanup: Delete the temp dir.
            try {
                fileHelper.deleteDirRecursively(tempDir);
            } catch (IOException ex) {
                LOG.error("Error deleting temp dir " + tempDir.getAbsolutePath() + " for app=" + appId + ", upload=" +
                        uploadId + ": " + ex.getMessage(), ex);
            }
        }
    }

    private ExportContext copyUploadToHealthDataBucket(Upload upload, HealthDataRecordEx3 record) {
        String uploadId = upload.getUploadId();

        // Copy the file to the health data bucket.
        Map<String, String> metadataMap = makeMetadataFromRecord(record);
        ObjectMetadata s3Metadata = makeS3Metadata(upload, metadataMap);
        String s3Key = getRawS3KeyForUpload(upload);
        s3Helper.copyS3File(uploadBucket, uploadId, rawHealthDataBucket, s3Key, s3Metadata);

        // The upload object has the MD5 in Base64 encoding. We need it in hex encoding.
        byte[] md5 = Base64.getDecoder().decode(upload.getContentMd5());
        String hexMd5 = Hex.encodeHexString(md5);

        ExportContext exportContext = new ExportContext();
        exportContext.setHexMd5(hexMd5);
        exportContext.setMetadataMap(metadataMap);
        return exportContext;
    }

    private Map<String, String> makeMetadataFromRecord(HealthDataRecordEx3 record) {
        Map<String, String> metadataMap = new HashMap<>();

        // Bridge-specific metadata.
        metadataMap.put(METADATA_KEY_CLIENT_INFO, RequestContext.get().getCallerClientInfo().toString());
        metadataMap.put(METADATA_KEY_EXPORTED_ON, DateUtils.convertToISODateTime(record.getExportedOn()));
        metadataMap.put(METADATA_KEY_HEALTH_CODE, record.getHealthCode());
        metadataMap.put(METADATA_KEY_RECORD_ID, record.getId());
        metadataMap.put(METADATA_KEY_UPLOADED_ON, DateUtils.convertToISODateTime(record.getCreatedOn()));

        // App-provided metadata.
        Map<String, String> recordMetadataMap = record.getMetadata();
        if (recordMetadataMap != null) {
            for (Map.Entry<String, String> metadataEntry : record.getMetadata().entrySet()) {
                metadataMap.put(metadataEntry.getKey(), metadataEntry.getValue());
            }
        }

        // In the future, assessment stuff, study-specific stuff, and participant version would go here.

        return metadataMap;
    }

    private ObjectMetadata makeS3Metadata(Upload upload, Map<String, String> metadataMap) {
        // Always specify S3 encryption.
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        metadata.setContentType(upload.getContentType());

        // Copy metadata from map.
        for (Map.Entry<String, String> metadataEntry : metadataMap.entrySet()) {
            metadata.addUserMetadata(metadataEntry.getKey(), metadataEntry.getValue());
        }

        return metadata;
    }

    private void exportToSynapse(Exporter3Configuration exporter3Config, Upload upload, ExportContext exportContext)
            throws SynapseException {
        // Exports are folderized by calendar date (YYYY-MM-DD). Create that folder if it doesn't already exist.
        String dateStr = getCalendarDateForUpload(upload);
        String folderId = synapseHelper.createFolderIfNotExists(exporter3Config.getRawDataFolderId(), dateStr);

        String filename = getFilenameForUpload(upload);
        String s3Key = getRawS3KeyForUpload(upload);

        // Create Synapse S3 file handle.
        S3FileHandle fileHandle = new S3FileHandle();
        fileHandle.setBucketName(rawHealthDataBucket);
        fileHandle.setContentType(upload.getContentType());
        fileHandle.setFileName(filename);
        fileHandle.setKey(s3Key);
        fileHandle.setStorageLocationId(exporter3Config.getStorageLocationId());

        // This is different because our upload takes in Base64 MD5, but Synapse needs a hexadecimal MD5. This was
        // pre-computed in a previous step and passed in.
        fileHandle.setContentMd5(exportContext.getHexMd5());

        fileHandle = synapseHelper.createS3FileHandleWithRetry(fileHandle);

        // Create FileEntity.
        FileEntity fileEntity = new FileEntity();
        fileEntity.setDataFileHandleId(fileHandle.getId());
        fileEntity.setName(filename);
        fileEntity.setParentId(folderId);
        fileEntity = synapseHelper.createEntityWithRetry(fileEntity);
        String fileEntityId = fileEntity.getId();

        // Add annotations.
        Map<String, AnnotationsValue> annotationMap = new HashMap<>();
        for (Map.Entry<String, String> metadataEntry : exportContext.getMetadataMap().entrySet()) {
            AnnotationsValue value = new AnnotationsValue();
            value.setType(AnnotationsValueType.STRING);
            value.setValue(ImmutableList.of(metadataEntry.getValue()));
            annotationMap.put(metadataEntry.getKey(), value);
        }
        synapseHelper.addAnnotationsToEntity(fileEntityId, annotationMap);
    }

    private String getRawS3KeyForUpload(Upload upload) {
        String filename = getFilenameForUpload(upload);
        String dateStr = getCalendarDateForUpload(upload);
        return upload.getAppId() + '/' + dateStr + '/' + filename;
    }

    private String getFilenameForUpload(Upload upload) {
        return upload.getUploadId() + '-' + upload.getFilename();
    }

    private String getCalendarDateForUpload(Upload upload) {
        long millis = upload.getCompletedOn();
        LocalDate localDate = new LocalDate(millis, BridgeConstants.LOCAL_TIME_ZONE);
        return localDate.toString();
    }

    // This helper method wraps a stream inside a buffered stream. It exists because our unit tests use
    // InMemoryFileHelper, which uses a ByteArrayInputStream, which ignores closing. But in Prod, we need to wrap it in
    // a BufferedInputStream because the files can get big, and a closed BufferedInputStream breaks unit tests.
    //
    // Note that OutputStream has no such limitation, since InMemoryFileHelper intercepts the output.
    InputStream getBufferedInputStream(InputStream inputStream) {
        return new BufferedInputStream(inputStream);
    }
}
