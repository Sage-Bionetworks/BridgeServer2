package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDIES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_UPDATE_STUDIES;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.CAN_EDIT_STUDY_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadataView;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.Schedule2Service;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.spring.util.EtagSupport;
import org.sagebionetworks.bridge.spring.util.EtagCacheKey;

@CrossOrigin
@RestController
public class Schedule2Controller extends BaseController {

    static final TimelineMetadataView EMPTY_VIEW = new TimelineMetadataView(new TimelineMetadata());
    static final StatusMessage DELETED_MSG = new StatusMessage("Schedule deleted.");
    
    private Schedule2Service service;
    
    private StudyService studyService;
    
    @Autowired
    final void setScheduleService(Schedule2Service service) {
        this.service = service;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @EtagSupport({
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"})
    })
    @GetMapping("/v5/studies/{studyId}/schedule")
    public Schedule2 getSchedule(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        
        Study study = studyService.getStudy(session.getAppId(), studyId, true);
        CAN_READ_STUDIES.checkAndThrow(STUDY_ID, studyId);
        
        return service.getScheduleForStudy(session.getAppId(), study)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
    }
    
    @PostMapping("/v5/studies/{studyId}/schedule")
    public ResponseEntity<Schedule2> createOrUpdateSchedule(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        
        Schedule2 schedule = parseJson(Schedule2.class);
        schedule.setAppId(session.getAppId());
        
        Study study = studyService.getStudy(session.getAppId(), studyId, true);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, studyId);
        
        if (!CAN_EDIT_STUDY_CORE.contains(study.getPhase())) {
            throw new BadRequestException("Study schedule cannot be changed during phase " 
                    + study.getPhase().label() + ".");
        }
        int status = (study.getScheduleGuid() == null) ? 201: 200;
        Schedule2 retValue = service.createOrUpdateStudySchedule(study, schedule);
        
        return ResponseEntity.status(status).body(retValue);
    }

    /** Returns a list of all study IDs in the given app that use the given schedule. */
    @GetMapping("/v1/apps/{appId}/schedules/{scheduleGuid}/studies")
    public ResourceList<String> getStudyIdsUsingSchedule(@PathVariable String appId, @PathVariable String scheduleGuid) {
        getAuthenticatedSession(WORKER);
        List<String> studyIdList = studyService.getStudyIdsUsingSchedule(appId, scheduleGuid);
        return new ResourceList<>(studyIdList);
    }

    @EtagSupport({
        @EtagCacheKey(model=Schedule2.class, keys={"appId", "studyId"})
    })
    @GetMapping("/v5/studies/{studyId}/timeline")
    public Timeline getTimeline(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        Study study = studyService.getStudy(session.getAppId(), studyId, true);
        CAN_READ_STUDIES.checkAndThrow(STUDY_ID, studyId);
        
        if (study.getScheduleGuid() == null) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        return service.getTimelineForSchedule(session.getAppId(), study.getScheduleGuid());
    }
    
    @DeleteMapping("/v5/schedules/{guid}")
    public StatusMessage deleteSchedule(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        service.deleteSchedulePermanently(session.getAppId(), guid);
        
        return DELETED_MSG;
    }
    
    @GetMapping("/v1/apps/{appId}/timelinemetadata/{instanceGuid}")
    public TimelineMetadataView getTimelineMetadataForWorker(@PathVariable String appId, @PathVariable String instanceGuid) { 
        getAuthenticatedSession(WORKER);
        
        Optional<TimelineMetadata> optional = service.getTimelineMetadata(instanceGuid);
        return (optional.isPresent()) ? new TimelineMetadataView(optional.get()) : EMPTY_VIEW;
    }
}
