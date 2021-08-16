package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

@Embeddable
@BridgeTypeName("CustomEvent")
public class StudyCustomEvent {
    private String eventId;
    @Enumerated(EnumType.STRING)
    private ActivityEventUpdateType updateType;
    
    // Hibernate needs this
    public StudyCustomEvent() {}
    
    public StudyCustomEvent(String eventId, ActivityEventUpdateType updateType) {
        this.eventId = eventId;
        this.updateType = updateType;
        
    }
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    public void setUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
}