package org.sagebionetworks.bridge.upload;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;

/** This encapsulates not just the table job, but also the S3 pre-signed URL to download results from. */
public class UploadTableJobResult implements BridgeEntity {
    private String jobGuid;
    private String studyId;
    private DateTime requestedOn;
    private UploadTableJob.Status status;
    private String url;
    private DateTime expiresOn;

    /** Convenience method to create a job result from a job. */
    public static UploadTableJobResult fromJob(UploadTableJob job) {
        UploadTableJobResult result = new UploadTableJobResult();
        result.setJobGuid(job.getJobGuid());
        result.setStudyId(job.getStudyId());
        result.setRequestedOn(job.getRequestedOn());
        result.setStatus(job.getStatus());
        return result;
    }

    /** Unique GUID for this job. */
    public String getJobGuid() {
        return jobGuid;
    }

    public void setJobGuid(String jobGuid) {
        this.jobGuid = jobGuid;
    }

    /** Study ID that this job is part of. */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** Timestamp for when this job was requested. */
    public DateTime getRequestedOn() {
        return requestedOn;
    }

    public void setRequestedOn(DateTime requestedOn) {
        this.requestedOn = requestedOn;
    }

    /** Status of the CSV generation job. */
    public UploadTableJob.Status getStatus() {
        return status;
    }

    public void setStatus(UploadTableJob.Status status) {
        this.status = status;
    }

    /** S3 pre-signed URL for the generated CSV. */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /** Timestamp for when the pre-signed URL expires. */
    public DateTime getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(DateTime expiresOn) {
        this.expiresOn = expiresOn;
    }
}
