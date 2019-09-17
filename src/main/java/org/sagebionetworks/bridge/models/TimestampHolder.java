package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TimestampHolder {

    private final Long timestamp;
    
    @JsonCreator
    public TimestampHolder(@JsonProperty("timestamp") Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
}