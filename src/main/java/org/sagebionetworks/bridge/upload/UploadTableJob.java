package org.sagebionetworks.bridge.upload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.HibernateUploadTableJob;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/** Represents a worker job to generate the CSV for uploads for a study. */
@BridgeTypeName("UploadTableRow")
@JsonDeserialize(as = HibernateUploadTableJob.class)
public interface UploadTableJob extends BridgeEntity {
    /** Status of the CSV generation job. */
    enum Status {
        /** Job is in progress. */
        IN_PROGRESS,

        /** Job is complete. */
        SUCCEEDED,

        /** Job failed. */
        FAILED
    }

    /** Factory method for creating a new UploadTableJob. */
    static UploadTableJob create() {
        return new HibernateUploadTableJob();
    }

    /** Unique GUID for this job. */
    String getJobGuid();
    void setJobGuid(String jobGuid);

    /** App ID that this job is part of. */
    String getAppId();
    void setAppId(String appId);

    /** Study ID that this job is part of. */
    String getStudyId();
    void setStudyId(String studyId);

    /** Timestamp for when this job was requested. */
    DateTime getRequestedOn();
    void setRequestedOn(DateTime requestedOn);

    /** Status of the CSV generation job. */
    Status getStatus();
    void setStatus(Status status);

    /** S3 filename for the generated CSV. May be null if the job is in progress or failed. */
    String getS3Key();
    void setS3Key(String s3Key);
}
