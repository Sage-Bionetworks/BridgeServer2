package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public interface StudyActivityEventDao {
    /**
     * Remove all timestamp records for a specific custom event. A timestamp value 
     * need not be supplied as part of this event (if it is supplied, it is ignored).
     */
    void deleteCustomEvent(StudyActivityEvent event);

    /**
     * Publish an event into this user’s event stream. This event becomes available 
     * for scheduling activities for this user.
     */
    void publishEvent(StudyActivityEvent event);
    
    /**
     * Return the most recently persisted study event record (the record with the most 
     * recent `createdOn` timestamp, not necessarily the recordwith the most recent 
     * `eventTimestamp` field). Returns null if this event has not been persisted for 
     * this user in this study. 
     */
    public StudyActivityEvent getRecentStudyActivityEvent(String userId, String studyId, String eventId);
    
    /**
     * Get a non-paginated list of all events recorded for this participant in this 
     * study. Each eventId is guaranteed to be in the list one time, with the latest 
     * `createdOn` timestamp recorded for that event. If the event has yet to be recorded
     * for this participant, there will be no entry for that event ID in the ResourceList.
     * Custom events are prefixed with "custom:".
     */
    public List<StudyActivityEvent> getRecentStudyActivityEvents(String userId, String studyId);
    
    /**
     * Get all timestamps (in a paginated API) for a specific event ID. Note that 
     * for immutable events there should only ever be one timestamp. Returns an 
     * empty PagedResourceList if the event has not yet been recorded for this 
     * participant. “Synthetic” or calculated events (study_start_date and 
     * created_on) are returned by this method.
     */
    PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(
            String userId, String studyId, String eventId, Integer offsetBy, Integer pageSize);
}
