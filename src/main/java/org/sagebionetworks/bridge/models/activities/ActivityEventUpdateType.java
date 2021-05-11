package org.sagebionetworks.bridge.models.activities;

/**
 * The type of update that is allowed for an activity event. 
 */
public enum ActivityEventUpdateType {
    /**
     * The event timestamp can be updated to any other value, and the event can 
     * be deleted.
     */
    MUTABLE {
        @Override
        public boolean canDelete(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return (persistedEvent != null);
        }
        @Override
        public boolean canUpdate(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return persistedEvent == null || !persistedEvent.getTimestamp().isEqual(newEvent.getTimestamp());
        }
    },
    /**
     * Once written, the event timestamp cannot be changed again.
     */
    IMMUTABLE {
        @Override
        public boolean canDelete(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return false;
        }
        @Override
        public boolean canUpdate(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return (persistedEvent == null);
        }
    },
    /**
     * The event timestamp can only be updated if the update is after the current 
     * timestamp (or the timestamp does not yet exist). The event cannot be deleted
     * after it is created.
     */
    FUTURE_ONLY {
        @Override
        public boolean canDelete(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return false;
        }
        @Override
        public boolean canUpdate(HasTimestamp persistedEvent, HasTimestamp newEvent) {
            return (persistedEvent == null || newEvent.getTimestamp().isAfter(persistedEvent.getTimestamp()));
        }
    };
    
    public abstract boolean canDelete(HasTimestamp persistedEvent, HasTimestamp newEvent);
    
    public abstract boolean canUpdate(HasTimestamp persistedEvent, HasTimestamp newEvent);
}
