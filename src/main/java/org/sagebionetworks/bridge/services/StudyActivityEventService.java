package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.BridgeUtils.parseAutoEventValue;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CREATED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.FIRST_SIGN_IN;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SENT_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.DELETE_INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.CREATE_INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Activity events that are scoped to a person participating in a specific study. 
 * Unlike v1 of activity events, these events maintain a history of their changes 
 * (if they are mutable). They also include some metadata that the earlier event 
 * system could not maintain, including a client time zone. This API will replace 
 * the v1 API, so some events that span all studies (like the creation of an 
 * account) are also in the events returned by this system. 
 * 
 * The code in this class to insert an enrollment event, when there is no enrollment
 * event in the tables, is backfill code for accounts that were created and enrolled 
 * in a study before the deployment of study-specific events. There is no apparent 
 * reason to actually save them in the table at this point since the records should
 * only be accessed through this service.
 */
@Component
public class StudyActivityEventService {
    
    static final String CREATED_ON_FIELD = CREATED_ON.name().toLowerCase();
    static final String ENROLLMENT_FIELD = ENROLLMENT.name().toLowerCase();
    static final String SENT_INSTALL_LINK_FIELD = SENT_INSTALL_LINK.name().toLowerCase();
    static final String FIRST_SIGN_IN_FIELD = FIRST_SIGN_IN.name().toLowerCase();
    static final List<String> GLOBAL_EVENTS_OF_INTEREST = ImmutableList.of(
            CREATED_ON_FIELD, FIRST_SIGN_IN_FIELD, SENT_INSTALL_LINK_FIELD);

    private StudyActivityEventDao dao;
    private AppService appService;
    private AccountService accountService;
    private ActivityEventService activityEventService;
    
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
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
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
        request.customEvents(app.getCustomEvents());
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        
        Validate.entityThrowingException(DELETE_INSTANCE, event);
        
        StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                event.getUserId(), event.getStudyId(), event.getEventId());

        if (request.getUpdateType().canDelete(mostRecent, event)) {
            dao.deleteCustomEvent(event);
        }
    }
    
    public void publishEvent(StudyActivityEventRequest request) {
        checkNotNull(request);

        App app = appService.getApp(request.getAppId());
        request.customEvents(app.getCustomEvents());
        request.createdOn(getCreatedOn());
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        
        Validate.entityThrowingException(CREATE_INSTANCE, event);
        
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
        addEnrollmentIfMissing(account, events, studyId);
        
        // There are some global events related to authentication and account creation that 
        // are useful when working with study-specific events, so add these from the global
        // system.
        Map<String, DateTime> map = activityEventService.getActivityEventMap(
                account.getAppId(), account.getHealthCode());
        for (String fieldName : GLOBAL_EVENTS_OF_INTEREST) {
            addIfMissing(events, map, fieldName);    
        }
        return new ResourceList<>(events); 
    }
    
    private void addIfMissing(List<StudyActivityEvent> events, Map<String, DateTime> map, String field) {
        if (map.containsKey(field)) {
            events.add(new StudyActivityEvent(field, map.get(field)));
        }
    }
    
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(
            AccountId accountId, String studyId, String eventId, Integer offsetBy, Integer pageSize) {
        
        if (eventId == null) {
            throw new BadRequestException("Event ID is required");
        }
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        Account account = accountService.getAccountNoFilter(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        App app = appService.getApp(accountId.getAppId());
        eventId = formatActivityEventId(app.getCustomEvents().keySet(), eventId);
        if (eventId == null) {
            throw new BadRequestException(INVALID_EVENT_ID);
        }
        
        // Global events emulate history for a cleaner and less confusing API, but there 
        // will only ever one value.
        if (GLOBAL_EVENTS_OF_INTEREST.contains(eventId)) {
            Map<String, DateTime> map = activityEventService.getActivityEventMap(
                    account.getAppId(), account.getHealthCode());
            List<StudyActivityEvent> events = new ArrayList<>();
            addIfMissing(events, map, eventId);
            
            return new PagedResourceList<>(events, 1, true)
                    .withRequestParam(ResourceList.OFFSET_BY, offsetBy)
                    .withRequestParam(ResourceList.PAGE_SIZE, pageSize);    
        }
        
        PagedResourceList<StudyActivityEvent> results = dao.getStudyActivityEventHistory(
                account.getId(), studyId, eventId, offsetBy, pageSize);
        
        if (eventId.equals(ENROLLMENT_FIELD) && results.getItems().size() == 0) {
            Enrollment en = findEnrollmentByStudyId(account, studyId);
            if (en != null) {
                List<StudyActivityEvent> events = new ArrayList<>();
                events.add(new StudyActivityEvent(ENROLLMENT_FIELD, en.getEnrolledOn()));
                results = new PagedResourceList<>(events, 1, true);
            }
        }
        return results.withRequestParam(ResourceList.OFFSET_BY, offsetBy)
            .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
    }
    
    /**
     * If the triggering event is mutable, these events can be created as well.
     */
    private void createAutomaticCustomEvents(App app, StudyActivityEventRequest request) {
        String eventId = request.toStudyActivityEvent().getEventId();
        for (Map.Entry<String, String> oneAutomaticEvent : app.getAutomaticCustomEvents().entrySet()) {
            String automaticEventKey = oneAutomaticEvent.getKey(); // new event key
            Tuple<String> autoEventSpec = parseAutoEventValue(oneAutomaticEvent.getValue());
            String triggerEventId = autoEventSpec.getLeft();
            
            // enrollment, activities_retrieved, or any of the custom:* events defined by the user.
            if (eventId.equals(triggerEventId)) {
                Period automaticEventDelay = Period.parse(autoEventSpec.getRight());
                DateTime automaticEventTime = new DateTime(request.getTimestamp()).plus(automaticEventDelay);
                
                StudyActivityEvent automaticEvent = request.copy()
                    .customEvents(ImmutableMap.of(automaticEventKey, MUTABLE))
                    .objectType(CUSTOM)
                    .objectId(automaticEventKey)
                    .timestamp(automaticEventTime)
                    .toStudyActivityEvent();
                dao.publishEvent(automaticEvent);
            }
        }        
    }
    
    /**
     * If events do not include enrollment, you can include it. This provides some
     * migration support.
     */
    private void addEnrollmentIfMissing(Account account, List<StudyActivityEvent> events, String studyId) {
        for (StudyActivityEvent oneEvent : events) {
            if (oneEvent.getEventId().equals(ENROLLMENT_FIELD)) {
                return;
            }
        }
        Enrollment en = findEnrollmentByStudyId(account, studyId);
        if (en != null) {
            events.add(new StudyActivityEvent(ENROLLMENT_FIELD, en.getEnrolledOn()));
        }
    }
    
    private Enrollment findEnrollmentByStudyId(Account account, String studyId) {
        for (Enrollment oneEnrollment : account.getEnrollments()) {
            if (oneEnrollment.getStudyId().equals(studyId)) {
                return oneEnrollment;
            }
        }
        return null;
    }
}