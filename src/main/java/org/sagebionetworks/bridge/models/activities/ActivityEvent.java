package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.models.BridgeEntity;

@JsonDeserialize(as = DynamoActivityEvent.class)
public interface ActivityEvent extends HasTimestamp, BridgeEntity {
    
    @JsonIgnore
    String getHealthCode();

    String getEventId();

    String getAnswerValue();

    DateTime getTimestamp();
    
    @JsonIgnore
    ActivityEventUpdateType getUpdateType();
}
