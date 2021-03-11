package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.services.Schedule2Service;

@CrossOrigin
@RestController
public class Schedule2Controller extends BaseController {

    private Schedule2Service service;
    
    @Autowired
    final void setScheduleService(Schedule2Service service) {
        this.service = service;
    }
    
    @GetMapping("/v5/schedules")
    public PagedResourceList<Schedule2> getSchedules(@RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER);
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0); 
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean includeDeletedBool = Boolean.valueOf(includeDeleted);
        
        if (session.isInRole(DEVELOPER) || !session.isInRole(STUDY_COORDINATOR)) {
            return service.getSchedules(session.getAppId(), offsetByInt, pageSizeInt, includeDeletedBool);
        }
        return service.getSchedulesForOrganization(session.getAppId(), session.getParticipant().getOrgMembership(), 
                offsetByInt, pageSizeInt, includeDeletedBool);
    }
    
    @PostMapping("/v5/schedules")
    public Schedule2 createSchedule() {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER);
        
        Schedule2 schedule = parseJson(Schedule2.class);
        schedule.setAppId(session.getAppId());
        
        return service.createSchedule(schedule);
    }
    
    @GetMapping("/v5/schedules/{guid}")
    public Schedule2 getSchedule(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER);
        
        return service.getSchedule(session.getAppId(), guid);
    }
    
    @PostMapping("/v5/schedules/{guid}")
    public Schedule2 updateSchedule(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER);
        
        Schedule2 schedule = parseJson(Schedule2.class);
        schedule.setGuid(guid);
        schedule.setAppId(session.getAppId());
        
        return service.updateSchedule(schedule);
    }
    
    @PostMapping("/v5/schedules/{guid}/publish")
    public Schedule2 publishSchedule(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER);
        
        return service.publishSchedule(session.getAppId(), guid);
    }
    
    @DeleteMapping("/v5/schedules/{guid}")
    public StatusMessage deleteSchedule(@PathVariable String guid, @RequestParam String physical) {
        UserSession session = getAuthenticatedSession(STUDY_COORDINATOR, DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteSchedulePermanently(session.getAppId(), guid);
        } else {
            service.deleteSchedule(session.getAppId(), guid);
        }
        return new StatusMessage("Schedule deleted.");
    }
}
