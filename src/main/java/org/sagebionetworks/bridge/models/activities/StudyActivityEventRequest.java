package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;

/**
 * We need to accumulate information from the client, information from the 
 * code path, and then put it together to create a StudyActivityEvent with
 * the correct event ID.
 */
public class StudyActivityEventRequest {

    private String appId; 
    private String userId;
    private String studyId;
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    private DateTime createdOn;
    private ActivityEventObjectType objectType;
    private String objectId;
    private ActivityEventType eventType;
    private ActivityEventUpdateType updateType;
    
    public StudyActivityEventRequest() { 
    }
    
    /**
     * These are the only properties an API user can submit for custom events.
     * The rest are set on the server.
     */
    @JsonCreator
    public StudyActivityEventRequest(
            @JsonProperty("eventId") @JsonAlias("eventKey") String thisObjectId,
            @JsonProperty("timestamp") DateTime timestamp, 
            @JsonProperty("answerValue") String answerValue,
            @JsonProperty("clientTimeZone") String clientTimeZone) {
        
        // this possesses logic we want to run, however the value is set
        this.objectId(thisObjectId); 
        this.timestamp = timestamp;
        this.answerValue = answerValue;
        this.clientTimeZone = clientTimeZone;
    }
    
    public StudyActivityEventRequest appId(String appId) {
        this.appId = appId;
        return this;
    }
    public StudyActivityEventRequest userId(String userId) {
        this.userId = userId;
        return this;
    }
    public StudyActivityEventRequest studyId(String studyId) {
        this.studyId = studyId;
        return this;
    }
    public StudyActivityEventRequest timestamp(DateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    public StudyActivityEventRequest answerValue(String answerValue) {
        this.answerValue = answerValue;
        return this;
    }
    public StudyActivityEventRequest clientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
        return this;
    }
    public StudyActivityEventRequest createdOn(DateTime createdOn) {
        this.createdOn = createdOn;
        return this;
    }
    public StudyActivityEventRequest objectType(ActivityEventObjectType objectType) {
        this.objectType = objectType;
        return this;
    }
    public StudyActivityEventRequest updateTypeForCustomEvents(Map<String,ActivityEventUpdateType> map) {
        if (objectType != null) {
            updateType = objectType.getUpdateType();    
        }
        if (objectType == CUSTOM) {
            updateType = map.get(objectId);
        }
        if (updateType == null) {
            updateType = IMMUTABLE;
        }
        return this;
    }
    public StudyActivityEventRequest objectId(String thisObjectId) {
        if (thisObjectId != null && thisObjectId.toLowerCase().startsWith("custom:")) {
            thisObjectId = thisObjectId.substring(7);
        }
        this.objectId = thisObjectId;
        return this;
    }
    public StudyActivityEventRequest eventType(ActivityEventType eventType) {
        this.eventType = eventType;
        return this;
    }
    public StudyActivityEventRequest updateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
        return this;
    }
    public String getAppId() {
        return appId;
    }
    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }
    public String getAnswerValue() {
        return answerValue;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public ActivityEventObjectType getObjectType() {
        return objectType;
    }
    public String getObjectId() {
        return objectId;
    }
    public ActivityEventType getEventType() {
        return eventType;
    }
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    public StudyActivityEvent toStudyActivityEvent( ) {
        String eventId = null;
        if (objectType != null) {
            eventId = objectType.getEventId(objectId, eventType, answerValue);    
        }
        StudyActivityEvent event = new StudyActivityEvent();
        event.setAppId(appId);
        event.setUserId(userId);
        event.setStudyId(studyId);
        event.setEventId(eventId);
        event.setTimestamp(timestamp);
        event.setAnswerValue(answerValue);
        event.setClientTimeZone(clientTimeZone);
        event.setCreatedOn(createdOn);
        event.setUpdateType(updateType);
        return event;
    }
    
    public StudyActivityEventRequest copy() {
        StudyActivityEventRequest copy = new StudyActivityEventRequest();
        copy.appId = appId; 
        copy.userId = userId;
        copy.studyId = studyId;
        copy.timestamp = timestamp;
        copy.answerValue = answerValue;
        copy.clientTimeZone = clientTimeZone;
        copy.createdOn = createdOn;
        copy.objectType = objectType;
        copy.objectId = objectId;
        copy.eventType = eventType;
        copy.updateType = updateType;
        return copy;
    }
}
