package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_JOINER;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CREATED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_START_DATE;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

/**
 * Scheduling is calculated relative to each user’s participation in a study, 
 * which is recorded through activity events.
 * 
 * Prior to multi-study apps, these events were global to an app. With the introduction
 * of multiple studies, these events are now specific to a study. Custom event 
 * definition is currently still at the app-level and not under the control of study 
 * designers.
 * 
 * For backwards compatibility, study-scoped `enrollment`, `activities_retrieved`, 
 * and `created_on` events will also create global equivalents (global events do not 
 * create study-specific variants). Since these are immutable, the first study to set 
 * a value will define the value for later studies as well. However, the global events 
 * should be retired in favor of the events retrieved through study-specific APIs (the
 * v2 scheduler will only use study-scoped events).
 */
@Component
public class ActivityEventService {

    private ActivityEventDao activityEventDao;
    private ParticipantService participantService;
    private AppService appService;
    
    @Autowired
    final void setActivityEventDao(ActivityEventDao activityEventDao) {
        this.activityEventDao = activityEventDao;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    /**
     * Publishes a custom event. Note that this automatically prepends "custom:" to the event key to form the event ID
     * (eg, event key "studyBurstStart" becomes event ID "custom:studyBurstStart"). Also note that the event key must
     * defined in the app (either in activityEventKeys or in AutomaticCustomEvents).
     */
    public void publishCustomEvent(App app, String studyId, String healthCode, String eventKey, DateTime timestamp) {
        checkNotNull(healthCode);
        checkNotNull(eventKey);

        if (!app.getActivityEventKeys().contains(eventKey)
                && !app.getAutomaticCustomEvents().containsKey(eventKey)) {
            throw new BadRequestException("App's ActivityEventKeys does not contain eventKey: " + eventKey);
        }

        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode(healthCode)
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .withObjectId(eventKey)
                .withStudyId(studyId)
                .withTimestamp(timestamp).build();
        
        if (activityEventDao.publishEvent(event)) {
            // Create automatic events, as defined in the app
            createAutomaticCustomEvents(app, studyId, healthCode, event);
        }
    }

    /**
     * Publishes the enrollment event for a user, as well as all of the automatic custom events that trigger on
     * enrollment time.
     */
    public void publishEnrollmentEvent(App app, String studyId, String healthCode, ConsentSignature signature) {
        checkNotNull(signature);

        // Create enrollment event. Use UTC for the timezone. DateTimes are used for period calculations, but since we
        // store everything as epoch milliseconds, the timezone should have very little affect.
        DateTime enrollment = new DateTime(signature.getSignedOn(), DateTimeZone.UTC);
        ActivityEvent globalEvent = new DynamoActivityEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(enrollment)
            .withObjectType(ENROLLMENT).build();
        
        if (activityEventDao.publishEvent(globalEvent)) {
            // Create automatic events, as defined in the app
            createAutomaticCustomEvents(app, null, healthCode, globalEvent);
        }
        if (studyId != null) {
            ActivityEvent studyEvent = new DynamoActivityEvent.Builder()
                    .withHealthCode(healthCode)
                    .withTimestamp(enrollment)
                    .withObjectType(ENROLLMENT)
                    .withStudyId(studyId).build();
            if (activityEventDao.publishEvent(studyEvent)) {
                createAutomaticCustomEvents(app, studyId, healthCode, studyEvent);
            }
        }
    }
    
    public void publishActivitiesRetrieved(App app, String studyId, String healthCode, DateTime timestamp) {
        checkNotNull(healthCode);
        checkNotNull(timestamp);
        
        ActivityEvent globalEvent = new DynamoActivityEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(timestamp)
            .withObjectType(ACTIVITIES_RETRIEVED).build();
        
        if (activityEventDao.publishEvent(globalEvent)) {
            // Create automatic events, as defined in the app
            createAutomaticCustomEvents(app, null, healthCode, globalEvent);
        }
        if (studyId != null) {
            ActivityEvent studyEvent = new DynamoActivityEvent.Builder()
                    .withHealthCode(healthCode)
                    .withTimestamp(timestamp)
                    .withObjectType(ACTIVITIES_RETRIEVED)
                    .withStudyId(studyId).build();
            if (activityEventDao.publishEvent(studyEvent)) {
                // Create automatic events, as defined in the app
                createAutomaticCustomEvents(app, studyId, healthCode, studyEvent);
            }
        }
    }
    
    /**
     * This method does not currently accept a studyId because we don’t anticipate a study-scoped
     * API for this event (it has not been used).
     */
    public void publishQuestionAnsweredEvent(String healthCode, SurveyAnswer answer) {
        checkNotNull(healthCode);
        checkNotNull(answer);
        
        ActivityEvent event = new DynamoActivityEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(answer.getAnsweredOn())
            .withObjectType(ActivityEventObjectType.QUESTION)
            .withObjectId(answer.getQuestionGuid())
            .withEventType(ActivityEventType.ANSWERED)
            .withAnswerValue(COMMA_JOINER.join(answer.getAnswers())).build();
        activityEventDao.publishEvent(event);
    }
    
    /**
     * This method does not currently accept a studyId because we don’t anticipate a study-scoped
     * API for this event (activities are probably going to be replaced with assessments).
     */
    public void publishActivityFinishedEvent(ScheduledActivity schActivity) {
        checkNotNull(schActivity);
        
        // If there's no colon, this is an existing activity and it cannot fire an 
        // activity event. Quietly ignore this until we have migrated activities.
        if (schActivity.getGuid().contains(":")) {
            String activityGuid = schActivity.getGuid().split(":")[0];
            
            ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode(schActivity.getHealthCode())
                .withObjectType(ActivityEventObjectType.ACTIVITY)
                .withObjectId(activityGuid)
                .withEventType(ActivityEventType.FINISHED)
                .withTimestamp(schActivity.getFinishedOn())
                .build();

            activityEventDao.publishEvent(event);
        }
    }
    
    /**
     * Publishes a created_on event for a user that essentially copies over the createdOn 
     * timestamp of an Account.
     */
    public void publishCreatedOnEvent(String studyId, String healthCode, DateTime createdOn) {
        checkNotNull(healthCode);
        
        ActivityEvent globalEvent = new DynamoActivityEvent.Builder()
                .withHealthCode(healthCode)
                .withTimestamp(createdOn)
                .withObjectType(CREATED_ON).build();
        activityEventDao.publishEvent(globalEvent);

        if (studyId != null) {
            ActivityEvent studyEvent = new DynamoActivityEvent.Builder()
                    .withHealthCode(healthCode)
                    .withTimestamp(createdOn)
                    .withObjectType(CREATED_ON)
                    .withStudyId(studyId).build();
            activityEventDao.publishEvent(studyEvent);
        }
    }
    
    /**
    * Gets the activity events times for a specific user in order to schedule against them.
    */
    public Map<String, DateTime> getActivityEventMap(String appId, String studyId, String healthCode) {
        checkNotNull(healthCode);
        Map<String, DateTime> activityMap = activityEventDao.getActivityEventMap(healthCode, studyId);
        
        Builder<String, DateTime> builder = ImmutableMap.<String, DateTime>builder();
        
        DateTime activitiesRetrieved = activityMap.get(ACTIVITIES_RETRIEVED.name().toLowerCase());
        DateTime enrollment = activityMap.get(ENROLLMENT.name().toLowerCase());
        DateTime createdOn = activityMap.get(CREATED_ON.name().toLowerCase());
        if (createdOn == null) {
            App app = appService.getApp(appId);
            StudyParticipant studyParticipant = participantService.getParticipant(app, "healthcode:"+healthCode, false);
            createdOn = studyParticipant.getCreatedOn();
            publishCreatedOnEvent(studyId, healthCode, createdOn);
            builder.put(CREATED_ON.name().toLowerCase(), createdOn);
        }
        DateTime studyStartDate = createdOn;
        if (activitiesRetrieved != null) {
            studyStartDate = activitiesRetrieved;
        } else if (enrollment != null) {
            studyStartDate = enrollment;
        }
        builder.put(STUDY_START_DATE.name().toLowerCase(), studyStartDate);
        builder.putAll(activityMap);
        
        return builder.build();
    }
    
    public List<ActivityEvent> getActivityEventList(String appId, String studyId, String healthCode) {
        Map<String, DateTime> activityEvents = getActivityEventMap(appId, studyId, healthCode);

        List<ActivityEvent> activityEventList = Lists.newArrayList();
        for (Map.Entry<String, DateTime> entry : activityEvents.entrySet()) {
            DynamoActivityEvent event = new DynamoActivityEvent();
            event.setEventId(entry.getKey());

            DateTime timestamp = entry.getValue();
            if (timestamp !=null) {
                event.setTimestamp(timestamp.getMillis());
            }

            activityEventList.add(event);
        }
        return activityEventList;
    }
    
    public void deleteActivityEvents(String studyId, String healthCode) {
        checkNotNull(healthCode);
        activityEventDao.deleteActivityEvents(healthCode, studyId);
    }

    private void createAutomaticCustomEvents(App app, String studyId, String healthCode, ActivityEvent event) {
        for (Map.Entry<String, String> oneAutomaticEvent : app.getAutomaticCustomEvents().entrySet()) {
            String automaticEventKey = oneAutomaticEvent.getKey(); // new event key
            Tuple<String> autoEventSpec = BridgeUtils.parseAutoEventValue(oneAutomaticEvent.getValue()); // originEventId:Period
            // enrollment, activities_retrieved, or any of the custom:* events defined by the user.
            if (event.getEventId().equals(autoEventSpec.getLeft()) || 
                event.getEventId().startsWith("custom:"+autoEventSpec.getLeft())) {
                
                Period automaticEventDelay = Period.parse(autoEventSpec.getRight());
                DateTime automaticEventTime = new DateTime(event.getTimestamp()).plus(automaticEventDelay);
                ActivityEvent automaticEvent = new DynamoActivityEvent.Builder()
                        .withHealthCode(healthCode)
                        .withObjectType(ActivityEventObjectType.CUSTOM)
                        .withObjectId(automaticEventKey)
                        .withTimestamp(automaticEventTime)
                        .withStudyId(studyId).build();
                activityEventDao.publishEvent(automaticEvent);
            }
        }        
    }
}
