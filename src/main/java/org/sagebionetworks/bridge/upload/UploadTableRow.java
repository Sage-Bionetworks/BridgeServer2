package org.sagebionetworks.bridge.upload;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.HibernateUploadTableRow;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/** Represents the tabular format of an upload. This is the intermediate state before CSV generation. */
@BridgeTypeName("UploadTableRow")
@JsonDeserialize(as = HibernateUploadTableRow.class)
public interface UploadTableRow extends BridgeEntity {
    /**
     * App ID that this table row is part of. This never needs to be specified as part of the request and is
     * automatically determined by the server.
     */
    String getAppId();
    void setAppId(String appId);

    /**
     * Study ID that this table row is part of. This never needs to be specified as part of the request and is
     * automatically determined by the server.
     */
    String getStudyId();
    void setStudyId(String studyId);

    /**
     * Record ID represented by this table row. This is unique within a study, but a record may be exported to multiple
     * studies, and thus the same record ID may appear in multiple studies. This is required.
     */
    String getRecordId();
    void setRecordId(String recordId);

    /** Assessment ID that this upload represents. This is required. */
    String getAssessmentId();
    void setAssessmentId(String assessmentId);

    /** Assessment revision that this upload represents. This is required. */
    int getAssessmentRevision();
    void setAssessmentRevision(int assessmentRevision);

    /**
     * When this upload was created. This is determined by the assessment-specific "summarize" method in the Worker.
     * If not specified, the server will automatically set this to the current time.
     */
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);

    /** Whether this upload is test data. If not specified, defaults to false. */
    boolean isTestData();
    void setTestData(boolean testData);

    /** Health code of the participant who uploaded this row. This is required. */
    String getHealthCode();
    void setHealthCode(String healthCode);

    /**
     * Participant version of the user who uploaded this row. This might not be present if the participant version was
     * never created, eg legacy participants.
     */
    Integer getParticipantVersion();
    void setParticipantVersion(Integer participantVersion);

    /**
     * Metadata for this upload. This is a map of key-value pairs, where the key is the column name and the value is
     * the string representation of the column value. This includes common metadata such as clientInfo, startDate, and
     * endDate. Cannot be larger than 64kb.
     *
     * This will never be null. If there is no metadata, this will be an empty map.
     */
    Map<String, String> getMetadata();
    void setMetadata(Map<String, String> metadata);

    /**
     * Data for this upload. This is a map of key-value pairs, where the key is the column name and the value is the
     * string representation of the column value. This includes data specific to the assessment, such as answers to
     * survey questions and scores. Cannot be larger than 64kb.
     *
     * This will never be null. If there is no data, this will be an empty map.
     */
    Map<String, String> getData();
    void setData(Map<String, String> data);
}
