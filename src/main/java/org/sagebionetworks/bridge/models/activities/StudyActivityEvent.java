package org.sagebionetworks.bridge.models.activities;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "StudyActivityEvents")
@IdClass(StudyActivityEventId.class)
@BridgeTypeName("ActivityEvent")
public class StudyActivityEvent implements BridgeEntity {

    @JsonIgnore
    private String appId; 
    @JsonIgnore
    @Id
    private String userId;
    @JsonIgnore
    @Id
    private String studyId;
    @Id
    private String eventId;
    @Id
    @Column(name = "eventTimestamp")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @JsonIgnore
    private DateTime createdOn;
    @Transient
    @JsonIgnore
    private ActivityEventUpdateType updateType;
    
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
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
    public String getAnswerValue() {
        return answerValue;
    }
    public void setAnswerValue(String answerValue) {
        this.answerValue = answerValue;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    public void setUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    
}