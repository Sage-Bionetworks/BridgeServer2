package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.INSTANCE;

import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class StudyActivityEventService {
    
    private StudyActivityEventDao dao;
    private ActivityEventService activityEventService;
    private AppService appService;
    private AccountService accountService;
    
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setStudyActivityEventDao(StudyActivityEventDao dao) {
        this.dao = dao;
    }
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    DateTime getCreatedOn() { 
        return DateTime.now();
    }

    public void deleteCustomEvent(StudyActivityEventRequest request) {
        checkNotNull(request);
        
        dao.deleteCustomEvent(request.toStudyActivityEvent());
    }
    
    public void publishEvent(StudyActivityEventRequest request) {
        checkNotNull(request);
        
        App app = appService.getApp(request.getAppId());
        
        request.createdOn(getCreatedOn());
        if (request.getObjectType() == CUSTOM) {
            ActivityEventUpdateType updateType = app.getCustomEvents().get(request.getObjectId());
            if (updateType != null) {
                request.updateType(updateType);
            }
        }
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        // Prepend "custom:" if it is a valid custom event. If it's not valid at all, 
        // it'll be set to null and fail validation.
        event.setEventId(formatActivityEventId(app.getCustomEvents().keySet(), event.getEventId()));
        
        Validate.entityThrowingException(INSTANCE, event);
        
        Map<String, StudyActivityEvent> eventMap = dao.getRecentStudyActivityEventMap(request.getUserId(), request.getStudyId());
        StudyActivityEvent mostRecent = eventMap.get(event.getEventId());
        
        if (request.getUpdateType().canUpdate(mostRecent, event)) {
            dao.publishEvent(event);
            createAutomaticCustomEvents(app, request);
            // In this specific case, we do publish a global event as well for
            // backwards compatibility. The first study to generate an enrollment
            // event sets it globally.
            if (request.getObjectType() == ENROLLMENT) {
                String healthCode = accountService.getHealthCodeForAccount(
                        AccountId.forId(event.getAppId(), event.getUserId()));
                activityEventService.publishEnrollmentEvent(app, 
                        null, healthCode, request.getTimestamp());
            }
        }
    }
    
    public ResourceList<StudyActivityEvent> getRecentStudyActivityEvents(String studyId, String userId) {
        checkNotNull(userId);
        checkNotNull(studyId);
        
        return new ResourceList<>(dao.getRecentStudyActivityEvents(userId, studyId), true)
                .withRequestParam(ResourceList.STUDY_ID, studyId);
    }
    
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(String userId, 
            String studyId, String eventId, int offsetBy, int pageSize) {
        checkNotNull(userId);
        checkNotNull(studyId);
        checkNotNull(eventId);
        return dao.getStudyActivityEventHistory(userId, studyId, eventId, offsetBy, pageSize);
    }
    
    /**
     * If the triggering event is mutable, it will succeed and these events must update as well, so they are 
     * always mutable when this function is called. 
     */
    private void createAutomaticCustomEvents(App app, StudyActivityEventRequest request) {
        for (Map.Entry<String, String> oneAutomaticEvent : app.getAutomaticCustomEvents().entrySet()) {
            String automaticEventKey = oneAutomaticEvent.getKey(); // new event key
            Tuple<String> autoEventSpec = BridgeUtils.parseAutoEventValue(oneAutomaticEvent.getValue()); // originEventId:Period
            // enrollment, activities_retrieved, or any of the custom:* events defined by the user.
            if (request.getObjectId().equals(autoEventSpec.getLeft())) {
                
                Period automaticEventDelay = Period.parse(autoEventSpec.getRight());
                DateTime automaticEventTime = new DateTime(request.getTimestamp()).plus(automaticEventDelay);
                
                StudyActivityEvent automaticEvent = request.copy()
                    .objectType(CUSTOM)
                    .updateType(MUTABLE) 
                    .objectId(automaticEventKey)
                    .timestamp(automaticEventTime).toStudyActivityEvent();
                dao.publishEvent(automaticEvent);
            }
        }        
    }
}