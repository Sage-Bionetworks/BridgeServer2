package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DateTimeRangeResourceList<T> extends ResourceList<T> {
    
    public DateTimeRangeResourceList(List<T> items, boolean suppressDeprecated) {
        super(items, suppressDeprecated);
    }

    @JsonCreator
    public DateTimeRangeResourceList(@JsonProperty(ITEMS) List<T> items) {
        super(items, false);
    }
    
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return (suppressDeprecated) ? null : getDateTime(START_TIME);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return (suppressDeprecated) ? null : getDateTime(END_TIME);
    }
    public DateTimeRangeResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
