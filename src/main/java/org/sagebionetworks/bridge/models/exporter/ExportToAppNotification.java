package org.sagebionetworks.bridge.models.exporter;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Notification for when a health data is exported to an app-wide Synapse project for Exporter 3.0. This is also used
 * by the worker to trigger notifications and generate study-specific notifications.
 */
public class ExportToAppNotification implements BridgeEntity {
    private String appId;
    private String recordId;
    private ExportedRecordInfo record;
    private Map<String, ExportedRecordInfo> studyRecords = new HashMap<>();

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
    public ExportedRecordInfo getRecord() {
        return record;
    }

    public void setRecord(ExportedRecordInfo record) {
        this.record = record;
    }

    /**
     * Records that are exported to the study-specific Synapse project, keyed by study ID. May be empty if there are no
     * study-specific Synapse projects configured.
     */
    public Map<String, ExportedRecordInfo> getStudyRecords() {
        return studyRecords;
    }

    public void setStudyRecords(Map<String, ExportedRecordInfo> studyRecords) {
        this.studyRecords = studyRecords != null ? studyRecords : new HashMap<>();
    }
}
