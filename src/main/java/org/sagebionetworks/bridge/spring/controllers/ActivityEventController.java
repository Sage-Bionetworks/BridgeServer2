package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.models.activities.ActivityEvent.ACTIVITY_EVENT_WRITER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ActivityEventService;

@CrossOrigin
@RestController
@RequestMapping("/v1/activityevents")
public class ActivityEventController extends BaseController {

    private ActivityEventService activityEventService;

    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createCustomActivityEvent() {
        UserSession session = getAuthenticatedAndConsentedSession();
        CustomActivityEventRequest activityEvent = parseJson(CustomActivityEventRequest.class);

        Study study = studyService.getStudy(session.getStudyIdentifier());
        activityEventService.publishCustomEvent(study, session.getHealthCode(),
                activityEvent.getEventKey(), activityEvent.getTimestamp());
        
        return new StatusMessage("Event recorded");
    }

    @GetMapping(produces={APPLICATION_JSON_UTF8_VALUE})
    public String getSelfActivityEvents() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<ActivityEvent> activityEvents = activityEventService.getActivityEventList(session.getStudyIdentifier().getIdentifier(),
                session.getHealthCode());
        
        // I do not like the fact we are serializing in the controller, but that's the only way to access
        // the ObjectWriter and that's currently how we suppress healthCode.
        ResourceList<ActivityEvent> list = new ResourceList<>(activityEvents);
        return ACTIVITY_EVENT_WRITER.writeValueAsString(list);
    }    
}
