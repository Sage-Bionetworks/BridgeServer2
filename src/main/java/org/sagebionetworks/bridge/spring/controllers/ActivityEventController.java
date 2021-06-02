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
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.StudyActivityEventService;
import org.sagebionetworks.bridge.time.DateUtils;

@CrossOrigin
@RestController
public class ActivityEventController extends BaseController {
    static final StatusMessage EVENT_RECORDED_MSG = new StatusMessage("Event recorded.");
    static final StatusMessage EVENT_DELETED_MSG = new StatusMessage("Event deleted.");

    private ActivityEventService activityEventService;
    
    private StudyActivityEventService studyActivityEventService;

    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
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
        activityEventService.publishCustomEvent(app, null,
                session.getHealthCode(), activityEvent.getEventKey(), activityEvent.getTimestamp());
        
        return EVENT_RECORDED_MSG;
    }
    
    @DeleteMapping("/v1/activityevents/{eventId}")
    public StatusMessage deleteCustomActivityEvent(@PathVariable String eventId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        App app = appService.getApp(session.getAppId());
        activityEventService.deleteCustomEvent(app, null, session.getHealthCode(), eventId);
        
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
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withTimelineAccessedOn(getDateTime()).build();
        requestInfoService.updateRequestInfo(requestInfo);
        
        studyActivityEventService.publishEvent(new StudyActivityEventRequest()
                .appId(session.getAppId())
                .studyId(studyId)
                .userId(session.getId())
                .objectType(TIMELINE_RETRIEVED)
                .timestamp(getDateTime()));

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
        
        StudyActivityEventRequest request = new StudyActivityEventRequest()
                .appId(session.getAppId())
                .studyId(studyId)
                .userId(session.getId())
                .objectId(eventId)
                .objectType(CUSTOM);
        
        return studyActivityEventService.getStudyActivityEventHistory(request, offsetByInt, pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/participants/self/activityevents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage publishActivityEventForSelf(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        StudyActivityEventRequest request = parseJson(StudyActivityEventRequest.class)
                .appId(session.getAppId())
                .studyId(studyId)
                .userId(session.getId())
                .objectType(CUSTOM);
        studyActivityEventService.publishEvent(request);
        
        return EVENT_RECORDED_MSG;
    }   
    
    @DeleteMapping("/v5/studies/{studyId}/participants/self/activityevents/{eventId}")
    public StatusMessage deleteActivityEventForSelf(@PathVariable String studyId, @PathVariable String eventId) {
        UserSession session = getAuthenticatedAndConsentedSession();

        if (!session.getParticipant().getStudyIds().contains(studyId)) {
            throw new EntityNotFoundException(Account.class);
        }
        
        studyActivityEventService.deleteCustomEvent(new StudyActivityEventRequest()
                .appId(session.getAppId())
                .studyId(studyId)
                .userId(session.getId())
                .objectId(eventId)
                .objectType(CUSTOM));
        
        return EVENT_DELETED_MSG;
    }
}
