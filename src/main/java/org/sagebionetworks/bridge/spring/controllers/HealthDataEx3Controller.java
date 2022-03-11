package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.services.HealthDataEx3Service;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.models.accounts.UserSession;

/**
 * This controller exposes the Exporter 3 implementation of Health Data Records. This is primarily used by the worker.
 */
@CrossOrigin
@RestController
public class HealthDataEx3Controller extends BaseController {
    private HealthDataEx3Service healthDataEx3Service;
    private StudyService studyService;

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    /** Create or update health data record. Returns the created or updated record. */
    @PostMapping(path="/v1/apps/{appId}/exporter3/healthdata")
    public HealthDataRecordEx3 createOrUpdateRecord(@PathVariable String appId) {
        getAuthenticatedSession(WORKER);

        // Verify app exists.
        App app = appService.getApp(appId);
        if (app == null) {
            throw new EntityNotFoundException(App.class);
        }

        HealthDataRecordEx3 record = parseJson(HealthDataRecordEx3.class);
        record.setAppId(appId);
        HealthDataRecordEx3 savedRecord = healthDataEx3Service.createOrUpdateRecord(record);

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(savedRecord.getId());
        }

        return savedRecord;
    }

    /** Deletes all health data records for the given user. */
    @DeleteMapping(path="/v1/apps/{appId}/participants/{userIdToken}/exporter3/healthdata")
    public StatusMessage deleteRecordsForUser(@PathVariable String appId, @PathVariable String userIdToken) {
        getAuthenticatedSession(SUPERADMIN);

        String healthCode = accountService.getAccountHealthCode(appId, userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(StudyParticipant.class));

        healthDataEx3Service.deleteRecordsForHealthCode(healthCode);

        return new StatusMessage("Health data has been deleted for participant");
    }

    /** Retrieves the record for the given ID. */
    @GetMapping(path="/v1/apps/{appId}/exporter3/healthdata/{recordId}")
    public HealthDataRecordEx3 getRecord(@PathVariable String appId, @PathVariable String recordId) {
        getAuthenticatedSession(WORKER);

        // Verify app exists.
        App app = appService.getApp(appId);
        if (app == null) {
            throw new EntityNotFoundException(App.class);
        }

        HealthDataRecordEx3 record = healthDataEx3Service.getRecord(recordId).orElseThrow(() ->
                new EntityNotFoundException(HealthDataRecordEx3.class));
        if (!appId.equals(record.getAppId())) {
            throw new EntityNotFoundException(HealthDataRecordEx3.class);
        }

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(record.getId());
        }

        return record;
    }

    /** Retrieves all records for the given user and time range. */
    @GetMapping(path="/v1/apps/{appId}/participants/{userIdToken}/exporter3/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForUser(
            @PathVariable String appId, @PathVariable String userIdToken,
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(WORKER);

        String healthCode = accountService.getAccountHealthCode(appId, userIdToken)
                .orElseThrow(() -> new EntityNotFoundException(StudyParticipant.class));
        
        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForHealthCode(healthCode, createdOnStartDateTime, createdOnEndDateTime,
                pageSizeInt, offsetKey)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }

    /** Retrieves all records in the current app for the given time range. */
    @GetMapping(path="/v1/apps/{appId}/exporter3/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForApp(@PathVariable String appId,
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(WORKER);

        // Verify app exists.
        App app = appService.getApp(appId);
        if (app == null) {
            throw new EntityNotFoundException(App.class);
        }

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForApp(appId, createdOnStartDateTime, createdOnEndDateTime,
                pageSizeInt, offsetKey)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }

    /** Retrieves all records in the current app for the given study and time range. */
    @GetMapping(path="/v1/apps/{appId}/studies/{studyId}/exporter3/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForStudy(
            @PathVariable String appId, @PathVariable String studyId,
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(WORKER);

        // Verify study exists (and therefore that app exists through the study.
        studyService.getStudy(appId, studyId, true);

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForAppAndStudy(appId, studyId, createdOnStartDateTime,
                createdOnEndDateTime, pageSizeInt, offsetKey)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }

    /** Retrieves the record for the given ID for self. */
    @GetMapping(path="/v3/participants/self/exporter3/healthdata/{recordId}")
    public HealthDataRecordEx3 getRecordForSelf(@PathVariable String recordId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        HealthDataRecordEx3 record = healthDataEx3Service.getRecord(recordId).orElseThrow(() ->
                new EntityNotFoundException(HealthDataRecordEx3.class));

        // Make sure the caller can only get their own health data records for this API
        if (!session.getAppId().equals(record.getAppId()) || !session.getHealthCode().equals(record.getHealthCode())) {
            throw new EntityNotFoundException(HealthDataRecordEx3.class);
        }

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(record.getId());
        }

        return record;
    }

    /** Retrieves all records for the given user and time range for self. */
    @GetMapping(path="/v3/participants/self/exporter3/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForUserForSelf(
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedAndConsentedSession();

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForHealthCode(session.getHealthCode(), createdOnStartDateTime, createdOnEndDateTime,
                        pageSizeInt, offsetKey)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }
}
