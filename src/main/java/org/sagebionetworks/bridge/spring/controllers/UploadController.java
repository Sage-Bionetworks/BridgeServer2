package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.models.upload.UploadViewEx3;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.UploadService;
import org.sagebionetworks.bridge.time.DateUtils;

@CrossOrigin
@RestController
public class UploadController extends BaseController {

    private UploadService uploadService;
    
    private HealthDataService healthDataService;
    
    private HealthCodeDao healthCodeDao;

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    
    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    @Autowired
    final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }
    
    /** Gets validation status and messages for the given upload ID. 
     * @throws JsonProcessingException */
    @GetMapping(path={"/v3/uploadstatuses/{uploadId}", "/api/v1/upload/{uploadId}/status"}, produces={APPLICATION_JSON_VALUE})
    public String getValidationStatus(@PathVariable String uploadId) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        // If not a developer or researcher, validate that this user owns the upload
        if (!session.isInRole(ImmutableSet.of(DEVELOPER, RESEARCHER))) {
            Upload upload = uploadService.getUpload(uploadId);
            if (!session.getHealthCode().equals(upload.getHealthCode())) {
                throw new UnauthorizedException();
            }
        }
        
        UploadValidationStatus validationStatus = uploadService.getUploadValidationStatus(uploadId);
        
        // Upload validation status may contain the health data record. Use the filter to filter out health code.
        return HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(validationStatus);
    }
    
    @PostMapping({"/v3/uploads", "/api/v1/upload"})
    public UploadSession upload() {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadRequest uploadRequest = parseJson(UploadRequest.class);
        UploadSession uploadSession = uploadService.createUpload(session.getAppId(), session.getParticipant(),
                uploadRequest);
        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadSize(uploadRequest.getContentLength());
            metrics.setUploadId(uploadSession.getId());
        }
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withUploadedOn(DateUtils.getCurrentDateTime()).build();
        requestInfoService.updateRequestInfo(requestInfo);
        
        return uploadSession;
    }

    /**
     * <p>
     * Signals to the Bridge server that the upload is complete. This kicks off the asynchronous validation process
     * through the Upload Validation Service.
     * </p>
     * <p>
     * If synchronous is set to "true", we will wait until upload validation is complete, then return the upload
     * validation status. This is generally recommended only for App Development, as some large uploads might take
     * several seconds to complete.
     * </p>
     * <p>
     * If synchronous is set to anything else, we will return a validation status immediately (which will often be in
     * the "validation_in_progress" state) and let upload validation run in the background.
     * </p>
     * <p>
     * If redrive is set to "true", then we allow upload validation of uploads that are already complete. This is to
     * allow redrives and backfills.
     * </p>
     */
    @PostMapping(path={"/v3/uploads/{uploadId}/complete", "/api/v1/upload/{uploadId}/complete"}, produces={APPLICATION_JSON_VALUE})
    public String uploadComplete(@PathVariable String uploadId,
            @RequestParam(defaultValue = "false") boolean synchronous,
            @RequestParam(defaultValue = "false") boolean redrive) throws Exception {
        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadId(uploadId);
        }

        // User can be a worker account (get app and health code from the upload itself)...
        UserSession session = getAuthenticatedSession();
        Upload upload = uploadService.getUpload(uploadId);
        String appId;
        UploadCompletionClient uploadCompletionClient;
        if (session.isInRole(Roles.WORKER)) {
            appId = upload.getAppId();
            if (appId == null) {
                appId = healthCodeDao.getAppId(upload.getHealthCode());
            }
            uploadCompletionClient = redrive? UploadCompletionClient.REDRIVE : UploadCompletionClient.S3_WORKER;
        } else {
            // Or, the consented user that originally made the upload request. Check that health codes match.
            // Do not need to look up the app.
            session = getAuthenticatedAndConsentedSession();
            if (!session.getHealthCode().equals(upload.getHealthCode())) {
                throw new UnauthorizedException();
            }

            appId = session.getAppId();
            uploadCompletionClient = UploadCompletionClient.APP;
        }
        uploadService.uploadComplete(appId, uploadCompletionClient, upload, redrive);

        // In async mode, we get the validation status (probably in validation_in_progress) and return immediately.
        // In sync mode, we poll until the validation status is complete (or failed or another non-transient status).
        UploadValidationStatus validationStatus;
        if (synchronous) {
            validationStatus = uploadService.pollUploadValidationStatusUntilComplete(uploadId);
        } else {
            validationStatus = uploadService.getUploadValidationStatus(uploadId);
        }

        // Upload validation status may contain the health data record. Use the filter to filter out health code.
        return HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(validationStatus);
    }

    @PostMapping("/v3/uploads/redrive")
    public String redriveUploads(@RequestBody byte[] fileBytes) throws IOException {
        if (fileBytes != null && fileBytes.length != 0) {
            uploadService.redriveUpload(fileBytes);
            return "Redrive uploads attempted.";
        }
        return "Please provide a non-empty file for upload redrive.";
    }
    
    @GetMapping("/v3/uploads/{uploadId}")
    public UploadView getUpload(@PathVariable String uploadId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);

        if (uploadId.startsWith("recordId:")) {
            String recordId = uploadId.split(":")[1];

            // This service does not throw an exception if the record is not found
            HealthDataRecord record = healthDataService.getRecordById(recordId);
            if (record == null) {
                throw new EntityNotFoundException(HealthDataRecord.class);
            }
            uploadId = record.getUploadId();
        }

        UploadView uploadView = uploadService.getUploadView(uploadId);
        
        // Upload access is allowed (from least to most restrictive):
        // 1. for workers or superadmins in any app
        // 2. for admins in their own app
        // 3. for developers in their own accounts
        
        Upload upload = uploadView.getUpload();
        if (session.isInRole(EnumSet.of(SUPERADMIN, WORKER))) {
            return uploadView;
        } else if (session.isInRole(ADMIN) && session.getAppId().equals(upload.getAppId())) {
            return uploadView;
        } else if (session.isInRole(DEVELOPER) && session.getHealthCode().equals(upload.getHealthCode())) {
            // this in effect also tests that we're in the right app, because health code
            // is globally uniques
            return uploadView;
        }
        throw new UnauthorizedException("Caller does not have permission to access upload.");
    }

    /**
     * This method gets a view that includes both the upload and the record (if they exist) for a given upload ID.
     * Optionally includes getting the timeline metadata and the adherence records, if they exist.
     *
     * App ID and upload ID are required. Study ID is only required if we are fetching adherence.
     *
     * Can only be called for your own uploads, for study coordinators and study designers that have access to the
     * study (study ID is required), and for developers, researchers, and admins.
     */
    @GetMapping({"/v3/uploads/{uploadId}/exporter3",
            "/v5/studies/{studyId}/uploads/{uploadId}/exporter3"})
    public UploadViewEx3 getUploadViewForExporter3(@PathVariable(required = false) Optional<String> studyId,
            @PathVariable String uploadId, @RequestParam(defaultValue = "false") boolean fetchTimeline,
            @RequestParam(defaultValue = "false") boolean fetchAdherence) {
        // UploadService handles fine-grained permissions checks. For the Controller, just check that we're
        // authenticated.
        UserSession session = getAuthenticatedSession();
        return uploadService.getUploadViewForExporter3(session.getAppId(), studyId.orElse(null), uploadId,
                fetchTimeline, fetchAdherence);
    }

    /** Worker equivalent to getUploadViewForExporter3. */
    @GetMapping({"/v1/apps/{appId}/uploads/{uploadId}/exporter3",
            "/v1/apps/{appId}/studies/{studyId}/uploads/{uploadId}/exporter3"})
    public UploadViewEx3 getUploadEx3ForWorker(@PathVariable String appId,
            @PathVariable(required = false) Optional<String> studyId, @PathVariable String uploadId,
            @RequestParam(defaultValue = "false") boolean fetchTimeline,
            @RequestParam(defaultValue = "false") boolean fetchAdherence) {
        getAuthenticatedSession(WORKER);
        return uploadService.getUploadViewForExporter3(appId, studyId.orElse(null), uploadId, fetchTimeline,
                fetchAdherence);
    }
}
