package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_UPLOADS;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadataView;
import org.sagebionetworks.bridge.models.upload.UploadViewEx3;
import org.sagebionetworks.bridge.models.worker.UploadRedriveWorkerRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;
import org.sagebionetworks.bridge.validators.UploadValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class UploadService {

    private static Logger logger = LoggerFactory.getLogger(UploadService.class);

    static final long EXPIRATION = 24 * 60 * 60 * 1000; // 24 hours
    
    // package-scoped to be available in unit tests
    static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";
    static final String CONFIG_KEY_BACKFILL_BUCKET = "backfill.bucket";
    static final String REDRIVE_UPLOAD_S3_KEY_PREFIX = "redrive-upload-id-";
    static final String METADATA_KEY_EVENT_TIMESTAMP = "eventTimestamp";
    static final String METADATA_KEY_INSTANCE_GUID = "instanceGuid";
    static final String METADATA_KEY_STARTED_ON = "startedOn";
    static final String WORKER_NAME_UPLOAD_REDRIVE = "uploadRedriveWorker";

    private AccountService accountService;
    private AdherenceService adherenceService;
    private AppService appService;
    private Exporter3Service exporter3Service;
    private HealthDataService healthDataService;
    private HealthDataEx3Service healthDataEx3Service;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;
    private Schedule2Service schedule2Service;
    private StudyService studyService;
    private String uploadBucket;
    private String redriveUploadBucket;
    private UploadDao uploadDao;
    private UploadDedupeDao uploadDedupeDao;
    private UploadValidationService uploadValidationService;
    private HealthCodeDao healthCodeDao;
    private String workerQueueUrl;
    private AmazonSQS sqsClient;

    // These parameters can be overriden to facilitate testing.
    // By default, we sleep 5 seconds, including right at the start and end. This means on our 7th iteration,
    // 30 seconds will have passed.
    private int pollValidationStatusMaxIterations = 7;
    private long pollValidationStatusSleepMillis = 5000;

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Autowired
    public final void setAdherenceService(AdherenceService adherenceService) {
        this.adherenceService = adherenceService;
    }
    
    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Autowired
    public final void setExporter3Service(Exporter3Service exporter3Service) {
        this.exporter3Service = exporter3Service;
    }

    /** Sets parameters from the specified Bridge config. */
    @Autowired
    final void setConfig(BridgeConfig config) {
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);
        redriveUploadBucket = config.getProperty(CONFIG_KEY_BACKFILL_BUCKET);
        workerQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL);

    }

    /**
     * Health data record service. This is needed to fetch the health data record when constructing the upload
     * validation status.
     */
    @Autowired
    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    @Resource(name = "s3Client")
    public final void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }
    @Resource(name = "s3Client")
    public final void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Autowired
    public final void setSchedule2Service(Schedule2Service schedule2Service) {
        this.schedule2Service = schedule2Service;
    }

    @Autowired
    public final void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /** Upload dedupe DAO, for checking to see if an upload is a dupe. (And eventually dedupe if it is.) */
    @Autowired
    final void setUploadDedupeDao(UploadDedupeDao uploadDedupeDao) {
        this.uploadDedupeDao = uploadDedupeDao;
    }

    /** Service handler for upload validation. This is configured by Spring. */
    @Autowired
    final void setUploadValidationService(UploadValidationService uploadValidationService) {
        this.uploadValidationService = uploadValidationService;
    }

    @Autowired
    final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    @Autowired
    final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    /**
     * Number of iterations while polling for validation status before we time out. This is used primarily by tests to
     * reduce the amount of wait time during tests.
     */
    public final void setPollValidationStatusMaxIterations(int pollValidationStatusMaxIterations) {
        this.pollValidationStatusMaxIterations = pollValidationStatusMaxIterations;
    }

    /**
     * Milliseconds to sleep per iterations while polling for validation status. This is used primarily by tests to
     * reduce the amount of wait time during tests.
     */
    public final void setPollValidationStatusSleepMillis(long pollValidationStatusSleepMillis) {
        this.pollValidationStatusSleepMillis = pollValidationStatusSleepMillis;
    }

    public UploadSession createUpload(String appId, StudyParticipant participant, UploadRequest uploadRequest) {
        Validate.entityThrowingException(UploadValidator.INSTANCE, uploadRequest);

        // Check to see if upload is a dupe, and if it is, get the upload status.
        String uploadMd5 = uploadRequest.getContentMd5();
        DateTime uploadRequestedOn = DateUtils.getCurrentDateTime();
        String originalUploadId = null;
        UploadStatus originalUploadStatus = null;

        // Do not execute dedupe logic on test/API app, because integration tests submit the 
        // same uploads over and over again with each test run.
        if (!API_APP_ID.equals(appId)) {
            try {
                originalUploadId = uploadDedupeDao.getDuplicate(participant.getHealthCode(), uploadMd5,
                        uploadRequestedOn);
                if (originalUploadId != null) {
                    Upload originalUpload = uploadDao.getUpload(originalUploadId);
                    originalUploadStatus = originalUpload.getStatus();
                }
            } catch (RuntimeException ex) {
                // Don't want dedupe logic to fail the upload. Log an error and swallow the exception.
                logger.error("Error deduping upload: " + ex.getMessage(), ex);
            }
        }

        String uploadId;
        if (originalUploadId != null && originalUploadStatus == UploadStatus.REQUESTED) {
            // This is a dupe of a previous upload, and that previous upload is incomplete (REQUESTED). Instead of
            // creating a new upload in the upload table, reactivate the old one.
            uploadId = originalUploadId;
        } else {
            // This is a new upload.
            Upload upload = uploadDao.createUpload(uploadRequest, appId, participant.getHealthCode(),
                    originalUploadId);
            uploadId = upload.getUploadId();

            // Get client info from Request Context, write it to the upload as JSON.
            RequestContext requestContext = RequestContext.get();
            ClientInfo clientInfo = requestContext.getCallerClientInfo();
            try {
                String clientInfoJsonText = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(clientInfo);
                upload.setClientInfo(clientInfoJsonText);
            } catch (JsonProcessingException ex) {
                // Should never happen. Log an error and swallow it, so that we don't fail the rest of the upload.
                logger.error("Error serializing client info to JSON for app " + appId + " healthcode " +
                        participant.getHealthCode(), ex);
            }

            // Also, get the User Agent.
            String userAgent = requestContext.getUserAgent();
            upload.setUserAgent(userAgent);

            // Write the upload back to the upload table with the user agent and client info.
            uploadDao.updateUpload(upload);

            if (originalUploadId != null) {
                // We had a dupe of a previous completed upload. Log this for future analysis.
                logger.info("Detected dupe: App " + appId + ", upload " + uploadId +
                        " is a dupe of " + originalUploadId);
            } else {
                try {
                    // Not a dupe. Register this dupe so we can detect dupes of this.
                    uploadDedupeDao.registerUpload(participant.getHealthCode(), uploadMd5, uploadRequestedOn, uploadId);
                } catch (RuntimeException ex) {
                    // Don't want dedupe logic to fail the upload. Log an error and swallow the exception.
                    logger.error("Error registering upload " + uploadId + " in dedupe table: " + ex.getMessage(), ex);
                }
            }
        }

        // Upload ID in DynamoDB is the same as the S3 Object ID
        GeneratePresignedUrlRequest presignedUrlRequest =
                new GeneratePresignedUrlRequest(uploadBucket, uploadId, HttpMethod.PUT);

        // Expiration
        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + EXPIRATION);
        presignedUrlRequest.setExpiration(expiration);

        // Ask for server-side encryption
        presignedUrlRequest.addRequestParameter(SERVER_SIDE_ENCRYPTION, AES_256_SERVER_SIDE_ENCRYPTION);

        // Additional headers for signing
        presignedUrlRequest.setContentMd5(uploadMd5);
        presignedUrlRequest.setContentType(uploadRequest.getContentType());

        URL url = s3UploadClient.generatePresignedUrl(presignedUrlRequest);
        return new UploadSession(uploadId, url, expiration.getTime());
    }

    /**
     * <p>
     * Get upload service handler. This isn't currently exposed directly to the users, but is currently used by the
     * controller class to call both uploadComplete() and upload validation APIs.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload metadata object
     */
    public Upload getUpload(@Nonnull String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(CANNOT_BE_BLANK, "uploadId"));
        }
        return uploadDao.getUpload(uploadId);
    }
    
    public UploadView getUploadView(String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(CANNOT_BE_BLANK, "uploadId"));
        }
        Upload upload = uploadDao.getUpload(uploadId);
        return uploadToUploadView(upload, true);
    }

    /**
     * <p>Get uploads for a given user in a time window. Start and end time are optional. If neither are provided, they 
     * default to the last day of uploads. If end time is not provided, the query ends at the time of the request. If the 
     * start time is not provided, it defaults to a day before the end time. The time window is constrained to two days 
     * of uploads (though those days can be any period in time). </p>
     */
    public ForwardCursorPagedResourceList<UploadView> getUploads(@Nonnull String healthCode,
            @Nullable DateTime startTime, @Nullable DateTime endTime, Integer pageSize, @Nullable String offsetKey) {
        checkNotNull(healthCode);
        
        return getUploads(startTime, endTime, (start, end)-> {
            return uploadDao.getUploads(healthCode, start, end,
                    (pageSize == null ? API_DEFAULT_PAGE_SIZE : pageSize.intValue()), offsetKey);
        });
    }
    
    /**
     * <p>Get uploads for an entire app in a time window. Start and end time are optional. If neither are provided, they 
     * default to the last day of uploads. If end time is not provided, the query ends at the time of the request. If the 
     * start time is not provided, it defaults to a day before the end time. The time window is constrained to two days 
     * of uploads (though those days can be any period in time). </p>
     */
    public ForwardCursorPagedResourceList<UploadView> getAppUploads(@Nonnull String appId,
            @Nullable DateTime startTime, @Nullable DateTime endTime, @Nullable Integer pageSize, @Nullable String offsetKey) {
        checkNotNull(appId);

        // in case clients didn't set page size up
        return getUploads(startTime, endTime, (start, end)-> 
            uploadDao.getAppUploads(appId, start, end,
                    (pageSize == null ? API_DEFAULT_PAGE_SIZE : pageSize.intValue()), offsetKey)
        );
    }
    
    private ForwardCursorPagedResourceList<UploadView> getUploads(DateTime startTime, DateTime endTime, UploadSupplier supplier) {
        checkNotNull(supplier);
        
        if (startTime == null && endTime == null) {
            endTime = DateTime.now();
            startTime = endTime.minusDays(1);
        } else if (endTime == null) {
            endTime = startTime.plusDays(1);
        } else if (startTime == null) {
            startTime = endTime.minusDays(1);
        }
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("Start time cannot be after end time: " + startTime + "-" + endTime);
        }
        
        ForwardCursorPagedResourceList<Upload> list = supplier.get(startTime, endTime);

        // This summary view is accessible to developers, so we do not include details of the health data record.
        List<UploadView> views = list.getItems().stream()
                .map(upload -> uploadToUploadView(upload, false))
                .collect(Collectors.toList());
        
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(views, list.getNextPageOffsetKey());
        for (Map.Entry<String,Object> entry : list.getRequestParams().entrySet()) {
            page.withRequestParam(entry.getKey(), entry.getValue());
        }
        return page;
    }
    
    private UploadView uploadToUploadView(Upload upload, boolean includeHealthDataRecord) {
        UploadView.Builder builder = new UploadView.Builder();
        builder.withUpload(upload);
        if (upload.getRecordId() != null) {
            HealthDataRecord record = healthDataService.getRecordById(upload.getRecordId());
            if (record != null) {
                if (includeHealthDataRecord) {
                    builder.withHealthDataRecord(record);
                } else {
                    builder.withSchemaId(record.getSchemaId());
                    builder.withSchemaRevision(record.getSchemaRevision());
                    builder.withHealthRecordExporterStatus(record.getSynapseExporterStatus());
                }
            }
        }
        return builder.build();
    }

    /**
     * <p>
     * Gets validation status and messages for the given upload ID. This includes the health data record, if one was
     * created for the upload.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload validation status, which includes the health data record if one was created
     */
    public UploadValidationStatus getUploadValidationStatus(@Nonnull String uploadId) {
        Upload upload = getUpload(uploadId);

        // get record, if it exists
        HealthDataRecord record = null;
        String recordId = upload.getRecordId();
        if (!Strings.isNullOrEmpty(recordId)) {
            try {
                record = healthDataService.getRecordById(recordId);
            } catch (RuntimeException ex) {
                // Underlying service failed to get the health data record. Log a warning, but move on.
                logger.warn("Error getting record ID " + recordId + " for upload ID " + uploadId + ": "
                        + ex.getMessage(), ex);
            }
        }

        UploadValidationStatus validationStatus = UploadValidationStatus.from(upload, record);
        return validationStatus;
    }

    /**
     * Polls for validation status for a given upload ID. Polls until validation is complete or otherwise is in a state
     * where further polling won't get any results (like validation failed, or upload is requested but not yet
     * uploaded), or until it times out. See getUploadValidationStatus() for more details.
     */
    public UploadValidationStatus pollUploadValidationStatusUntilComplete(String uploadId) {
        // Loop logic is a little wonky. (Loop-and-a-half problem.) Use an infinite loop here and rely on tests to make
        // sure we don't go infinite.
        int numIters = 0;
        while (true) {
            UploadValidationStatus validationStatus = getUploadValidationStatus(uploadId);
            if (validationStatus.getStatus() != UploadStatus.VALIDATION_IN_PROGRESS) {
                // Validation is either finished processing, or otherwise in a state where it's pointless to wait.
                // Return the answer we have now.
                return validationStatus;
            }

            // Short-circuit: If we've elapsed our timeout, just exit now. Don't wait for a sleep.
            numIters++;
            if (numIters >= pollValidationStatusMaxIterations) {
                throw new BridgeServiceException("Timeout polling validation status for upload " + uploadId);
            }

            // Sleep and try again.
            try {
                Thread.sleep(pollValidationStatusSleepMillis);
            } catch (InterruptedException ex) {
                logger.error("Interrupted while polling for validation status: " + ex.getMessage());
            }
        }
    }

    public void uploadComplete(String appId, UploadCompletionClient completedBy, Upload upload,
            boolean redrive) throws JsonProcessingException {
        String uploadId = upload.getUploadId();

        // We don't want to kick off upload validation on an upload that already has upload validation.
        if (!upload.canBeValidated() && !redrive) {
            logger.info(String.format("uploadComplete called for upload %s, which is already complete", uploadId));
            return;
        }

        final String objectId = upload.getObjectId();
        ObjectMetadata obj;
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            obj = s3Client.getObjectMetadata(uploadBucket, objectId);
            logger.info("Finished getting S3 metadata for bucket " + uploadBucket + " key " + objectId + " in " +
                    stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() == 404) {
                throw new NotFoundException(ex);
            } else {
                // Only S3 404s are mapped to 404s. Everything else is an internal server error.
                throw new BridgeServiceException(ex);
            }
        }
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            logger.error("Missing S3 server-side encryption (SSE) for presigned upload " + uploadId + ".");
        }

        try {
            uploadDao.uploadComplete(completedBy, upload);
        } catch (ConcurrentModificationException ex) {
            // The old workflow is the app calls uploadComplete. The new workflow has an S3 trigger to call
            // uploadComplete. During the transition, it's very likely that this will be called twice, sometimes
            // concurrently. As such, we should log and squelch the ConcurrentModificationException.
            logger.info("Concurrent modification of upload " + uploadId + " while marking upload complete");

            // Also short-circuit the call early, so we don't end up validating the upload twice, as this causes errors
            // and duplicate records.
            return;
        }

        // kick off upload validation
        App app = appService.getApp(appId);
        if (app.isExporter3Enabled()) {
            exporter3Service.completeUpload(app, upload);
        }

        // For backwards compatibility, always call Legacy Exporter 2.0. In the future, we may introduce a setting to
        // disable this for new apps.
        uploadValidationService.validateUpload(appId, upload);
        
        // Save uploadedOn date and uploadId to related adherence records.
        updateAdherenceWithUploadInfo(appId, upload);
    }

    public void redriveUpload( byte[] fileBytes) throws IOException {
        // Convert the byte[] array to a String
        String fileContent = new String(fileBytes);

        // Split the content into lines (each line is an entry - uploadId)
        String[] lines = fileContent.split("\\r?\\n");

        // Count the number of entries (lines)
        int numberOfEntries = lines.length;

        // Redrive upload.
        if (numberOfEntries <= 10) {
            redriveSmallAmountOfUploads(fileBytes);
        } else {
            redriveLargeAmountOfUploads(fileBytes);
        }
    }

    private void redriveSmallAmountOfUploads(byte[] fileBytes) throws JsonProcessingException {
        // Convert the byte[] array to a String
        String fileContent = new String(fileBytes);

        // Split the String into an array of lines
        String[] linesArray = fileContent.split("\n");

        // Convert the array of lines into a List
        List<String> uploadIds = new ArrayList<>(Arrays.asList(linesArray));

        for (String uploadId : uploadIds) {
            Upload upload = getUpload(uploadId);
            String appId = upload.getAppId();
            if (appId == null) {
                appId = healthCodeDao.getAppId(upload.getHealthCode());
            }
            uploadComplete(appId, UploadCompletionClient.REDRIVE, upload, true);
            UploadValidationStatus validationStatus = pollUploadValidationStatusUntilComplete(uploadId);
            if (validationStatus.getStatus() != UploadStatus.SUCCEEDED) {
                logger.error("Redrive failed for uploadId=" + uploadId + ": " + COMMA_SPACE_JOINER.join(
                        validationStatus.getMessageList()));
            }
        }
    }

    private void redriveLargeAmountOfUploads(byte[] fileBytes) throws IOException {
        // set up s3 Key.
        String currentYear = String.valueOf(DateUtils.getCurrentDateTime().getYear());
        String currentMonth = String.valueOf(DateUtils.getCurrentDateTime().getMonthOfYear());
        String s3Key = REDRIVE_UPLOAD_S3_KEY_PREFIX + currentYear + "-" + currentMonth;

        // Upload file to S3 bucket
        InputStream inputStream = new ByteArrayInputStream(fileBytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(URLConnection.guessContentTypeFromStream(inputStream));

        PutObjectRequest request = new PutObjectRequest(CONFIG_KEY_BACKFILL_BUCKET, s3Key, inputStream, metadata);
        s3Client.putObject(request);
        // write Json message to sqs
        // 1. Create request.
        UploadRedriveWorkerRequest uploadRedriveWorkerRequest = new UploadRedriveWorkerRequest();
        uploadRedriveWorkerRequest.setS3Key(s3Key);
        uploadRedriveWorkerRequest.setS3Bucket(redriveUploadBucket);
        uploadRedriveWorkerRequest.setRedriveTypeStr("upload_id");

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(WORKER_NAME_UPLOAD_REDRIVE);
        workerRequest.setBody(uploadRedriveWorkerRequest);

        // Convert request to JSON.
        ObjectMapper objectMapper = BridgeObjectMapper.get();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(workerRequest);
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch and re-throw for code hygiene.
            throw new BridgeServiceException("Error creating upload redrive request for S3 file " + s3Key,
                    ex);
        }
        // 2. Send to SQS.
        SendMessageResult sqsResult = sqsClient.sendMessage(workerQueueUrl, requestJson);
        logger.info("Sent redrive upload request for file " + s3Key +
                sqsResult.getMessageId());
    }
    
    public void deleteUploadsForHealthCode(String healthCode) {
        checkArgument(isNotBlank(healthCode));

        // Delete from DynamoDB.
        List<String> uploadIdList = uploadDao.deleteUploadsForHealthCode(healthCode);

        // Delete files from S3.
        // If the file does not exist on S3, the s3Client will actually return success
        // instead of an error message.
        for (String uploadId : uploadIdList) {
            s3Client.deleteObject(uploadBucket, uploadId);
        }
    }

    /**
     * This method gets a view that includes both the upload and the record (if they exist) for a given upload ID.
     * Optionally includes getting the timeline metadata and the adherence records, if they exist.
     *
     * App ID and upload ID are required. Study ID is only required if we are fetching adherence.
     *
     * Can only be called for your own uploads, for study coordinators and study designers that have access to the
     * study (study ID is required), and for developers, researchers, workers, and admins.
     */
    public UploadViewEx3 getUploadViewForExporter3(String appId, String studyId, String uploadId,
            boolean fetchTimeline, boolean fetchAdherence) {
        checkNotNull(appId);
        checkNotNull(uploadId);

        if (fetchAdherence && isBlank(studyId)) {
            throw new BadRequestException("Adherence requires study ID");
        }

        // Get upload, if it exists. This can be null if the record was created through the synchronous health data
        // submission API.
        Upload upload = uploadDao.getUploadNoThrow(uploadId);
        if (upload != null && !appId.equals(upload.getAppId())) {
            // Upload is from a different app.
            throw new EntityNotFoundException(UploadViewEx3.class);
        }

        // Get record, if it exists. This can be null if the upload complete API was never called.
        HealthDataRecordEx3 record = healthDataEx3Service.getRecord(uploadId, false).orElse(null);
        if (record != null && !appId.equals(record.getAppId())) {
            // Record is from a different app.
            throw new EntityNotFoundException(UploadViewEx3.class);
        }

        // If neither upload nor record exist, then throw a 404.
        if (upload == null && record == null) {
            throw new EntityNotFoundException(UploadViewEx3.class);
        }

        // Uploads and Records only store health codes. We need the health code to get the User ID.
        String healthCode = null;
        if (upload != null) {
            healthCode = upload.getHealthCode();
        }
        if (healthCode == null) {
            // Fall back to record.
            if (record != null) {
                healthCode = record.getHealthCode();
            }
        }

        // Get the user ID. We need this to check permissions.
        String userId = accountService.getAccountId(appId, "healthcode:" + healthCode)
                .orElseThrow(() -> new EntityNotFoundException(UploadViewEx3.class));
        CAN_READ_UPLOADS.checkAndThrow(STUDY_ID, studyId, USER_ID, userId);

        UploadViewEx3 view = new UploadViewEx3();
        view.setId(uploadId);
        view.setHealthCode(healthCode);
        view.setRecord(record);
        view.setUpload(upload);
        view.setUserId(userId);

        if (fetchTimeline || fetchAdherence) {
            // Get the instanceGuid from upload/record metadata. This is needed for both timeline and adherence.
            // instanceGuid in metadata instead of being a top-level attribute for legacy reasons.
            String instanceGuid = null;
            if (upload != null) {
                ObjectNode metadataNode = upload.getMetadata();
                if (metadataNode != null) {
                    JsonNode instanceGuidNode = metadataNode.get(METADATA_KEY_INSTANCE_GUID);
                    if (instanceGuidNode != null && instanceGuidNode.isTextual()) {
                        instanceGuid = instanceGuidNode.textValue();
                    }
                }
            }
            if (instanceGuid == null) {
                // Fall back to record.
                if (record != null) {
                    Map<String, String> metadataMap = record.getMetadata();
                    if (metadataMap != null) {
                        instanceGuid = metadataMap.get(METADATA_KEY_INSTANCE_GUID);
                    }
                }
            }

            if (instanceGuid != null) {
                if (fetchTimeline) {
                    // Fetch timeline metadata.
                    Optional<TimelineMetadata> timelineMetadataOptional = schedule2Service.getTimelineMetadata(
                            instanceGuid);
                    if (timelineMetadataOptional.isPresent()) {
                        TimelineMetadata timelineMetadata = timelineMetadataOptional.get();
                        if (!appId.equals(timelineMetadata.getAppId())) {
                            // This should never happen, but if it does, log an error and move on.
                            // In this case, we log instead of throwing because there's still useful information in the
                            // upload and/or record.
                            logger.error(
                                    "Timeline metadata associated with upload is from a different app, uploadId=" +
                                            uploadId + ", upload appId=" + appId + ", timeline instanceGuid=" +
                                            instanceGuid + ", timeline appId=" + timelineMetadata.getAppId());
                        } else {
                            // Wrap the TimelineMetadata in a TimelineMetadataView, because this is how our API exposes
                            // TimelineMetadata externally.
                            view.setTimelineMetadata(new TimelineMetadataView(timelineMetadata));
                        }
                    }
                }

                if (fetchAdherence) {
                    // Fetch the adherence record(s). MAX_PAGE_SIZE is 500. It is very unlikely that a single
                    // instanceGuid will map to more than 500 adherence records. If it somehow does, that's a problem
                    // we can solve in the future.
                    AdherenceRecordsSearch adherenceSearch = new AdherenceRecordsSearch.Builder()
                            .withInstanceGuids(ImmutableSet.of(instanceGuid))
                            .withPageSize(AdherenceRecordsSearchValidator.MAX_PAGE_SIZE)
                            .withStudyId(studyId)
                            .withUserId(userId)
                            .build();
                    List<AdherenceRecord> adherenceRecords = adherenceService.getAdherenceRecords(appId,
                            adherenceSearch).getItems();
                    view.setAdherenceRecordsForSchedule(adherenceRecords);
                }
            }
        }

        // We can also fetch adherence by upload ID. This doesn't require instanceGuid, but it does require study ID.
        if (fetchAdherence) {
            // Fetch the adherence record(s). Similar to above, it's unlikely that we have more than 500 records for
            // a given upload ID.
            AdherenceRecordsSearch adherenceSearch = new AdherenceRecordsSearch.Builder()
                    .withPageSize(AdherenceRecordsSearchValidator.MAX_PAGE_SIZE)
                    .withStudyId(studyId)
                    .withUploadId(uploadId)
                    .withUserId(userId)
                    .build();
            List<AdherenceRecord> adherenceRecords = adherenceService.getAdherenceRecords(appId, adherenceSearch)
                    .getItems();
            view.setAdherenceRecordsForUpload(adherenceRecords);
        }

        return view;
    }
    
    // protected for unit tests
    protected void updateAdherenceWithUploadInfo(String appId, Upload upload) {
        String uploadId = upload.getUploadId();
        // Check for metadata that can tie the upload to an adherence record.
        JsonNode metadata = upload.getMetadata();
        if (metadata != null) {
            JsonNode instanceGuidNode = metadata.get(METADATA_KEY_INSTANCE_GUID);
            JsonNode eventTimestampNode = metadata.get(METADATA_KEY_EVENT_TIMESTAMP);
            JsonNode startedOnNode = metadata.get(METADATA_KEY_STARTED_ON);
            
            // Must include instanceGuid, eventTimestamp, and startedOn
            if (instanceGuidNode != null && instanceGuidNode.textValue() != null &&
                    eventTimestampNode != null && eventTimestampNode.textValue() != null &&
                    startedOnNode != null && startedOnNode.textValue() != null) {
                
                String instanceGuid = instanceGuidNode.textValue();
                DateTime eventTimestamp;
                try {
                    eventTimestamp = DateTime.parse(eventTimestampNode.textValue());
                } catch (IllegalArgumentException ex) {
                    logger.info("Upload sent with malformed eventTimestamp in metadata. UploadId: " + uploadId +
                            ", ErrorMessage: " + ex.getMessage());
                    return;
                }
                
                DateTime startedOn;
                try {
                    startedOn = DateTime.parse(startedOnNode.textValue());
                } catch (IllegalArgumentException ex) {
                    logger.info("Upload sent with malformed startedOn in metadata. UploadId: " + uploadId +
                            ", ErrorMessage: " + ex.getMessage());
                    return;
                }
                
                String userId = accountService.getAccountId(appId, "healthcode:" + upload.getHealthCode())
                        .orElseThrow(() -> new EntityNotFoundException(Account.class));
                
                // Find the study using the instanceGuid referenced by the upload
                TimelineMetadata timelineMetadata =  schedule2Service.getTimelineMetadata(instanceGuid).orElse(null);
                if (timelineMetadata != null) {
                    
                    String scheduleGuid = timelineMetadata.getScheduleGuid();
                    List<String> studyIds = studyService.getStudyIdsUsingSchedule(appId, scheduleGuid);
                    
                    if (studyIds.size() == 1) {
                        String studyId = studyIds.get(0);
                        
                        // If adherence record already exists, update it. Otherwise, create a new one.
                        AdherenceRecordsSearch.Builder search = new AdherenceRecordsSearch.Builder()
                                .withInstanceGuids(ImmutableSet.of(instanceGuid))
                                .withUserId(userId)
                                .withStudyId(studyId);
                        if (timelineMetadata.isTimeWindowPersistent()) {
                            search.withStartTime(startedOn);
                            search.withEndTime(startedOn);
                        }
                        PagedResourceList<AdherenceRecord> recordList = adherenceService.getAdherenceRecords(appId,
                                search.build());
                        
                        List<AdherenceRecord> recordsToUpdate = new ArrayList<>();
                        boolean foundExistingRecord = false;
                        
                        for (AdherenceRecord record : recordList.getItems()) {
                            if (timelineMetadata.isTimeWindowPersistent() && !record.getStartedOn().isEqual(startedOn)) {
                                // If the window is persistent then the records would have to share startedOn values
                                // to be considered the same.
                                logger.info("Unexpected adherence record returned when searching persistent window. " +
                                        "AppId: " + appId + ", StudyId: " + studyId + ", InstanceGuid: " + 
                                        instanceGuid + ", searched StartedOn: " + startedOn + 
                                        ", returned StartedOn: " + record.getStartedOn());
                                continue;
                            }
    
                            if (record.getEventTimestamp().isEqual(eventTimestamp)) {
                                // The DAO retains the earlier uploadedOn date and previous uploadIds
                                // so these can ignore the calculation and pass in the new upload.
                                // Since there is an existing record, let the persisted startedOn date remain.
                                record.setUploadedOn(new DateTime(upload.getCompletedOn()));
                                record.addUploadId(uploadId);
                                recordsToUpdate.add(record);
                                foundExistingRecord = true;
                                break;
                            }
                        }
                        
                        if (!foundExistingRecord) {
                            AdherenceRecord record = new AdherenceRecord();
                            record.setAppId(appId);
                            record.setStudyId(studyId);
                            record.setUserId(userId);
                            record.setInstanceGuid(instanceGuid);
                            record.setEventTimestamp(eventTimestamp);
                            record.setStartedOn(startedOn);
                            record.setUploadedOn(new DateTime(upload.getCompletedOn()));
                            record.addUploadId(uploadId);
                            
                            recordsToUpdate.add(record);
                        }
                        
                        adherenceService.updateAdherenceRecords(appId, new AdherenceRecordList(recordsToUpdate));
                        
                    } else if (studyIds.size() > 1) {
                        // It's unlikely that there are multiple studies tied to one schedule, but not impossible.
                        // If it does happen, the timelines for the studies would be identical. It would be possible
                        // to check which study a participant is enrolled in. But they can also enroll in multiple.
                        logger.warn("Upload completion can not be noted in adherence since its schedule belongs " +
                                "to multiple studies. UploadId: " + uploadId + ", ScheduleGuid: " + scheduleGuid);
                    }
                } else {
                    logger.info("Upload referenced a non-existent instanceGuid. AppId: " + appId +
                            ", UploadId: " + uploadId);
                }
            }
        }
    }

    @FunctionalInterface
    private static interface UploadSupplier {
        ForwardCursorPagedResourceList<Upload> get(DateTime startTime, DateTime endTime);
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}
