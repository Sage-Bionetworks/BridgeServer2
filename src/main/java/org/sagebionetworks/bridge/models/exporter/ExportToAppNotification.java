package org.sagebionetworks.bridge.models.exporter;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Notification for when a health data is exported to an app-wide Synapse project for Exporter 3.0. This is also used
 * by the worker to trigger notifications and generate study-specific notifications.
 */
public class ExportToAppNotification implements BridgeEntity {
    public static class RecordInfo {
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

    private String appId;
    private String recordId;
    private RecordInfo record;
    private Map<String, RecordInfo> studyRecords = new HashMap<>();

    /** App that the health data is exported for. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Record ID of the health data that is exported. */
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /**
     * Record that is exported to the app-wide Synapse project. May be null if there is no app-wide Synapse project
     * configured.
     */
    public RecordInfo getRecord() {
        return record;
    }

    public void setRecord(RecordInfo record) {
        this.record = record;
    }

    /**
     * Records that are exported to the study-specific Synapse project, keyed by study ID. May be empty if there are no
     * study-specific Synapse projects configured.
     */
    public Map<String, RecordInfo> getStudyRecords() {
        return studyRecords;
    }

    public void setStudyRecords(Map<String, RecordInfo> studyRecords) {
        this.studyRecords = studyRecords != null ? studyRecords : new HashMap<>();
    }
}
