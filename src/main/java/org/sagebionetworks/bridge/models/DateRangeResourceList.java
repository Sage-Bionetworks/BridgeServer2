package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateRangeResourceList<T> extends ResourceList<T> {
    
    public DateRangeResourceList(List<T> items, boolean suppressDeprecated) {
        super(items, suppressDeprecated);
    }
    
    @JsonCreator
    public DateRangeResourceList(@JsonProperty(ITEMS) List<T> items) {
        this(items, false);
    }
    
    @Deprecated
    public LocalDate getStartDate() {
        return (suppressDeprecated) ? null : getLocalDate(START_DATE);
    }
    @Deprecated
    public LocalDate getEndDate() {
        return (suppressDeprecated) ? null : getLocalDate(END_DATE);
    }
    @Override
    @Deprecated
    public Integer getTotal() {
        if (suppressDeprecated) {
            return null;
        }
        // This is necessary solely to keep current integration tests passing. 
        // The total property is going away on everything except PagedResourceList.
        Integer total = super.getTotal();
        return (total == null) ? Integer.valueOf(0) : total;
    }
    public DateRangeResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
    
}
