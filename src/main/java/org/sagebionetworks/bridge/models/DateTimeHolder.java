package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonDeserialize(builder = DateTimeHolder.Builder.class)
public class DateTimeHolder {
    
    private DateTime dateTime;
    
    @JsonCreator
    public DateTimeHolder(@JsonProperty("dateTime") DateTime dateTime) {
        this.dateTime = dateTime;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getDateTime() {
        return dateTime;
    }
    
    public static class Builder {
        private DateTime dateTime;
        
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withDateTime(DateTime dateTime) {
            this.dateTime = dateTime;
            return this;
        }
        
        public DateTimeHolder build() {
            return new DateTimeHolder(dateTime);
        }
    }
}