package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

import java.util.EnumSet;

public enum ActivityEventObjectType {
    /**
     * Event for the first time the user successfully requests scheduled activities from the 
     * server. This event is not recorded until the activities can be successfully returned to 
     * the user, so if consent is required, it will have to be provided first. This event does 
     * not update after creation.
     */
    ACTIVITIES_RETRIEVED(IMMUTABLE),
    /**
     * An enrollment event. The eventId will be "enrollment". This event does not update after 
     * creation.
     */
    ENROLLMENT(IMMUTABLE),
    /**
     * Event for when a question has been answered. An event record is created for each answer 
     * submitted to the survey response API (NOTE: No application as of 2/9/2016 uses this 
     * API for historical reasons, so we can't schedule based on the answers to survey questions).
     * Event IDs take the format "question:<guid>:answered=<answer_values>".
     */
    QUESTION(FUTURE_ONLY),
    /**
     * Event for when a survey has been finished. This event is recorded when we have received an 
     * answer (or a declined-to-answer) for every question in a survey through the survey response 
     * API. (NOTE: No application as of 2/9/2016 uses this API for historical reasons, so we can't 
     * schedule based on users finishing a survey). Event IDs take the format 
     * "survey:<guid>:finished".
     */
    SURVEY(FUTURE_ONLY),
    /**
     * Event for when any activity has been finished. An event is published every time the client 
     * updates a scheduled activity record with a finishedOn timestamp. Clients that use the scheduled 
     * events API do send these updates and we can schedule against the completion of a survey or task. 
     * Event IDs take the format "activity:<guid>:finished" (The guid is the guid of the activity as 
     * saved in a schedule plan).
     */
    ACTIVITY(FUTURE_ONLY),
    /**
     * A custom event configured at the app level with an update type (mutable, immutable, or future-
     * only). If it cannot be found, then the event will default to the most restrictive (immutable).
     */
    CUSTOM(IMMUTABLE),
    /**
     * Event records the date the account was created on. This event does not update after creation.
     */
    CREATED_ON(IMMUTABLE),
    /**
     * A study start date event determined by the date the activities_retrieved or enrollment event are
     * received. If neither event exists then this event records the date the account was created on. 
     * Event is not persisted.
     */
    STUDY_START_DATE(IMMUTABLE);
    
    public static final EnumSet<ActivityEventObjectType> UNARY_EVENTS = EnumSet.of(ENROLLMENT, ACTIVITIES_RETRIEVED);
    
    private final ActivityEventUpdateType updateType;
    
    private ActivityEventObjectType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
}
