package org.sagebionetworks.bridge.models.activities;

/**
 * The type of update that is allowed for an activity event. 
 */
public enum ActivityEventUpdateType {
    /**
     * The event timestamp can be updated to any other value, or the event can 
     * even be deleted.
     */
    MUTABLE,
    /**
     * Once written, the event timestamp cannot be changed again.
     */
    IMMUTABLE,
    /**
     * The event timestamp can only be updated if the update is after the current 
     * timestamp (or the timestamp does not yet exist).
     */
    FUTURE_ONLY;
    
    public boolean canDelete(ActivityEvent persistedEvent, ActivityEvent newEvent) {
        return (persistedEvent != null && this == MUTABLE);
    }
    
    public boolean canUpdate(ActivityEvent persistedEvent, ActivityEvent newEvent) {
        return persistedEvent == null || this == MUTABLE ||
            this == FUTURE_ONLY && newEvent.getTimestamp() > persistedEvent.getTimestamp();
    }
}
