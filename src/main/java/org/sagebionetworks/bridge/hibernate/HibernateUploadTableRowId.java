package org.sagebionetworks.bridge.hibernate;

import java.io.Serializable;

/** Represents the composite key for UploadTableRow, which is appId, studyId, and recordId. */
@SuppressWarnings("serial")
public class HibernateUploadTableRowId implements Serializable {
    private final String appId;
    private final String studyId;
    private final String recordId;

    public HibernateUploadTableRowId(String appId, String studyId, String recordId) {
        this.appId = appId;
        this.studyId = studyId;
        this.recordId = recordId;
    }

    public String getAppId() {
        return appId;
    }

    public String getStudyId() {
        return studyId;
    }

    public String getRecordId() {
        return recordId;
    }
}
