package org.sagebionetworks.bridge.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import javax.annotation.Resource;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.s3.S3Helper;

// todo doc
// todo this is synchronous because it doesn't take that long, and app developers prefer synchronous
// todo we specifically want to have a clean separation Exporter v2. Lots of this code is similar to the code in
// Exporter v2.
public class Exporter3Service {
    private static final Logger LOG = LoggerFactory.getLogger(Exporter3Service.class);

    // Package-scoped to be available in unit tests.
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";
    static final String METADATA_KEY_APP_ID = "appId";
    static final String METADATA_KEY_CLIENT_INFO = "clientInfo";
    static final String METADATA_KEY_CREATED_ON = "createdOn";
    static final String METADATA_KEY_HEALTH_CODE = "healthCode";
    static final String METADATA_KEY_RECORD_ID = "recordId";
    static final String METADATA_KEY_STUDY_ID = "studyId";

    // Config attributes.
    private String rawHealthDataBucket;
    private String uploadBucket;

    private FileHelper fileHelper;
    private DigestUtils md5DigestUtils;
    private S3Helper s3Helper;
    private UploadArchiveService uploadArchiveService;
    private UploadDao uploadDao;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);
    }

    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    @Resource(name = "md5DigestUtils")
    public final void setMd5DigestUtils(DigestUtils md5DigestUtils) {
        this.md5DigestUtils = md5DigestUtils;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    @Autowired
    public final void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /*
new Handler to upload to S3 and write metadata as S3 metadata

new Handler to create Synapse FileHandles with annotations

new Handler to write to SNS topics

Note: folderize uploads by date
     */
    public void processUpload(Upload upload) {
        // Create temp dir.
        String appId = upload.getAppId();
        String uploadId = upload.getUploadId();

        try {
            String md5;
            if (upload.isEncrypted()) {
                md5 = decryptAndUploadFile(upload);
            } else {
                md5 = copyUploadToHealthDataBucket(upload);
            }
        } catch (Exception ex) {
            LOG.error("Exporter 3 error processing upload, app=" + appId + ", upload=" + uploadId + ": " +
                    ex.getMessage(), ex);

            // Clean-up before re-throwing.
            cleanup(upload, false);

            // Propagate 4XX erors.
            if (ex instanceof BridgeServiceException) {
                int status = ((BridgeServiceException) ex).getStatusCode();
                if (status >= 400 && status < 500) {
                    throw new BadRequestException(ex);
                }
            }

            throw new BridgeServiceException(ex);
        }
        cleanup(upload, true);
    }

    private String decryptAndUploadFile(Upload upload) throws IOException {
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
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            s3Helper.writeFileToS3(rawHealthDataBucket, uploadId + '-' + upload.getFilename(),
                    decryptedFile, metadata);

            // Step 4: While we have the file on disk, calculate the MD5 (hex-encoded). We'll need this for Synapse.
            byte[] md5 = md5DigestUtils.digest(decryptedFile);
            return Hex.encodeHexString(md5);
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

    private String copyUploadToHealthDataBucket(Upload upload) {
        String uploadId = upload.getUploadId();

        // Copy the file to the health data bucket.
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        s3Helper.copyS3File(uploadBucket, uploadId, rawHealthDataBucket,
                uploadId + '-' + upload.getFilename(), metadata);

        // The upload object has the MD5 in Base64 encoding. We need it in hex encoding.
        byte[] md5 = Base64.getDecoder().decode(upload.getContentMd5());
        return Hex.encodeHexString(md5);
    }

    private ObjectMetadata makeS3Metadata(Upload upload) {
        // Always specify S3 encryption.
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        // Bridge-specific metadata.
        /*
    static final String METADATA_KEY_CREATED_ON = "createdOn";
    static final String METADATA_KEY_HEALTH_CODE = "healthCode";
    static final String METADATA_KEY_RECORD_ID = "recordId";
    static final String METADATA_KEY_STUDY_ID = "studyId";
         */
        metadata.addUserMetadata(METADATA_KEY_APP_ID, upload.getAppId());
        metadata.addUserMetadata(METADATA_KEY_CLIENT_INFO, BridgeUtils.getRequestContext().getCallerClientInfo()
                        .toString());
        //todo
    }

    private void cleanup(Upload upload, boolean success) {
        String appId = upload.getAppId();
        String uploadId = upload.getUploadId();

        // Log status for posterity.
        UploadStatus status = success ? UploadStatus.SUCCEEDED : UploadStatus.VALIDATION_FAILED;
        LOG.info("Exporter 3 finished processing upload, app=" + appId + ", upload=" + uploadId + " with status " +
                status);

        // Write validation status to the upload DAO.
        try {
            // Message list is not used in Exporter 3. But we can't store null messages, so we must use an empty list.
            uploadDao.writeValidationStatus(upload, status, ImmutableList.of(), uploadId);
        } catch (RuntimeException ex) {
            // We don't want an error writing the validation status to squelch errors processing the upload. So log
            // the error here and don't pass it up the call stack.
            LOG.error("Exporter 3 error writing valudation status, app=" + appId + ", upload=" + uploadId + ": " +
                            ex.getMessage(), ex);
        }
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
