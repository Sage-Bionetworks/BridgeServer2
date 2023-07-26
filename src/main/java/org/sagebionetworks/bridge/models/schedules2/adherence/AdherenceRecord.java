package org.sagebionetworks.bridge.models.schedules2.adherence;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "AdherenceRecords")
@IdClass(AdherenceRecordId.class)
public class AdherenceRecord implements BridgeEntity {
    
    @JsonIgnore
    private String appId;
    @Id
    private String userId;
    @Id
    @JsonIgnore
    private String studyId;
    @Id
    private String instanceGuid;
    @Id
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime eventTimestamp;
    @Id
    @JsonIgnore
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime instanceTimestamp;
    
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime startedOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime finishedOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime uploadedOn;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode clientData;
    private String clientTimeZone;
    private boolean declined;

    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode postProcessingAttributes;

    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime postProcessingCompletedOn;

    private String postProcessingStatus;

    private String sessionGuid;
    private String assessmentGuid;
    @CollectionTable(name = "AdherenceUploads",
            joinColumns = { @JoinColumn(name = "userId", referencedColumnName = "userId"),
                    @JoinColumn(name = "studyId", referencedColumnName = "studyId"),
                    @JoinColumn(name = "instanceGuid", referencedColumnName = "instanceGuid"), 
                    @JoinColumn(name = "eventTimestamp", referencedColumnName = "eventTimestamp"),
                    @JoinColumn(name = "instanceTimestamp", referencedColumnName = "instanceTimestamp")})
    @Column(name = "uploadId")
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> uploadIds;
    
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    public void setInstanceGuid(String instanceGuid) {
        this.instanceGuid = instanceGuid;
    }
    public DateTime getStartedOn() {
        return startedOn;
    }
    public void setStartedOn(DateTime startedOn) {
        this.startedOn = startedOn;
    }
    public DateTime getFinishedOn() {
        return finishedOn;
    }
    public void setFinishedOn(DateTime finishedOn) {
        this.finishedOn = finishedOn;
    }
    public DateTime getUploadedOn() {
        return uploadedOn;
    }
    public void setUploadedOn(DateTime uploadedOn) {
        this.uploadedOn = uploadedOn;
    }
    /**
     * Persistent activities can be done more than once, and are only differentiated by their
     * start times, while other activities can only be done once in a time stream, so we use
     * the startedOn timestamp for persistent records and the eventTimestamp for other records.
     * This is set on all updates and is not exposed through the API, and it forms part of 
     * the record’s primary key.
     */
    public DateTime getInstanceTimestamp() {
        return instanceTimestamp;
    }
    public void setInstanceTimestamp(DateTime instanceTimestamp) {
        this.instanceTimestamp = instanceTimestamp;
    }
    /**
     * The eventTimestamp is reported in the clientTimeZone of AdherenceRecord, and not the 
     * underlying event itself. This is a limitation of how we convert events into a map,
     * at least for now.
     */
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }
    public void setEventTimestamp(DateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
    /**
     * Arbitrary JSON information stored by the client (limited to ~65k).
     */
    public JsonNode getClientData() {
        return clientData;
    }
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    public boolean isDeclined() {
        return declined;
    }
    public void setDeclined(boolean declined) {
        this.declined = declined;
    }

    /** Key-value pairs related to upload post-processing (maximum size 65k). */
    public JsonNode getPostProcessingAttributes() {
        return postProcessingAttributes;
    }

    public void setPostProcessingAttributes(JsonNode postProcessingAttributes) {
        this.postProcessingAttributes = postProcessingAttributes;
    }

    /** When the post-processing step was completed. */
    public DateTime getPostProcessingCompletedOn() {
        return postProcessingCompletedOn;
    }

    public void setPostProcessingCompletedOn(DateTime postProcessingCompletedOn) {
        this.postProcessingCompletedOn = postProcessingCompletedOn;
    }

    /**
     * Short string that represents the current status of the upload in the post-processing pipeline. This may be app
     * or study specific. Examples include: "Pending", "SchemaVerified", "SchemaVerificationFailed", "DataInParquet",
     * "DataScored". Must be 255 characters or less.
     */
    public String getPostProcessingStatus() {
        return postProcessingStatus;
    }

    public void setPostProcessingStatus(String postProcessingStatus) {
        this.postProcessingStatus = postProcessingStatus;
    }

    public String getSessionGuid() {
        return sessionGuid;
    }
    public void setSessionGuid(String sessionGuid) {
        this.sessionGuid = sessionGuid;
    }
    public String getAssessmentGuid() {
        return assessmentGuid;
    }
    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }
    public Set<String> getUploadIds() {
        return (uploadIds != null) ? uploadIds : ImmutableSet.of();
    }
    public void setUploadIds(Set<String> uploadIds) {
        this.uploadIds = uploadIds;
    }
    public void addUploadId(String uploadId) {
        if (uploadIds == null) {
            uploadIds = new HashSet<>();
        }
        uploadIds.add(uploadId);
    }
}