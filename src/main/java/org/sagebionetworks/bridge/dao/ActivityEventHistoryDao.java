package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.activities.ActivityEvent;

/**
 * A reimplementation of the activity event system with history, and scoped to 
 * specific studies. It is an error to submit an ActivityEvent without a studyId. 
 */
public interface ActivityEventHistoryDao {
    /**
     * Delete all timestamps for a custom event.
     */
    boolean deleteCustomEvent(ActivityEvent event);

    /**
     * Publish an event into this user’s event stream for a specific study. This event 
     * becomes available for scheduling activities for this user. Returns true if the 
     * event is recorded.
     */
    boolean publishEvent(ActivityEvent event);
    
    /**
     * Get a map of events, where the string key is an event identifier, and the value 
     * is a list of the timestamps for this event, from the most recent to the oldest 
     * timestamp (so the active timestamp is always at index 0 in the list). 
     */
    Map<String, List<DateTime>> getActivityEventMap(String healthCode, String studyId);
    
    /**
     * Delete all activity events for this user across all studies. This should only 
     * be called when physically deleting test users.
     */
    void deleteActivityEvents(String healthCode, String studyId);
}
