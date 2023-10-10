package org.sagebionetworks.bridge.hibernate;

import java.util.HashMap;
import java.util.Map;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.upload.UploadTableRow;

/** Hibernate implementation of UploadTableRow. */
@BridgeTypeName("UploadTableRow")
@Entity
@IdClass(HibernateUploadTableRowId.class)
@Table(name = "UploadTableRows")
public class HibernateUploadTableRow implements UploadTableRow {
    private String appId;
    private String studyId;
    private String recordId;
    private String assessmentId;
    private int assessmentRevision;
    private DateTime createdOn;
    private boolean testData;
    private String healthCode;
    private Integer participantVersion;
    private Map<String, String> metadata = new HashMap<>();
    private Map<String, String> data = new HashMap<>();

    @Id
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Id
    @Override
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    @Id
    @Override
    public String getRecordId() {
        return recordId;
    }

    @Override
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    @Override
    public String getAssessmentId() {
        return assessmentId;
    }

    @Override
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    @Override
    public int getAssessmentRevision() {
        return assessmentRevision;
    }

    @Override
    public void setAssessmentRevision(int assessmentRevision) {
        this.assessmentRevision = assessmentRevision;
    }

    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public boolean isTestData() {
        return testData;
    }

    @Override
    public void setTestData(boolean testData) {
        this.testData = testData;
    }

    @Override
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @Override
    public Integer getParticipantVersion() {
        return participantVersion;
    }

    @Override
    public void setParticipantVersion(Integer participantVersion) {
        this.participantVersion = participantVersion;
    }

    @Convert(converter = StringMapConverter.class)
    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    @Convert(converter = StringMapConverter.class)
    @Override
    public Map<String, String> getData() {
        return data;
    }

    @Override
    public void setData(Map<String, String> data) {
        this.data = data != null ? data : new HashMap<>();
    }
}
