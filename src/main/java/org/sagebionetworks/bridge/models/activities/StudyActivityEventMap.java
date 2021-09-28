package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_BURST;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class StudyActivityEventMap {

    Map<ActivityEventObjectType, Map<String, ActivityEventUpdateType>> map;
    
    public StudyActivityEventMap() {
        map = new HashMap<>();
        map.put(CUSTOM, new HashMap<>());
        map.put(STUDY_BURST, new HashMap<>());
    }
    
    public void addCustomEvents(List<StudyCustomEvent> customEvents) {
        for (StudyCustomEvent event : customEvents) {
            map.get(CUSTOM).put(event.getEventId(), event.getUpdateType());
        }
    }
    
    public void addStudyBursts(List<StudyBurst> studyBursts) {
        for (StudyBurst burst : studyBursts) {
            map.get(STUDY_BURST).put(burst.getIdentifier(), burst.getUpdateType());
        }
    }
    
    public boolean hasCustomId(String key) {
        return map.get(CUSTOM).containsKey(key);
    }

    public ActivityEventUpdateType getCustomUpdateType(String key) {
        return map.get(CUSTOM).get(key);
    }

    public boolean hasBurstId(String key) {
        return map.get(STUDY_BURST).containsKey(key);
    }

    public ActivityEventUpdateType getBurstUpdateType(String key) {
        return map.get(STUDY_BURST).get(key);
    }
}
