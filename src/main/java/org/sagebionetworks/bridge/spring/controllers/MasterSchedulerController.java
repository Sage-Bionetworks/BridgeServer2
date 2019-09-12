package org.sagebionetworks.bridge.spring.controllers;

import java.util.List;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.services.MasterSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class MasterSchedulerController extends BaseController {
    static final StatusMessage CREATED_MSG = new StatusMessage("Scheduler config created.");
    static final StatusMessage UPDATED_MSG = new StatusMessage("Scheduler config updated.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Scheduler config deleted.");

    @Autowired
    MasterSchedulerService schedulerService;

    public void setMasterSchedulerService(MasterSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/v3/schedulerconfigs")
    public ResourceList<MasterSchedulerConfig> getAllSchedulerConfigs() {
        getAuthenticatedSession(Roles.ADMIN);
        List<MasterSchedulerConfig> configs = schedulerService.getAllSchedulerConfigs();
        return new ResourceList<>(configs);
    }

    @PostMapping("/v3/schedulerconfigs")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createSchedulerConfig() {
        getAuthenticatedSession(Roles.ADMIN);

        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        
        schedulerService.createSchedulerConfig(schedulerConfig);
        return CREATED_MSG;
    }

    /**
     * Get a single scheduler config.
     */
    @GetMapping("/v3/schedulerconfigs/{scheduleId}")
    public MasterSchedulerConfig getSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(Roles.ADMIN);
        
        return schedulerService.getSchedulerConfig(scheduleId);
    }

    /**
     * Update a single scheduler config.
     */
    @PostMapping("/v3/schedulerconfigs/{scheduleId}")
    public StatusMessage updateSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(Roles.ADMIN);
        
        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        
        schedulerService.updateSchedulerConfig(scheduleId, schedulerConfig);
        return UPDATED_MSG;
    }

    /**
     * Delete an individual study report record.
     */
    @DeleteMapping("/v3/schedulerconfigs/{scheduleId}")
    public StatusMessage deleteSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(Roles.ADMIN);
        
        schedulerService.deleteSchedulerConfig(scheduleId);
        return DELETED_MSG;
    }
}
