package org.sagebionetworks.bridge.models.exporter;

/** Info about a health data record that was exported to Synapse. */
public class ExportedRecordInfo {
    private String parentProjectId;
    private String rawFolderId;
    private String fileEntityId;
    private String s3Bucket;
    private String s3Key;

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
