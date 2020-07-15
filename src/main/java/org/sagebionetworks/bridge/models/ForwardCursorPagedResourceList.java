package org.sagebionetworks.bridge.models;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A page of items from a longer list of items, as calculated based on the supplied 
 * request parameters, with a <code>nextPageOffsetKey</code> to retrieve the next page 
 * of records. This kind of list cannot calculate the total number of records across 
 * all pages, or provide the information to page backwards in the list.
 */
public class ForwardCursorPagedResourceList<T> extends ResourceList<T> {

    private static final String HAS_NEXT = "hasNext";
    
    private final @Nullable String nextPageOffsetKey;

    @JsonCreator
    public ForwardCursorPagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(NEXT_PAGE_OFFSET_KEY) String nextPageOffsetKey) {
        super(items);
        this.nextPageOffsetKey = nextPageOffsetKey;
    }
    
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return v2 ? null : getDateTime(START_TIME);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return v2 ? null : getDateTime(END_TIME);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getScheduledOnStart() {
        return v2 ? null : getDateTime(SCHEDULED_ON_START);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getScheduledOnEnd() {
        return v2 ? null : getDateTime(SCHEDULED_ON_END);
    }
    @Deprecated
    public String getOffsetKey() {
        return v2 ? null : nextPageOffsetKey;
    }
    @Deprecated
    public Integer getPageSize() {
        return v2 ? null : (Integer)getRequestParams().get(PAGE_SIZE);
    }
    public String getNextPageOffsetKey() {
        return nextPageOffsetKey;
    }
    @JsonProperty(HAS_NEXT)
    public boolean hasNext() {
        return (nextPageOffsetKey != null);
    }
    public ForwardCursorPagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }

    @Override
    public String toString() {
        return "ForwardCursorPagedResourceList [nextPageOffsetKey=" + nextPageOffsetKey + ", getNextPageOffsetKey()="
                + getNextPageOffsetKey() + ", getItems()=" + getItems() + ", getRequestParams()=" + getRequestParams()
                + "]";
    }
    
}
