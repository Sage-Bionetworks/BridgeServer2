package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
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
    @GetMapping(path={"/v3/uploadstatuses/{uploadId}", "/api/v1/upload/{uploadId}/status"}, produces={APPLICATION_JSON_UTF8_VALUE})
    public String getValidationStatus(@PathVariable String uploadId) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        // If not a researcher, validate that this user owns the upload
        if (!session.isInRole(Roles.RESEARCHER)) {
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
        UploadRequest uploadRequest = UploadRequest.fromJson(parseJson(JsonNode.class));
        UploadSession uploadSession = uploadService.createUpload(session.getStudyIdentifier(), session.getParticipant(),
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
    @PostMapping(path={"/v3/uploads/{uploadId}/complete", "/api/v1/upload/{uploadId}/complete"}, produces={APPLICATION_JSON_UTF8_VALUE})
    public String uploadComplete(@PathVariable String uploadId,
            @RequestParam(defaultValue = "false") boolean synchronous,
            @RequestParam(defaultValue = "false") boolean redrive) throws Exception {
        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadId(uploadId);
        }

        // User can be a worker account (get study and health code from the upload itself)...
        UserSession session = getAuthenticatedSession();
        Upload upload = uploadService.getUpload(uploadId);
        StudyIdentifier studyIdentifier;
        UploadCompletionClient uploadCompletionClient;
        if (session.isInRole(Roles.WORKER)) {
            String studyId = upload.getStudyId();
            if (studyId == null) {
                studyId = healthCodeDao.getStudyIdentifier(upload.getHealthCode());
            }
            studyIdentifier = new StudyIdentifierImpl(studyId);
            uploadCompletionClient = UploadCompletionClient.S3_WORKER;
        } else {
            // Or, the consented user that originally made the upload request. Check that health codes match.
            // Do not need to look up the study.
            session = getAuthenticatedAndConsentedSession();
            if (!session.getHealthCode().equals(upload.getHealthCode())) {
                throw new UnauthorizedException();
            }

            studyIdentifier = session.getStudyIdentifier();
            uploadCompletionClient = UploadCompletionClient.APP;
        }
        uploadService.uploadComplete(studyIdentifier, uploadCompletionClient, upload, redrive);

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
    
    @GetMapping("/v3/uploads/{uploadId}")
    public UploadView getUpload(@PathVariable String uploadId) {
        UserSession session = getAuthenticatedSession(ADMIN, WORKER);

        if (uploadId.startsWith("recordId:")) {
            String recordId = uploadId.split(":")[1];

            // This service does not throw an exception if the record is not found
            HealthDataRecord record = healthDataService.getRecordById(recordId);
            if (record == null) {
                throw new EntityNotFoundException(HealthDataRecord.class);
            }
            
            if (!session.isInRole(EnumSet.of(SUPERADMIN, WORKER)) &&
                !session.getStudyIdentifier().getIdentifier().equals(record.getStudyId())) {
                throw new UnauthorizedException("Study admin cannot retrieve upload in another study.");
            }
            uploadId = record.getUploadId();
        }
        return uploadService.getUploadView(uploadId);
    }
}
