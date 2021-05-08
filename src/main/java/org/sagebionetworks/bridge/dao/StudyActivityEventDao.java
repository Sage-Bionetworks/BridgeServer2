package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public interface StudyActivityEventDao {
    /**
     * Remove a custom event (only).
     */
    boolean deleteCustomEvent(StudyActivityEvent event);

    /**
     * Publish an event into this user's event stream. This event becomes available 
     * for scheduling activities for this user. Returns true if the event is recorded.
     */
    void publishEvent(StudyActivityEvent event);
    
    public Map<String, StudyActivityEvent> getRecentStudyActivityEventMap(String userId, String studyId);
    
    public List<StudyActivityEvent> getRecentStudyActivityEvents(String userId, String studyId);
    
    PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(
            String userId, String studyId, String eventId, int offsetBy, int pageSize);
}
