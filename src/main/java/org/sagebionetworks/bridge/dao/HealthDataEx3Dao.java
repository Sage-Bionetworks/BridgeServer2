package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

/**
 * Exporter 3 version of the Health Data DAO. Methods in this interface only apply to the EX3 implementation of Health
 * Data Records, not to the original implementation of Health Data records.
 */
public interface HealthDataEx3Dao {
    /** Create or update health data record. Returns the created or updated record. */
    HealthDataRecordEx3 createOrUpdateRecord(HealthDataRecordEx3 record);

    /** Deletes all health data records for the given health code. */
    void deleteRecordsForHealthCode(String healthCode);

    /** Retrieves the record for the given ID. */
    Optional<HealthDataRecordEx3> getRecord(String id);

    /** Retrieves all records for the given healthcode and time range. */
    ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForHealthCode(String healthCode, long createdOnStart,
            long createdOnEnd, int pageSize, String offsetKey);

    /** Retrives all records for the given app and time range. */
    ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForApp(String appId, long createdOnStart,
            long createdOnEnd, int pageSize, String offsetKey);

    /** Retrives all records for the given app, study, and time range. */
    ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForAppAndStudy(String appId, String studyId,
            long createdOnStart, long createdOnEnd, int pageSize, String offsetKey);
}
