package org.sagebionetworks.bridge.models.activities;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_BURST;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;

/**
 * Accumulate information from the client and provide the parsing logic to create 
 * the appropriate StudyActivityEventParams object. If the eventKey is not found to 
 * be valid, the parameter object that results will generate an activity event with 
 * a null eventId.
 */
public class StudyActivityEventRequest {

    private String eventKey;
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    
    @JsonCreator
    public StudyActivityEventRequest(
            @JsonProperty("eventId") @JsonAlias("eventKey") String eventKey,
            @JsonProperty("timestamp") DateTime timestamp, 
            @JsonProperty("answerValue") String answerValue,
            @JsonProperty("clientTimeZone") String clientTimeZone) {
        this.eventKey = eventKey;
        this.timestamp = timestamp;
        this.answerValue = answerValue;
        this.clientTimeZone = clientTimeZone;
    }
    
    // Getters exist for testing purposes only
    
    protected String getEventKey() {
        return eventKey;
    }
    protected DateTime getTimestamp() {
        return timestamp;
    }
    protected String getAnswerValue() {
        return answerValue;
    }
    protected String getClientTimeZone() {
        return clientTimeZone;
    }
    
    /**
     * Convert a request using a compound eventId into the individual fields of the 
     * parameter object. If the string is not valid, the builder is not valid, the 
     * study activity event it generates will be null, and validation will fail.
     */
    public StudyActivityEvent.Builder parse(StudyActivityEventMap eventMap) {
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder();
        builder.withTimestamp(timestamp);
        builder.withClientTimeZone(clientTimeZone);

        if (isBlank(eventKey)) {
            return builder;
        }
        String[] elements = eventKey.split(":");

        ActivityEventObjectType objectType = parseObjectType(elements[0]);
        // However, custom events were originally submitted without a prefix, so check that
        // This covers system events which have no qualifiers
        if (objectType != null) {
            builder.withObjectType(objectType);
            builder.withUpdateType(objectType.getUpdateType());
        }
        else if (elements.length == 1 && eventMap.hasCustomId(elements[0])) {
            builder.withObjectType(CUSTOM);
            builder.withObjectId(elements[0]);
            builder.withUpdateType(eventMap.getCustomUpdateType((elements[0])));
        }
        // Fully specified custom events are two parts
        if (elements.length == 2 && objectType == CUSTOM && eventMap.hasCustomId(elements[1])) {
            builder.withObjectId(elements[1]);
            builder.withUpdateType(eventMap.getCustomUpdateType((elements[1])));
        }
        // This covers system events with two qualifiers (an objectId and an answerValue)
        if (elements.length == 3) {
            if (objectType == STUDY_BURST) {
                if (eventMap.hasBurstId(elements[1])) {
                    builder.withObjectId(elements[1]);
                    builder.withUpdateType(eventMap.getBurstUpdateType((elements[1])));
                    builder.withAnswerValue(elements[2]); // the iteration #
                }
            } else {
                if (elements[2].contains("=")) { // question with an answer
                    String[] answer = elements[2].split("=");
                    builder.withObjectId(elements[1]);
                    builder.withEventType(parseEventType(answer[0]));
                    builder.withAnswerValue(answer[1]);
                } else { // survey, activity, assessment finished
                    builder.withObjectId(elements[1]);
                    builder.withEventType(parseEventType(elements[2]));
                }
            }
        }
        return builder;
    }
    
    private ActivityEventObjectType parseObjectType(String value) {
        try {
            return ActivityEventObjectType.valueOf(value.toUpperCase());
        } catch(IllegalArgumentException e) {
        }
        return null;
    }
    
    private ActivityEventType parseEventType(String value) {
        try {
            return ActivityEventType.valueOf(value.toUpperCase());
        } catch(IllegalArgumentException e) {
        }
        return null;
    }
}
