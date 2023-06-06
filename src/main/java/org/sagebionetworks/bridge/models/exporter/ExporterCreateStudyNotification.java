package org.sagebionetworks.bridge.models.exporter;

/**
 * This is the notification sent to subscribers when a study is initialized for Exporter 3.0 in an app they subscribe
 * to.
 */
public class ExporterCreateStudyNotification {
    private String appId;
    private String parentProjectId;
    private String rawFolderId;
    private String studyId;

    /** App that the study lives in. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Synapse project initialized for the study. */
    public String getParentProjectId() {
        return parentProjectId;
    }

    public void setParentProjectId(String parentProjectId) {
        this.parentProjectId = parentProjectId;
    }

    /** Synapse folder containing the raw data for the study. */
    public String getRawFolderId() {
        return rawFolderId;
    }

    public void setRawFolderId(String rawFolderId) {
        this.rawFolderId = rawFolderId;
    }

    /** Study that was initialized. */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
}
