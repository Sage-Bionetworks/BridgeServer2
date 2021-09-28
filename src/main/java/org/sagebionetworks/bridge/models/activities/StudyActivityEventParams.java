package org.sagebionetworks.bridge.models.activities;

import org.joda.time.DateTime;

/**
 * The parameters used to construct a valid StudyActivityEvent. When events are submitted through
 * the API, they are submitted partly as a compound string with some of these values encoded in
 * the string, and some provided in separate JSON fields. These values are deserialized into the 
 * StudyActivityEventRequest object, which can produce this parameters object. In our system code,
 * these values can be supplied directly through this parameter object. The 
 * StudyActivityEventParams is accepted by the StudyActivityEventService so that both these code 
 * paths have been aligned by the time we call the service.
 */
public class StudyActivityEventParams {
    
    private String appId;
    private String userId;
    private String studyId;
    private String clientTimeZone;
    private DateTime createdOn;
    private ActivityEventObjectType objectType;
    private String objectId;
    private ActivityEventType eventType;
    private ActivityEventUpdateType updateType;
    private String answerValue;
    private DateTime timestamp;
    
    public StudyActivityEventParams withAppId(String appId) {
        this.appId = appId;
        return this;
    }
    public StudyActivityEventParams withUserId(String userId) {
        this.userId = userId;
        return this;
    }
    public StudyActivityEventParams withStudyId(String studyId) {
        this.studyId = studyId;
        return this;
    }
    public StudyActivityEventParams withClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
        return this;
    }
    public StudyActivityEventParams withObjectType(ActivityEventObjectType objectType) {
        this.objectType = objectType;
        return this;
    }
    public StudyActivityEventParams withObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }
    public StudyActivityEventParams withAnswerValue(String answerValue) {
        this.answerValue = answerValue;
        return this;
    }
    public StudyActivityEventParams withTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    public StudyActivityEventParams withCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
        return this;
    }
    public StudyActivityEventParams withUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
        return this;
    }
    public StudyActivityEventParams withEventType(ActivityEventType eventType) {
        this.eventType = eventType;
        return this;
    }
    
    public StudyActivityEvent toStudyActivityEvent() {
        StudyActivityEvent event = new StudyActivityEvent();
        // this is a total failure to initialize the builder properly.
        if (objectType == null) {
            return event;
        }
        if (updateType == null) {
            updateType = objectType.getUpdateType();
        }
        event.setAppId(appId);
        event.setUserId(userId);
        event.setStudyId(studyId);
        event.setClientTimeZone(clientTimeZone);
        event.setTimestamp(timestamp);
        event.setEventId(objectType.getEventId(objectId, eventType, answerValue));
        event.setAnswerValue(answerValue);
        event.setCreatedOn(createdOn);
        event.setUpdateType(updateType);
        return event;
    }
}