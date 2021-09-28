package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

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

/**
 * When events are submitted through the API, they are submitted partly as a compound string along
 * with some separate JSON fields. The compound string may or may not be valid so it it parsed 
 * and all the data is returned as a Builder for a study activity event. In our system code, these 
 * values can be supplied directly through the builder.
 */
@Entity
@Table(name = "StudyActivityEvents")
@IdClass(StudyActivityEventId.class)
@BridgeTypeName("ActivityEvent")
public class StudyActivityEvent implements HasTimestamp, BridgeEntity {

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
    private DateTime createdOn;
    @Transient
    private int recordCount;
    @Transient
    private ActivityEventUpdateType updateType;
    
    // Hibernate is happy with default (package) visibility constructor and setters, ensuring
    // that objects can only be constructed in code through a builder.
    
    StudyActivityEvent() {}
    
    public String getAppId() {
        return appId;
    }
    void setAppId(String appId) {
        this.appId = appId;
    }
    public String getUserId() {
        return userId;
    }
    void setUserId(String userId) {
        this.userId = userId;
    }
    public String getStudyId() {
        return studyId;
    }
    void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getEventId() {
        return eventId;
    }
    void setEventId(String eventId) {
        this.eventId = eventId;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }
    void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
    public String getAnswerValue() {
        return answerValue;
    }
    void setAnswerValue(String answerValue) {
        this.answerValue = answerValue;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public int getRecordCount() {
        return recordCount;
    }
    void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    void setUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    
    public static final class Builder {
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
        private int recordCount;
        private String eventId;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withClientTimeZone(String clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }
        public Builder withObjectType(ActivityEventObjectType objectType) {
            this.objectType = objectType;
            return this;
        }
        public Builder withObjectId(String objectId) {
            this.objectId = objectId;
            return this;
        }
        public Builder withAnswerValue(String answerValue) {
            this.answerValue = answerValue;
            return this;
        }
        public Builder withTimestamp(DateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        public Builder withUpdateType(ActivityEventUpdateType updateType) {
            this.updateType = updateType;
            return this;
        }
        public Builder withEventType(ActivityEventType eventType) {
            this.eventType = eventType;
            return this;
        }
        public Builder withRecordCount(int recordCount) {
            this.recordCount = recordCount;
            return this;
        }
        /**
         * Events are stored with the compound event ID string. However, we do not want to construct
         * instances with this string alone, since it will not be validated. Nor can we hide this 
         * because our code is not organized in packages correctly. This method should be called by 
         * code that is constructing events from the database, or from a couple of exceptional 
         * places in our code where we are “faking” the existence of database events. Normal events 
         * should be parsed for correctness or constructed with object type, event type, and object ID.
         */
        public Builder withEventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public StudyActivityEvent build() {
            StudyActivityEvent event = new StudyActivityEvent();
            event.setAppId(appId);
            event.setUserId(userId);
            event.setStudyId(studyId);
            event.setClientTimeZone(clientTimeZone);
            event.setTimestamp(timestamp);
            event.setAnswerValue(answerValue);
            event.setCreatedOn(createdOn);
            event.setUpdateType(updateType);
            event.setRecordCount(recordCount);
            // We’re constructing the event with a known (already validated) event ID
            if (eventId != null) {
                if (updateType == null) {
                    event.setUpdateType(IMMUTABLE);    
                }
                event.setEventId(eventId);
            } 
            // We’re constructing the event ID from its constituent parts
            else if (objectType != null) {
                if (updateType == null) {
                    event.setUpdateType(objectType.getUpdateType());
                }
                event.setEventId(objectType.getEventId(objectId, eventType, answerValue));
            }
            return event;
        }        
    }
}