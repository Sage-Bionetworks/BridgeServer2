package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

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
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.services.HealthDataEx3Service;

/**
 * This controller exposes the Exporter 3 implementation of Health Data Records. It's current usage is only for
 * testing purposes, which is why all the APIs are accessible only to Super Admins and why the URLs all start with
 * /v1/admin/exporter3
 */
@CrossOrigin
@RestController
public class HealthDataEx3Controller extends BaseController {
    private HealthDataEx3Service healthDataEx3Service;

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    /** Create or update health data record. Returns the created or updated record. */
    @PostMapping(path="/v1/admin/exporter3/healthdata")
    public HealthDataRecordEx3 createOrUpdateRecord() {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        HealthDataRecordEx3 record = parseJson(HealthDataRecordEx3.class);
        record.setAppId(session.getAppId());
        HealthDataRecordEx3 savedRecord = healthDataEx3Service.createOrUpdateRecord(record);

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(savedRecord.getId());
        }

        return savedRecord;
    }

    /** Deletes all health data records for the given user. */
    @DeleteMapping(path="/v1/admin/exporter3/participants/{userId}/healthdata")
    public StatusMessage deleteRecordsForUser(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        String healthCode = accountService.getHealthCodeForAccount(AccountId.forId(session.getAppId(), userId));
        if (healthCode == null) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }

        healthDataEx3Service.deleteRecordsForHealthCode(healthCode);

        return new StatusMessage("Health data has been deleted for participant");
    }

    /** Retrieves the record for the given ID. */
    @GetMapping(path="/v1/admin/exporter3/healthdata/{recordId}")
    public HealthDataRecordEx3 getRecord(@PathVariable String recordId) {
        getAuthenticatedSession(SUPERADMIN);
        HealthDataRecordEx3 record = healthDataEx3Service.getRecord(recordId).orElseThrow(() ->
                new EntityNotFoundException(HealthDataRecordEx3.class));

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(record.getId());
        }

        return record;
    }

    /** Retrieves all records for the given user and time range. */
    @GetMapping(path="/v1/admin/exporter3/participants/{userId}/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForUser(@PathVariable String userId,
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        String healthCode = accountService.getHealthCodeForAccount(AccountId.forId(session.getAppId(), userId));
        if (healthCode == null) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForHealthCode(healthCode, createdOnStartDateTime, createdOnEndDateTime,
                pageSizeInt, offsetKey)
                .withRequestParam("userId", userId)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }

    /** Retrieves all records in the current app for the given time range. */
    @GetMapping(path="/v1/admin/exporter3/apps/self/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForCurrentApp(
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForApp(session.getAppId(), createdOnStartDateTime, createdOnEndDateTime,
                pageSizeInt, offsetKey)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }

    /** Retrieves all records in the current app for the given study and time range. */
    @GetMapping(path="/v1/admin/exporter3/apps/self/studies/{studyId}/healthdata")
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForStudy(@PathVariable String studyId,
            @RequestParam(required = false) String createdOnStart, @RequestParam(required = false) String createdOnEnd,
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);

        DateTime createdOnStartDateTime = BridgeUtils.getDateTimeOrDefault(createdOnStart, null);
        DateTime createdOnEndDateTime = BridgeUtils.getDateTimeOrDefault(createdOnEnd, null);
        Integer pageSizeInt = BridgeUtils.getIntegerOrDefault(pageSize, null);
        return healthDataEx3Service.getRecordsForAppAndStudy(session.getAppId(), studyId, createdOnStartDateTime,
                createdOnEndDateTime, pageSizeInt, offsetKey)
                .withRequestParam("studyId", studyId)
                .withRequestParam(ResourceList.START_TIME, createdOnStart)
                .withRequestParam(ResourceList.END_TIME, createdOnEnd)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSizeInt)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }
}
