package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataEx3Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.validators.HealthDataRecordEx3Validator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Exporter 3 version of the Health Data Service. Methods in this class only apply to the EX3 implementation of Health
 * Data Records, not to the original implementation of Health Data records.
 */
@Component
public class HealthDataEx3Service {
    private static final int MAX_DATE_RANGE_DAYS = 60;

    private HealthDataEx3Dao healthDataEx3Dao;

    @Autowired
    public final void setHealthDataEx3Dao(HealthDataEx3Dao healthDataEx3Dao) {
        this.healthDataEx3Dao = healthDataEx3Dao;
    }

    /** Create or update health data record. Returns the created or updated record. */
    public HealthDataRecordEx3 createOrUpdateRecord(HealthDataRecordEx3 record) {
        if (record == null) {
            throw new InvalidEntityException("Health data record must not be null");
        }
        Validate.entityThrowingException(HealthDataRecordEx3Validator.INSTANCE, record);

        return healthDataEx3Dao.createOrUpdateRecord(record);
    }

    /** Deletes all health data records for the given health code. */
    public void deleteRecordsForHealthCode(String healthCode) {
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException("Health code must be specified");
        }

        healthDataEx3Dao.deleteRecordsForHealthCode(healthCode);
    }

    /** Retrieves the record for the given ID. */
    public Optional<HealthDataRecordEx3> getRecord(String id) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException("ID must be specified");
        }

        return healthDataEx3Dao.getRecord(id);
    }

    /** Retrieves all records for the given healthcode and time range. */
    public List<HealthDataRecordEx3> getRecordsForHealthCode(String healthCode, DateTime createdOnStart,
            DateTime createdOnEnd) {
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException("Health code must be specified");
        }
        validateCreatedOnParameters(createdOnStart, createdOnEnd);

        return healthDataEx3Dao.getRecordsForHealthCode(healthCode, createdOnStart.getMillis(),
                createdOnEnd.getMillis());
    }

    /** Retrieves all records for the given app and time range. */
    public List<HealthDataRecordEx3> getRecordsForApp(String appId, DateTime createdOnStart, DateTime createdOnEnd) {
        if (StringUtils.isBlank(appId)) {
            throw new BadRequestException("App ID must be specified");
        }
        validateCreatedOnParameters(createdOnStart, createdOnEnd);

        return healthDataEx3Dao.getRecordsForApp(appId, createdOnStart.getMillis(), createdOnEnd.getMillis());
    }

    /** Retrieves all records for the given app, study, and time range. */
    public List<HealthDataRecordEx3> getRecordsForAppAndStudy(String appId, String studyId, DateTime createdOnStart,
            DateTime createdOnEnd) {
        if (StringUtils.isBlank(appId)) {
            throw new BadRequestException("App ID must be specified");
        }
        if (StringUtils.isBlank(studyId)) {
            throw new BadRequestException("Study ID must be specified");
        }
        validateCreatedOnParameters(createdOnStart, createdOnEnd);

        return healthDataEx3Dao.getRecordsForAppAndStudy(appId, studyId, createdOnStart.getMillis(),
                createdOnEnd.getMillis());
    }

    // Package-scoped for unit tests.
    static void validateCreatedOnParameters(DateTime start, DateTime end) {
        if (start == null) {
            throw new BadRequestException("Start time must be specified");
        }
        if (end == null) {
            throw new BadRequestException("End time must be specified");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("Start time must be before end time");
        }
        if (start.plusDays(MAX_DATE_RANGE_DAYS).isBefore(end)) {
            throw new BadRequestException("Maximum time range is " + MAX_DATE_RANGE_DAYS + " days");
        }
    }
}
