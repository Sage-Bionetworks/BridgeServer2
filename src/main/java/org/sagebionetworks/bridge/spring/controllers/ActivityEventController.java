package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventMap;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventParams;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.StudyActivityEventService;
import org.sagebionetworks.bridge.services.StudyService;

@CrossOrigin
@RestController
public class ActivityEventController extends BaseController {
    static final StatusMessage EVENT_RECORDED_MSG = new StatusMessage("Event recorded.");
    static final StatusMessage EVENT_DELETED_MSG = new StatusMessage("Event deleted.");

    private ActivityEventService activityEventService;
    
    private StudyActivityEventService studyActivityEventService;
    
    private StudyService studyService;

    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    DateTime getDateTime() {
        return new DateTime();
    }

    @PostMapping("/v1/activityevents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createCustomActivityEvent() {
        UserSession session = getAuthenticatedAndConsentedSession();
        CustomActivityEventRequest activityEvent = parseJson(CustomActivityEventRequest.class);

        App app = appService.getApp(session.getAppId());
        activityEventService.publishCustomEvent(app, session.getHealthCode(), 
                activityEvent.getEventKey(), activityEvent.getTimestamp());
        
        return EVENT_RECORDED_MSG;
    }
    
    @DeleteMapping("/v1/activityevents/{eventId}")
    public StatusMessage deleteCustomActivityEvent(@PathVariable String eventId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        App app = appService.getApp(session.getAppId());
        activityEventService.deleteCustomEvent(app, session.getHealthCode(), eventId);
        
        return EVENT_DELETED_MSG;
    }

    @GetMapping("/v1/activityevents")
    public ResourceList<ActivityEvent> getSelfActivityEvents() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<ActivityEvent> activityEvents = activityEventService.getActivityEventList(session.getAppId(),
                null, session.getHealthCode());
        
        return new ResourceList<>(activityEvents);
    }
    
    /* v2 study-scoped APIs for participants */
    
    @GetMapping("/v5/studies/{studyId}/participants/self/activityevents")
    public ResourceList<StudyActivityEvent> getRecentActivityEventsForSelf(@PathVariable String studyId)
            throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        DateTime timelineRequestedOn = getDateTime();
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withTimelineAccessedOn(timelineRequestedOn).build();
        requestInfoService.updateRequestInfo(requestInfo);
        
        studyActivityEventService.publishEvent(new StudyActivityEventParams()
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(session.getId())
                .withObjectType(TIMELINE_RETRIEVED)
                .withTimestamp(timelineRequestedOn));

        return studyActivityEventService.getRecentStudyActivityEvents(session.getAppId(), session.getId(), studyId);
    }

    @GetMapping("/v5/studies/{studyId}/participants/self/activityevents/{eventId}")
    public ResourceList<StudyActivityEvent> getActivityEventHistoryForSelf(@PathVariable String studyId, 
            @PathVariable String eventId,
            @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        AccountId accountId = AccountId.forId(session.getAppId(), session.getId());
        
        return studyActivityEventService.getStudyActivityEventHistory(
                accountId, studyId, eventId, offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/activityevents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage publishActivityEventForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        StudyActivityEventRequest request = parseJson(StudyActivityEventRequest.class);
        StudyActivityEventMap eventMap = studyService.getStudyActivityEventMap(session.getAppId(), studyId);

        StudyActivityEventParams builder = request.parse(eventMap);
        builder.withAppId(session.getAppId());
        builder.withStudyId(studyId);
        builder.withUserId(session.getId());
        
        studyActivityEventService.publishEvent(builder);
        
        return EVENT_RECORDED_MSG;
    }   
    
    @DeleteMapping("/v5/studies/{studyId}/participants/self/activityevents/{eventId}")
    public StatusMessage deleteActivityEventForSelf(@PathVariable String studyId, @PathVariable String eventId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        studyActivityEventService.deleteCustomEvent(new StudyActivityEventParams()
                .withAppId(session.getAppId())
                .withStudyId(studyId)
                .withUserId(session.getId())
                .withObjectId(eventId)
                .withObjectType(CUSTOM));
        
        return EVENT_DELETED_MSG;
    }
}
