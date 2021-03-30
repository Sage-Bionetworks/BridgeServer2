package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public interface TimelineDao {
    
    DateTime getModifedOn(String scheduleGuid);
    
    /**
     * Persist the lookup records to find the scheduling context data from 
     * any instance guid in a timeline.
     */
    void persistMetadata(List<TimelineMetadata> metadata);
    
    /**
     * Retrieve scheduling metadata for an instance GUID.
     */
    TimelineMetadata getMetadata(String instanceGuid);
}
