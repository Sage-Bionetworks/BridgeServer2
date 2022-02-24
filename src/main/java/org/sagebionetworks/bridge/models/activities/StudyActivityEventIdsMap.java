package org.sagebionetworks.bridge.models.activities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

/**
 * Some events include a user-defined identifier (custom events, study burst events). This class
 * provides the custom values so we can validate an event string is using a known identifier.  
 */
public class StudyActivityEventIdsMap {

    Map<String, ActivityEventUpdateType> customEvents;
    Map<String, ActivityEventUpdateType> studyBursts;
    
    public StudyActivityEventIdsMap() {
        customEvents = new HashMap<>();
        studyBursts = new HashMap<>();
    }
    
    public void addCustomEvents(List<StudyCustomEvent> events) {
        if (events != null) {
            for (StudyCustomEvent event : events) {
                if (event != null && event.getEventId() != null && event.getUpdateType() != null) {
                    customEvents.put(event.getEventId(), event.getUpdateType());    
                }
            }
        }
    }
    
    public void addStudyBursts(List<StudyBurst> bursts) {
        if (bursts != null) {
            for (StudyBurst burst : bursts) {
                if (burst != null && burst.getIdentifier() != null && burst.getUpdateType() != null) {
                    studyBursts.put(burst.getIdentifier(), burst.getUpdateType());    
                }
            }
        }
    }
    
    public boolean hasCustomId(String key) {
        return customEvents.containsKey(key);
    }

    public ActivityEventUpdateType getCustomUpdateType(String key) {
        return customEvents.get(key);
    }

    public boolean hasBurstId(String key) {
        return studyBursts.containsKey(key);
    }

    public ActivityEventUpdateType getBurstUpdateType(String key) {
        return studyBursts.get(key);
    }
}
