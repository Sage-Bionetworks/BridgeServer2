package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadataView;

/**
 * This helpful data structure includes the upload, health data record, adherence records, and timeline metadata for
 * a given upload/record ID, if they exist.
 */
public class UploadViewEx3 implements BridgeEntity {
    private String id;
    private String healthCode;
    private String userId;
    private List<AdherenceRecord> adherenceRecordsForSchedule;
    private List<AdherenceRecord> adherenceRecordsForUpload;
    private HealthDataRecordEx3 record;
    private TimelineMetadataView timelineMetadata;
    private Upload upload;

    /**
     * Upload ID. This is the same as Record ID. Provided here for convenience, since the upload or record might not
     * always exist.
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** Health code of the user that submitted the upload or record. */
    public String getHealthCode() {
        return healthCode;
    }

    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** ID of the user that submitted the upload or record. */
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** Adherence records associated with this upload/record via the associated instanceGuid. */
    public List<AdherenceRecord> getAdherenceRecordsForSchedule() {
        return adherenceRecordsForSchedule;
    }

    public void setAdherenceRecordsForSchedule(List<AdherenceRecord> adherenceRecordsForSchedule) {
        this.adherenceRecordsForSchedule = adherenceRecordsForSchedule;
    }

    /** Adherence records associated with this upload/record via the upload ID. */
    public List<AdherenceRecord> getAdherenceRecordsForUpload() {
        return adherenceRecordsForUpload;
    }

    public void setAdherenceRecordsForUpload(List<AdherenceRecord> adherenceRecordsForUpload) {
        this.adherenceRecordsForUpload = adherenceRecordsForUpload;
    }

    /** Health data record corresponding to this upload, if it exists. */
    public HealthDataRecordEx3 getRecord() {
        return record;
    }

    public void setRecord(HealthDataRecordEx3 record) {
        this.record = record;
    }

    /** Timeline metadata associated with this upload/record. */
    public TimelineMetadataView getTimelineMetadata() {
        return timelineMetadata;
    }

    public void setTimelineMetadata(TimelineMetadataView timelineMetadata) {
        this.timelineMetadata = timelineMetadata;
    }

    /** Upload corresponding to the health data record, if it exists. */
    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }
}
