package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateTimeHolder {

    private final DateTime dateTime;
    
    @JsonCreator
    public DateTimeHolder(@JsonProperty("dateTime") DateTime dateTime) {
        this.dateTime = dateTime;
    }
    
    public DateTime getDateTime() {
        return dateTime;
    }
    
}