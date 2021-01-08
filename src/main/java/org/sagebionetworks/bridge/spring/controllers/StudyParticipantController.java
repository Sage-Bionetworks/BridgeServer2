package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.models.activities.ActivityEvent.ACTIVITY_EVENT_WRITER;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.EnrollmentService;

/**
 * APIs for a study coordinator to access participants in a study (that they are 
 * associated to through their organization).
 */
@CrossOrigin
@RestController
public class StudyParticipantController extends BaseController {
    static final StatusMessage EVENT_RECORDED_MSG = new StatusMessage("Event recorded");

    private EnrollmentService enrollmentService;
    
    private ActivityEventService activityEventService;
    
    @Autowired
    final void setEnrollmentService(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }
    
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/{userId}/activityEvents")
    public String getActivityEvents(@PathVariable String studyId, @PathVariable String userId)
            throws JsonProcessingException {
        // TODO: Fix permissions with study coordinator stuff before merging
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        AccountId accountId = AccountId.forId(session.getAppId(), userId);
        String healthCode = accountService.getHealthCodeForAccount(accountId);
        if (healthCode == null) {
            throw new EntityNotFoundException(Account.class);
        }
        
        List<ActivityEvent> events = activityEventService.getActivityEventList(session.getAppId(), healthCode, studyId);
        
        return ACTIVITY_EVENT_WRITER.writeValueAsString(new ResourceList<>(events));
    }

    @PostMapping("/v5/studies/{studyId}/participants/{userId}/activityEvents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createActivityEvent(@PathVariable String studyId, @PathVariable String userId) {
        // TODO: Fix permissions with study coordinator stuff before merging
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        checkAccountInStudy(session.getAppId(), studyId, userId);
        
        AccountId accountId = AccountId.forId(session.getAppId(), userId);
        String healthCode = accountService.getHealthCodeForAccount(accountId);
        
        CustomActivityEventRequest event = parseJson(CustomActivityEventRequest.class);
        
        App app = appService.getApp(session.getAppId());
        activityEventService.publishCustomEvent(app, healthCode,
                event.getEventKey(), event.getTimestamp(), studyId);
        
        return EVENT_RECORDED_MSG;
    }
    
    @GetMapping("/v5/studies/{studyId}/participants/self/activityEvents")
    public String getSelfActivityEvents(@PathVariable String studyId) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        checkAccountInStudy(session.getAppId(), studyId, session.getId());
        
        List<ActivityEvent> events = activityEventService.getActivityEventList(session.getAppId(),
                session.getHealthCode(), studyId);
        
        return ACTIVITY_EVENT_WRITER.writeValueAsString(new ResourceList<>(events));
    }

    @PostMapping("/v5/studies/{studyId}/participants/self/activityEvents")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createSelfActivityEvent(@PathVariable String studyId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        CustomActivityEventRequest event = parseJson(CustomActivityEventRequest.class);

        checkAccountInStudy(session.getAppId(), studyId, session.getId());
        
        App app = appService.getApp(session.getAppId());
        activityEventService.publishCustomEvent(app, session.getHealthCode(),
                event.getEventKey(), event.getTimestamp(), studyId);
        
        return EVENT_RECORDED_MSG;
    }
    
    /**
     * Verify that the account referenced is enrolled in the target study.
     * 
     * @throws EntityNotFoundException
     */
    void checkAccountInStudy(String appId, String studyId, String userId) {
        // We need an auth check here: study coordinator of the study, or a researcher (or self for the
        // self calls)
        List<EnrollmentDetail> enrollments = enrollmentService.getEnrollmentsForUser(appId, userId, studyId);
        boolean matches = enrollments.stream().anyMatch(en -> studyId.equals(en.getStudyId()));
        if (!matches) {
            throw new EntityNotFoundException(Account.class);
        }
    }
}