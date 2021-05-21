package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@BridgeTypeName("ActivityEvent")
@DynamoDBTable(tableName = "TaskEvent")
@JsonFilter("filter")
public class DynamoActivityEvent implements ActivityEvent {

    private String studyId;
    private String healthCode;
    private String answerValue;
    private DateTime timestamp;
    private String eventId;
    private ActivityEventUpdateType updateType;
    
    @DynamoDBHashKey
    @Override
    public String getHealthCode() {
        if (studyId == null || healthCode == null || healthCode.endsWith(":" + studyId)) {
            return healthCode;
        }
        return (healthCode + ":" + studyId);
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @Override
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    @Override
    public String getAnswerValue() {
        return answerValue;
    }
    public void setAnswerValue(String answerValue) {
        this.answerValue = answerValue;
    }
    @Override
    @JsonSerialize(using = DateTimeSerializer.class)
    @DynamoDBTypeConverted(converter = DateTimeToLongMarshaller.class)
    public DateTime getTimestamp() {
        return timestamp;
    }
    @JsonDeserialize(using = DateTimeDeserializer.class)
    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
    @DynamoDBRangeKey
    @Override
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    @JsonIgnore
    @DynamoDBIgnore
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    public void setUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    
    public static class Builder {
        private String healthCode;
        private String studyId;
        private DateTime timestamp;
        private ActivityEventObjectType objectType;
        private String objectId;
        private ActivityEventType eventType;
        private String answerValue;
        private ActivityEventUpdateType updateType;
        
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withTimestamp(DateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public Builder withObjectType(ActivityEventObjectType type) {
            this.objectType = type;
            return this;
        }
        public Builder withObjectId(String objectId) {
            this.objectId = objectId;
            return this;
        }
        public Builder withEventType(ActivityEventType objectType) {
            this.eventType = objectType;
            return this;
        }
        public Builder withAnswerValue(String answerValue) {
            this.answerValue = answerValue;
            return this;
        }
        public Builder withUpdateType(ActivityEventUpdateType updateType) {
            this.updateType = updateType;
            return this;
        }
        private String getEventId() {
            if (objectType == null) {
                return null;
            }
            String typeName = objectType.name().toLowerCase();
            if (objectId != null && eventType != null) {
                return String.format("%s:%s:%s", typeName, objectId, eventType.name().toLowerCase());
            } else if (objectId != null) {
                return String.format("%s:%s", typeName, objectId);
            }
            return typeName;
        }
        
        public DynamoActivityEvent build() {
            // For custom events, we need to retrieve the update type from app settings as part of the 
            // event's construction. But for all other object types, we know the update behavior
            if (objectType != null && updateType == null) {
                updateType = objectType.getUpdateType();    
            }
            if (updateType == null) {
                throw new IllegalStateException("No update type configured for event: " + getEventId());
            }
            DynamoActivityEvent event = new DynamoActivityEvent();
            event.setHealthCode(healthCode);
            event.setStudyId(studyId);
            event.setTimestamp(timestamp);
            event.setEventId(getEventId());
            event.setAnswerValue(answerValue);
            event.setUpdateType(updateType);
            return event;
        }
    }
}
