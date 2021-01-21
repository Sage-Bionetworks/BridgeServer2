package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;

public interface ActivityEventDao {

    /**
     * Publish an event into this user's event stream. This event becomes available 
     * for scheduling activities for this user. Returns true if the event is recorded.
     */
    boolean publishEvent(ActivityEvent event);
    
    /**
     * Get a map of events, where the string key is an event identifier, and the value 
     * is the timestamp of the event. If studyId is null, only events that are not scoped
     * to a study are returned; if a studyId is provided, only events that are scoped to
     * that study are provided. 
     * 
     * @see org.sagebionetworks.bridge.models.activities.ActivityEventObjectType
     */
    Map<String, DateTime> getActivityEventMap(String healthCode, String studyId);
    
    /**
     * Delete all activity events for this user (if no studyId is provided, delete all the 
     * app-scoped events, if there is a studyId, delete all the study-scoped events; this 
     * method will need to be called more than once during test cleanup). This should only 
     * be called when physically deleting test users; users in production take too many 
     * server resources to completely delete this way.
     */
    void deleteActivityEvents(String healthCode, String studyId);
}
