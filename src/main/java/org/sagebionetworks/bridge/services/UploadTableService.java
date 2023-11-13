package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_UPLOADS;

import java.util.List;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadTableJobDao;
import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.upload.UploadCsvRequest;
import org.sagebionetworks.bridge.upload.UploadTableJob;
import org.sagebionetworks.bridge.upload.UploadTableJobGuidHolder;
import org.sagebionetworks.bridge.upload.UploadTableJobResult;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;
import org.sagebionetworks.bridge.validators.UploadTableRowQueryValidator;
import org.sagebionetworks.bridge.validators.UploadTableRowValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service handler for upload table rows. */
@Component
public class UploadTableService {
    private static final Logger LOG = LoggerFactory.getLogger(Exporter3Service.class);

    // Package-scoped for unit tests.
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final int DEDUPE_WINDOW_MINUTES = 5;
    static final int EXPIRATION_IN_DAYS = 7;
    static final String WORKER_NAME_UPLOAD_CSV = "UploadCsvWorker";

    private BridgeConfig config;
    private String rawHealthDataBucket;
    private AmazonS3 s3Client;
    private AmazonSQS sqsClient;
    private StudyService studyService;
    private UploadService uploadService;
    private UploadTableJobDao uploadTableJobDao;
    private UploadTableRowDao uploadTableRowDao;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        this.config = config;
        this.rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
    }

    @Autowired
    public final void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    public final void setUploadTableJobDao(UploadTableJobDao uploadTableJobDao) {
        this.uploadTableJobDao = uploadTableJobDao;
    }

    @Autowired
    public final void setUploadTableRowDao(UploadTableRowDao uploadTableRowDao) {
        this.uploadTableRowDao = uploadTableRowDao;
    }

    /** Get the upload table job result, with the downloadable S3 URL if it's ready. */
    public UploadTableJobResult getUploadTableJobResult(String appId, String studyId, String jobGuid) {
        // Verify caller has access to app and study.
        CAN_READ_UPLOADS.checkAndThrow(AuthEvaluatorField.STUDY_ID, studyId);

        // Get the table job.
        UploadTableJob tableJob = uploadTableJobDao.getUploadTableJob(jobGuid).orElseThrow(
                () -> new EntityNotFoundException(UploadTableJob.class));
        if (!tableJob.getAppId().equals(appId)) {
            throw new EntityNotFoundException(UploadTableJob.class);
        }
        if (!tableJob.getStudyId().equals(studyId)) {
            throw new EntityNotFoundException(UploadTableJob.class);
        }

        // Create a job result from the job.
        UploadTableJobResult result = UploadTableJobResult.fromJob(tableJob);

        if (UploadTableJob.Status.SUCCEEDED.equals(tableJob.getStatus())) {
            // If the job succeeded, add the URL and expiration.
            DateTime expiresOn = DateTime.now().plusDays(EXPIRATION_IN_DAYS);
            GeneratePresignedUrlRequest presignedUrlRequest = new GeneratePresignedUrlRequest(rawHealthDataBucket,
                    tableJob.getS3Key(), HttpMethod.GET);
            presignedUrlRequest.setExpiration(expiresOn.toDate());
            String url = s3Client.generatePresignedUrl(presignedUrlRequest).toString();
            result.setUrl(url);
            result.setExpiresOn(expiresOn);
        }

        return result;
    }

    /** List upload table jobs for the given app and study. Does not include the downloadable S3 URL. */
    public PagedResourceList<UploadTableJob> listUploadTableJobsForStudy(String appId, String studyId, int start,
            int pageSize) {
        // Verify caller has access to app and study.
        CAN_READ_UPLOADS.checkAndThrow(AuthEvaluatorField.STUDY_ID, studyId);

        // Validate start and pageSize.
        if (start < 0) {
            throw new BadRequestException("start cannot be negative");
        }
        if (pageSize < BridgeConstants.API_MINIMUM_PAGE_SIZE) {
            throw new BadRequestException("pageSize must be at least " + BridgeConstants.API_MINIMUM_PAGE_SIZE);
        } else if (pageSize > BridgeConstants.API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException("pageSize must be at most " + BridgeConstants.API_MAXIMUM_PAGE_SIZE);
        }

        return uploadTableJobDao.listUploadTableJobsForStudy(appId, studyId, start, pageSize);
    }

    /**
     * Request a zip file with CSVs of all uploads in this app and study. This includes test uploads. This will dedupe
     * requests within a 5-minute window. If no new uploads have been submitted to the app since the last CSV request,
     * this will return the same job GUID as the last request.
     */
    public UploadTableJobGuidHolder requestUploadTableForStudy(String appId, String studyId) {
        // Verify caller has access to app and study.
        CAN_READ_UPLOADS.checkAndThrow(AuthEvaluatorField.STUDY_ID, studyId);

        // Get the most recent request for this study.
        List<UploadTableJob> jobList = uploadTableJobDao.listUploadTableJobsForStudy(appId, studyId, 0,
                1).getItems();
        if (!jobList.isEmpty()) {
            UploadTableJob mostRecentJob = jobList.get(0);
            DateTime mostRecentJobRequestedOn = mostRecentJob.getRequestedOn();

            // If it's within the last 5 minutes, just return that job GUID.
            DateTime fiveMinutesAgo = DateTime.now().minusMinutes(DEDUPE_WINDOW_MINUTES);
            if (mostRecentJobRequestedOn.isAfter(fiveMinutesAgo)) {
                return new UploadTableJobGuidHolder(mostRecentJob.getJobGuid());
            }

            // If there have been no uploads in this app since the last time the job was requested, just return that.
            // Note that table jobs are scoped to the study, but uploads are scoped to the app. This is fine. It means
            // we might sometimes query the study when there are no new uploads in the study, but there are uploads in
            // the app. This is a very rare case, and it's better to be conservative and generate a new table job.
            List<UploadView> recentUploadList = uploadService.getAppUploads(appId, mostRecentJobRequestedOn,
                    DateTime.now(DateTimeZone.UTC), 1, null).getItems();
            if (recentUploadList.isEmpty()) {
                return new UploadTableJobGuidHolder(mostRecentJob.getJobGuid());
            }
        }

        // Write the job to the database.
        String jobGuid = generateGuid();

        UploadTableJob job = UploadTableJob.create();
        job.setJobGuid(jobGuid);
        job.setAppId(appId);
        job.setStudyId(studyId);
        job.setRequestedOn(DateTime.now());
        job.setStatus(UploadTableJob.Status.IN_PROGRESS);
        uploadTableJobDao.saveUploadTableJob(job);

        // Send the request to the worker.
        UploadCsvRequest uploadCsvRequest = new UploadCsvRequest();
        uploadCsvRequest.setJobGuid(jobGuid);
        uploadCsvRequest.setAppId(appId);
        uploadCsvRequest.setStudyId(studyId);
        uploadCsvRequest.setIncludeTestData(true);

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(WORKER_NAME_UPLOAD_CSV);
        workerRequest.setBody(uploadCsvRequest);

        // Convert request to JSON.
        ObjectMapper objectMapper = BridgeObjectMapper.get();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(workerRequest);
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch and re-throw for code hygiene.
            throw new BridgeServiceException("Error creating CSV request for app " + appId + " study " + studyId, ex);
        }

        // Note: SqsInitializer runs after Spring, so we need to grab the queue URL dynamically.
        String workerQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL);

        // Sent to SQS.
        SendMessageResult sqsResult = sqsClient.sendMessage(workerQueueUrl, requestJson);
        LOG.info("Sent CSV request for app " + appId + " study " + studyId + "; received message ID=" +
                sqsResult.getMessageId());

        return new UploadTableJobGuidHolder(jobGuid);
    }

    /** Worker API to get the upload table job. Does not include the downloadable S3 URL. */
    public UploadTableJob getUploadTableJobForWorker(String appId, String studyId, String jobGuid) {
        // The worker can access any app or study, so we don't need to check permissions. (Worker permissions are
        // enforced by the controller.)

        // Get the table job.
        UploadTableJob tableJob = uploadTableJobDao.getUploadTableJob(jobGuid).orElseThrow(
                () -> new EntityNotFoundException(UploadTableJob.class));
        if (!tableJob.getAppId().equals(appId)) {
            throw new EntityNotFoundException(UploadTableJob.class);
        }
        if (!tableJob.getStudyId().equals(studyId)) {
            throw new EntityNotFoundException(UploadTableJob.class);
        }

        return tableJob;
    }

    /** Worker API to update the upload table job. */
    public void updateUploadTableJobForWorker(String appId, String studyId, String jobGuid, UploadTableJob job) {
        // Code paths that call this method have already verified that the caller has access to the app and study.

        // appId, studyId, and jobGuid are required and come from the URL path.
        job.setAppId(appId);
        job.setStudyId(studyId);
        job.setJobGuid(jobGuid);

        // If requestedOn isn't set (which shouldn't ever happen), set it to the current time.
        if (job.getRequestedOn() == null) {
            job.setRequestedOn(DateTime.now());
        }

        uploadTableJobDao.saveUploadTableJob(job);
    }

    /** Delete a single upload table row. */
    public void deleteUploadTableRow(String appId, String studyId, String recordId) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        uploadTableRowDao.deleteUploadTableRow(appId, studyId, recordId);
    }

    /** Get a single upload table row. Throws if the row doesn't exist. */
    public UploadTableRow getUploadTableRow(String appId, String studyId, String recordId) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        return uploadTableRowDao.getUploadTableRow(appId, studyId, recordId).orElseThrow(
                () -> new EntityNotFoundException(UploadTableRow.class));
    }

    /** Query for upload table rows. */
    public PagedResourceList<UploadTableRow> queryUploadTableRows(String appId, String studyId,
            UploadTableRowQuery query) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        // appId and studyId are required and come from the URL path.
        query.setAppId(appId);
        query.setStudyId(studyId);

        // Validate the query.
        Validate.entityThrowingException(UploadTableRowQueryValidator.INSTANCE, query);

        return uploadTableRowDao.queryUploadTableRows(query);
    }

    /** Create a new upload table row, or overwrite it if the row already exists. */
    public void saveUploadTableRow(String appId, String studyId, UploadTableRow row) {
        // Verify study exists, by passing in throwsException = true.
        studyService.getStudy(appId, studyId, true);

        // appId and studyId are required and come from the URL path.
        row.setAppId(appId);
        row.setStudyId(studyId);

        // CreatedOn defaults to the current time.
        if (row.getCreatedOn() == null) {
            row.setCreatedOn(DateTime.now());
        }

        // Validate the row.
        Validate.entityThrowingException(UploadTableRowValidator.INSTANCE, row);

        uploadTableRowDao.saveUploadTableRow(row);
    }

    // Package-scoped so unit tests can mock this.
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
