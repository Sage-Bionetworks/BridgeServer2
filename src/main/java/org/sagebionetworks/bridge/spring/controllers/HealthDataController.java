package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.upload.UploadValidationException;

@CrossOrigin
@RestController
public class HealthDataController extends BaseController {
    
    static final TypeReference<DateTimeRangeResourceList<HealthDataRecord>> RECORD_RESOURCE_LIST_TYPE_REF =
            new TypeReference<DateTimeRangeResourceList<HealthDataRecord>>() {};

    private HealthDataService healthDataService;
    
    private ParticipantService participantService;

    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /** Gets the participant for developer APIs. */
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /** Gets a list of records for the given healthCode between the specified createdOn times (inclusive). */
    @GetMapping(path="/v3/healthdata", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getRecordsByCreatedOn(@RequestParam(required = false) String createdOnStart,
            @RequestParam(required = false) String createdOnEnd) throws IOException {
        UserSession session = getAuthenticatedAndConsentedSession();

        DateTime createdOnStartObj = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndObj = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);

        List<HealthDataRecord> recordList = healthDataService.getRecordsByHealthCodeCreatedOn(session.getHealthCode(),
                createdOnStartObj, createdOnEndObj);
        DateTimeRangeResourceList<HealthDataRecord> recordResourceList = new DateTimeRangeResourceList<>(recordList)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd);
        return HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(recordResourceList);
    }

    /**
     * API to allow consented users to submit health data in a synchronous API, instead of using the asynchronous
     * upload API. This is most beneficial for small data sets, like simple surveys. This API returns the health data
     * record produced from this submission, which includes the record ID.
     */
    @PostMapping(path="/v3/healthdata", produces={APPLICATION_JSON_UTF8_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public String submitHealthData() throws IOException, UploadValidationException {
        // Submit health data.
        UserSession session = getAuthenticatedAndConsentedSession();
        HealthDataSubmission healthDataSubmission = parseJson(HealthDataSubmission.class);
        HealthDataRecord savedRecord = healthDataService.submitHealthData(session.getStudyIdentifier(),
                session.getParticipant(), healthDataSubmission);

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(savedRecord.getId());
        }

        // Record upload time to user's request info. This allows us to track the last time the user submitted.
        RequestInfo requestInfo = getRequestInfoBuilder(session).withUploadedOn(DateUtils.getCurrentDateTime())
                .build();
        requestInfoService.updateRequestInfo(requestInfo);

        // Return the record produced by this submission. Filter out Health Code, of course.
        return HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(savedRecord);
    }

    /** Allows a developer to submit health data on behalf of the participant. This is generally used for backfills. */
    @PostMapping(path="/v3/participants/{userId}/healthdata", produces={APPLICATION_JSON_UTF8_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public String submitHealthDataForParticipant(@PathVariable String userId) throws IOException, UploadValidationException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Get participant.
        StudyParticipant participant = participantService.getParticipant(study, userId, false);

        // Submit health data.
        HealthDataSubmission healthDataSubmission = parseJson(HealthDataSubmission.class);
        HealthDataRecord savedRecord = healthDataService.submitHealthData(study.getIdentifier(), participant,
                healthDataSubmission);

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(savedRecord.getId());
        }

        // Return the record produced by this submission. Filter out Health Code, of course.
        return HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(savedRecord);
    }

    @PostMapping({"/v3/recordexportstatuses", "/v3/recordExportStatuses"})
    public StatusMessage updateRecordsStatus() {
        getAuthenticatedSession(WORKER);

        RecordExportStatusRequest recordExportStatusRequest = parseJson(RecordExportStatusRequest.class);

        List<String> updatedRecordIds = healthDataService.updateRecordsWithExporterStatus(recordExportStatusRequest);

        return new StatusMessage("Update exporter status to: " + updatedRecordIds + " complete.");
    }
}
