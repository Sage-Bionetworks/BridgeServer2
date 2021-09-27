package org.sagebionetworks.bridge.models.activities;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_BURST;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;

/**
 * Accumulate information from the client, from the code path, and then put it together
 * to form a valid StudyActivityEvent with the correct event ID and update type.
 */
public class StudyActivityEventRequest {

    private String eventKey;
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    
    public StudyActivityEventRequest() { 
    }
    
    /**
     * These are the only properties an API user can submit for custom events.
     * The rest are set on the server.
     */
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
     * builder. If the string is not valid, the builder is not valid, the study activity
     * event it generates is not valid, and validation fails.
     */
    public StudyActivityEventParams parseRequest(StudyActivityEventMap eventMap) {
        StudyActivityEventParams params = new StudyActivityEventParams();
        params.withTimestamp(timestamp);
        params.withClientTimeZone(clientTimeZone);

        if (isBlank(eventKey)) {
            return params;
        }
        String[] elements = eventKey.split(":");

        ActivityEventObjectType objectType = parseObjectType(elements[0]);
        // This covers system events which have no qualifiers
        if (objectType != null) {
            params.withObjectType(objectType);
            params.withUpdateType(objectType.getUpdateType());
        }
        // However, custom events were originally submitted without a prefix, so check that
        else if (elements.length == 1 && eventMap.containsCustomId(elements[0])) {
            params.withObjectType(CUSTOM);
            params.withObjectId(elements[0]);
            params.withUpdateType(eventMap.getCustomUpdateType((elements[0])));
        }
        // Fully specified custom events are two parts
        if (elements.length == 2 && objectType == CUSTOM && eventMap.containsCustomId(elements[1])) {
            params.withObjectId(elements[1]);
            params.withUpdateType(eventMap.getCustomUpdateType((elements[1])));
        }
        // This covers system events with two qualifiers (an objectId and an answerValue)
        if (elements.length == 3) {
            if (objectType == STUDY_BURST) {
                if (eventMap.containsBurstId(elements[1])) {
                    params.withObjectId(elements[1]);
                    params.withUpdateType(eventMap.getBurstUpdateType((elements[1])));
                    params.withAnswerValue(elements[2]); // the iteration #
                }
            } else {
                if (elements[2].contains("=")) { // question with an answer
                    String[] answer = elements[2].split("=");
                    params.withObjectId(elements[1]);
                    params.withEventType(parseEventType(answer[0]));
                    params.withAnswerValue(answer[1]);
                } else { // survey, activity, assessment finished
                    params.withObjectId(elements[1]);
                    params.withEventType(parseEventType(elements[2]));
                }
            }
        }
        return params;
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
