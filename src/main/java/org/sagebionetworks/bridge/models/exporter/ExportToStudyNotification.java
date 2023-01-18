package org.sagebionetworks.bridge.models.exporter;

/** Notification for when a health data is exported to a study-specific Synapse project for Exporter 3.0. */
public class ExportToStudyNotification {
    private String appId;
    private String studyId;
    private String recordId;
    private String parentProjectId;
    private String rawFolderId;
    private String fileEntityId;
    private String s3Bucket;
    private String s3Key;

    /** App that the health data is exported for. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Study that the health data is exported for. */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** Record ID of the health data that is exported. */
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /** Synapse project that the health data is exported to. */
    public String getParentProjectId() {
        return parentProjectId;
    }

    public void setParentProjectId(String parentProjectId) {
        this.parentProjectId = parentProjectId;
    }

    /** Synapse folder that the health data is exported to. */
    public String getRawFolderId() {
        return rawFolderId;
    }

    public void setRawFolderId(String rawFolderId) {
        this.rawFolderId = rawFolderId;
    }

    /** Synapse file entity of the health data that is exported. */
    public String getFileEntityId() {
        return fileEntityId;
    }

    public void setFileEntityId(String fileEntityId) {
        this.fileEntityId = fileEntityId;
    }

    /** S3 bucket that contains the exported health data. */
    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    /** S3 key that of the exported health data. */
    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }
}
