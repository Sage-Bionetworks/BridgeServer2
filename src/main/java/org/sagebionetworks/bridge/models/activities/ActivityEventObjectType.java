package org.sagebionetworks.bridge.models.activities;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

public enum ActivityEventObjectType {
    /**
     * Event for the first time the user successfully requests scheduled activities from the 
     * server. This event is not recorded until the activities can be successfully returned to 
     * the user, so if consent is required, it will have to be provided first. This event does 
     * not update after creation.
     */
    ACTIVITIES_RETRIEVED(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    /**
     * An enrollment event. The eventId will be "enrollment". This event does not update after 
     * creation.
     */
    ENROLLMENT(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    /**
     * Event for when a question has been answered. An event record is created for each answer 
     * submitted to the survey response API (NOTE: No application as of 2/9/2016 uses this 
     * API for historical reasons, so we can't schedule based on the answers to survey questions).
     * Event IDs take the format "question:<guid>:answered=<answer_values>".
     */
    QUESTION(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if(isBlank(objectId) || eventType == null || isBlank(answerValue)) {
                return null;
            }
            return String.format("%s:%s:%s=%s", this.name().toLowerCase(),
                    objectId, eventType.name().toLowerCase(), answerValue);
        }
    },
    
    /**
     * Event for when a survey has been finished. This event is recorded when we have received an 
     * answer (or a declined-to-answer) for every question in a survey through the survey response 
     * API. (NOTE: No application as of 2/9/2016 uses this API for historical reasons, so we can't 
     * schedule based on users finishing a survey). Event IDs take the format 
     * "survey:<guid>:finished".
     */
    SURVEY(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if(isBlank(objectId) || eventType == null) {
                return null;
            }
            return String.format("%s:%s:%s", this.name().toLowerCase(),
                    objectId, eventType.name().toLowerCase());
        }
    },
    /**
     * Event for when any activity has been finished. An event is published every time the client 
     * updates a scheduled activity record with a finishedOn timestamp. Clients that use the scheduled 
     * events API do send these updates and we can schedule against the completion of a survey or task. 
     * Event IDs take the format "activity:<guid>:finished" (the guid is the guid of the activity as 
     * saved in a schedule plan).
     */
    ACTIVITY(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if (isBlank(objectId) || eventType == null) {
                return null;
            }
            return String.format("%s:%s:%s", this.name().toLowerCase(),
                    objectId, eventType.name().toLowerCase());
        }
    },
    /**
     * Event for the first time the user successfully requests the timeline for a study. This event is 
     * not recorded until the activities can be successfully returned to the user, so if consent is 
     * required, it will have to be provided first. This event does not update after creation.
     */
    TIMELINE_RETRIEVED(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    /**
     * Event for when a session has been finished. An event is published every time the client updates
     * an adherence record for a session with a finishedOn timestamp. Event IDs take the format 
     * "session:<guid>:finished" (the guid is the guid of the session as saved in a v2 schedule). 
     */
    SESSION(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if (isBlank(objectId) || eventType == null) {
                return null;
            }
            return String.format("%s:%s:%s", this.name().toLowerCase(),
                    objectId, eventType.name().toLowerCase());
        }
    },
    /**
     * Event for when an assessment has been finished. An event is published every time the client updates
     * an adherence record for an assessment with a finishedOn timestamp. Event IDs take the format 
     * "assessment:<id>:finished" (the ID is the ID of the assessment). 
     */
    ASSESSMENT(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if (isBlank(objectId) || eventType == null) {
                return null;
            }
            return String.format("%s:%s:%s", this.name().toLowerCase(),
                    objectId, eventType.name().toLowerCase());
        }
    },
    /**
     * A custom event configured at the app level with an update type (mutable, immutable, or future-
     * only). If it cannot be found, then the event will default to the most restrictive (immutable).
     */
    CUSTOM(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if (isBlank(objectId)) {
                return null;
            }
            // "custom:" must be lower-case, adjust it if provided by the client
            if (objectId.toLowerCase().startsWith("custom:")) {
                return "custom:" + objectId.substring(7);
            }
            return "custom:" + objectId;
        }
    },
    /**
     * Event records the date the account was created on. This event does not update after creation.
     * It is present in both global and study-specific APIs, despite the fact that an account spans
     * all studies in an app.
     */
    CREATED_ON(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    /**
     * A study start date event determined for the global event by the activities_retrieved 
     * or enrollment event (whichever is more recent) or the account’s createdOn timestamp 
     * if neither exist. For study-specific events, this is determined by the timeline_retrieved 
     * or enrollment event (whichever is more recent) or the account’s createdOn timestamp if 
     * neither exist. Thus, although this event is recorded as “immutable,” it can change
     * with the recording of the events on which it is calculated. 
     */
    STUDY_START_DATE(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    
    /**
     * An event to record that the install link message has been sent to a participant. Note
     * that this records the message was sent, and does not guarantee that it was received.
     */
    INSTALL_LINK_SENT(FUTURE_ONLY) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            return this.name().toLowerCase();
        }
    },
    
    /**
     * A study burst custom event, generated automatically from study burst configuration. The objectId
     * should be the study burst ID, and the answerValue should be the iteration number, e.g. "03", to
     * generate an event ID like "study_burst:foo:03"
     */
    STUDY_BURST(IMMUTABLE) {
        public String getEventId(String objectId, ActivityEventType eventType, String answerValue) {
            if (isBlank(objectId)) {
                return null;
            }
            return STUDY_BURST_PREFIX + objectId + ":" + answerValue;
        }
    };
    
    ActivityEventObjectType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    
    public abstract String getEventId(String objectId, ActivityEventType eventType, String answerValue);
    
    private final ActivityEventUpdateType updateType;

    public static final String STUDY_BURST_PREFIX = "study_burst:";
    
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
}
