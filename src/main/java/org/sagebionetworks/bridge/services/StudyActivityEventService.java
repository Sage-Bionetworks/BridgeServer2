package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.findByEventType;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CREATED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_START_DATE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.DELETE_INSTANCE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class StudyActivityEventService {
    
    private static final String START_DATE_FIELD = STUDY_START_DATE.name().toLowerCase();
    private static final String CREATED_ON_FIELD = CREATED_ON.name().toLowerCase();

    private StudyActivityEventDao dao;
    private AppService appService;
    private AccountService accountService;
    
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

    /**
     * Only custom events can be deleted (if they are mutable). Other requests 
     * are silently ignored. 
     */
    public void deleteCustomEvent(StudyActivityEventRequest request) {
        checkNotNull(request);
        checkArgument(request.getObjectType() == CUSTOM);

        App app = appService.getApp(request.getAppId());
        request.objectId(formatActivityEventId(app.getCustomEvents().keySet(), request.getObjectId()));
        request.updateTypeForCustomEvents(app.getCustomEvents());
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        
        Validate.entityThrowingException(DELETE_INSTANCE, event);
        
        StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                event.getUserId(), event.getStudyId(), event.getEventId());

        if (event.getUpdateType().canDelete(mostRecent, event)) {
            dao.deleteCustomEvent(event);
        }
    }
    
    public void publishEvent(StudyActivityEventRequest request) {
        checkNotNull(request);

        App app = appService.getApp(request.getAppId());
        
        request.createdOn(getCreatedOn());
        request.updateTypeForCustomEvents(app.getCustomEvents());
        request.objectId(formatActivityEventId(app.getCustomEvents().keySet(), request.getObjectId()));
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        
        Validate.entityThrowingException(INSTANCE, event);
        
        StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                request.getUserId(), request.getStudyId(), event.getEventId());
        
        if (request.getUpdateType().canUpdate(mostRecent, event)) {
            dao.publishEvent(event);
            createAutomaticCustomEvents(app, request);
        }
    }
    
    public ResourceList<StudyActivityEvent> getRecentStudyActivityEvents(String appId, String userId, String studyId) {
        checkNotNull(userId);
        checkNotNull(studyId);

        Account account = accountService.getAccountNoFilter(AccountId.forId(appId, userId))
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        List<StudyActivityEvent> events = dao.getRecentStudyActivityEvents(userId, studyId);
        
        List<StudyActivityEvent> finalEvents = new ArrayList<>();
        finalEvents.addAll(events);
        
        makeCreatedOn(finalEvents, account.getCreatedOn());
        makeStudyStartDate(finalEvents, finalEvents, account.getCreatedOn());
        
        return new ResourceList<>(finalEvents); 
    }
    
    private void makeCreatedOn(List<StudyActivityEvent> outputEvents, DateTime createdOn) { 
        StudyActivityEvent createdOnEvent = makeSyntheticEvent(CREATED_ON_FIELD, createdOn);
        outputEvents.add(createdOnEvent);
    }
    
    private void makeStudyStartDate(List<StudyActivityEvent> inputEvents, 
            List<StudyActivityEvent> outputEvents, DateTime createdOn) {
        StudyActivityEvent start = makeSyntheticEvent(START_DATE_FIELD, createdOn);
        StudyActivityEvent timelineRetrieved = findByEventType(inputEvents, TIMELINE_RETRIEVED);
        if (timelineRetrieved != null) {
            start.setTimestamp(timelineRetrieved.getTimestamp());
        } else {
            StudyActivityEvent enrollment = findByEventType(inputEvents, ENROLLMENT);
            if (enrollment != null) {
                start.setTimestamp(enrollment.getTimestamp());
            }
        }
        outputEvents.add(start);
    }
    
    private StudyActivityEvent makeSyntheticEvent(String eventId, DateTime timestamp) {
        StudyActivityEvent event = new StudyActivityEvent();
        event.setEventId(eventId);
        event.setTimestamp(timestamp);
        return event;
    }
    
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(
            StudyActivityEventRequest request, Integer offsetBy, Integer pageSize) {
        checkNotNull(request);
        
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        String appId = request.getAppId();
        String userId = request.getUserId();
        String studyId = request.getStudyId();
        String eventId = request.getObjectId();

        // These need to be emulated in the history view as well, or it might be confusing to consumers
        if (eventId.equals(START_DATE_FIELD) || eventId.equals(CREATED_ON_FIELD)) {
            
            List<StudyActivityEvent> list = dao.getRecentStudyActivityEvents(userId, studyId);
            
            List<StudyActivityEvent> finalEvents = new ArrayList<>();
            
            Account account = accountService.getAccountNoFilter(AccountId.forId(appId, userId))
                    .orElseThrow(() -> new EntityNotFoundException(Account.class));
            
            if (eventId.equals(START_DATE_FIELD)) {
                makeStudyStartDate(list, finalEvents, account.getCreatedOn());
            } else {
                makeCreatedOn(finalEvents, account.getCreatedOn());   
            }
            return new PagedResourceList<>(finalEvents, 1, true)
                    .withRequestParam(ResourceList.OFFSET_BY, offsetBy)
                    .withRequestParam(ResourceList.PAGE_SIZE, pageSize);    
        }
        App app = appService.getApp(appId);
        String adjEventId = formatActivityEventId(app.getCustomEvents().keySet(), request.getObjectId());
        return dao.getStudyActivityEventHistory(userId, studyId, adjEventId, offsetBy, pageSize)
            .withRequestParam(ResourceList.OFFSET_BY, offsetBy)
            .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
    }
    
    /**
     * If the triggering event is mutable, it will succeed and these events must update as well, so they are 
     * always mutable when this function is called. 
     */
    private void createAutomaticCustomEvents(App app, StudyActivityEventRequest request) {
        StudyActivityEvent event = request.toStudyActivityEvent();
        for (Map.Entry<String, String> oneAutomaticEvent : app.getAutomaticCustomEvents().entrySet()) {
            String automaticEventKey = oneAutomaticEvent.getKey(); // new event key
            Tuple<String> autoEventSpec = BridgeUtils.parseAutoEventValue(oneAutomaticEvent.getValue()); // originEventId:Period
            // enrollment, activities_retrieved, or any of the custom:* events defined by the user.
            if (event.getEventId().equals(autoEventSpec.getLeft())) {
                
                Period automaticEventDelay = Period.parse(autoEventSpec.getRight());
                DateTime automaticEventTime = new DateTime(event.getTimestamp()).plus(automaticEventDelay);
                
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