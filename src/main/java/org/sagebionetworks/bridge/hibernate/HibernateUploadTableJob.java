package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.upload.UploadTableJob;

/** Hibernate implementation of UploadTableJob. */
@BridgeTypeName("UploadTableJob")
@Entity
@Table(name = "UploadTableJobs")
public class HibernateUploadTableJob implements UploadTableJob {
    private String jobGuid;
    private String appId;
    private String studyId;
    private DateTime requestedOn;
    private Status status;
    private String s3Key;

    @Id
    @Override
    public String getJobGuid() {
        return jobGuid;
    }

    @Override
    public void setJobGuid(String jobGuid) {
        this.jobGuid = jobGuid;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getRequestedOn() {
        return requestedOn;
    }

    @Override
    public void setRequestedOn(DateTime requestedOn) {
        this.requestedOn = requestedOn;
    }

    @Enumerated(EnumType.STRING)
    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String getS3Key() {
        return s3Key;
    }

    @Override
    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }
}
