package org.sagebionetworks.bridge.hibernate;

import java.io.Serializable;

/**
 * Represents the composite key for UploadTableRow, which is appId, studyId, and recordId. Default constructor and
 * setters are required by Hibernate.
 */
@SuppressWarnings("serial")
public class HibernateUploadTableRowId implements Serializable {
    private String appId;
    private String studyId;
    private String recordId;

    public HibernateUploadTableRowId() {
    }

    public HibernateUploadTableRowId(String appId, String studyId, String recordId) {
        this.appId = appId;
        this.studyId = studyId;
        this.recordId = recordId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
}
