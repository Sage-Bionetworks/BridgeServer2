package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;

public class CreatedOnHolder {

    private final DateTime createdOn;
    
    @JsonCreator
    public CreatedOnHolder(@JsonProperty("createdOn") DateTime createdOn) {
        this.createdOn = createdOn;
    }
    
    public DateTime getCreatedOn() {
        return createdOn;
    }
    
}
