package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;

/**
 * Accumulate information from the client, from the code path, and then put it together
 * to form a valid StudyActivityEvent with the correct event ID and update type.
 */
public class StudyActivityEventRequest {

    private String appId; 
    private String userId;
    private String studyId;
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    private DateTime createdOn;
    private ActivityEventObjectType objectType = CUSTOM;
    private String objectId;
    private ActivityEventType eventType;
    private ActivityEventUpdateType updateType = IMMUTABLE;
    private Map<String,ActivityEventUpdateType> customEvents;
    
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
    public StudyActivityEventRequest objectId(String objectId) {
        this.objectId = objectId;
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
    public StudyActivityEventRequest customEvents(Map<String,ActivityEventUpdateType> customEvents) {
        this.customEvents = customEvents;
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
        fixUpdateTypeAndObjectId();
        return objectId;
    }
    public ActivityEventType getEventType() {
        return eventType;
    }
    public ActivityEventUpdateType getUpdateType() {
        fixUpdateTypeAndObjectId();
        return updateType;
    }
    public StudyActivityEvent toStudyActivityEvent() {
        fixUpdateTypeAndObjectId();
        String eventId = objectType.getEventId(objectId, eventType, answerValue);
        
        StudyActivityEvent event = new StudyActivityEvent();
        event.setAppId(appId);
        event.setUserId(userId);
        event.setStudyId(studyId);
        event.setEventId(eventId);
        event.setTimestamp(timestamp);
        event.setAnswerValue(answerValue);
        event.setClientTimeZone(clientTimeZone);
        event.setCreatedOn(createdOn);
        return event;
    }
    private void fixUpdateTypeAndObjectId() { 
        // This will be reformatted by the CUSTOM object type
        if (objectId != null && objectId.toLowerCase().startsWith("custom:")) {
            objectId = objectId.substring(7);
        }
        updateType = objectType.getUpdateType();
        if (objectType == CUSTOM && customEvents != null) {
            updateType = customEvents.get(objectId);
            objectId = formatActivityEventId(customEvents.keySet(), objectId);
        }
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
