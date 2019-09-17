package org.sagebionetworks.bridge.spring.controllers;

import java.util.List;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.TimestampHolder;
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
    static final StatusMessage DELETED_MSG = new StatusMessage("Scheduler config deleted.");

    
    @Autowired
    MasterSchedulerService schedulerService;

    public void setMasterSchedulerService(MasterSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/v3/schedulerconfigs")
    public ResourceList<MasterSchedulerConfig> getAllSchedulerConfigs() {
        getAuthenticatedSession(ADMIN);
        
        List<MasterSchedulerConfig> configs = schedulerService.getAllSchedulerConfigs();
        
        return new ResourceList<>(configs);
    }

    @PostMapping("/v3/schedulerconfigs")
    @ResponseStatus(HttpStatus.CREATED)
    public MasterSchedulerConfig createSchedulerConfig() {
        getAuthenticatedSession(ADMIN);
        
        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        
        return schedulerService.createSchedulerConfig(schedulerConfig);
    }

    /**
     * Get a single scheduler config.
     */
    @GetMapping("/v3/schedulerconfigs/{scheduleId}")
    public MasterSchedulerConfig getSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(ADMIN);
        
        return schedulerService.getSchedulerConfig(scheduleId);
    }

    /**
     * Update a single scheduler config.
     */
    @PostMapping("/v3/schedulerconfigs/{scheduleId}")
    public MasterSchedulerConfig updateSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(ADMIN);
        
        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        
        return schedulerService.updateSchedulerConfig(scheduleId, schedulerConfig);
    }

    /**
     * Delete an individual study report record.
     */
    @DeleteMapping("/v3/schedulerconfigs/{scheduleId}")
    public StatusMessage deleteSchedulerConfig(@PathVariable String scheduleId) {
        getAuthenticatedSession(ADMIN);
        
        schedulerService.deleteSchedulerConfig(scheduleId);
        return DELETED_MSG;
    }
    

    /**
     * Get the last time scheduler ran.
     */
    @GetMapping("/v3/schedulerstatus")
    public TimestampHolder getSchedulerStatus() {
        getAuthenticatedSession(ADMIN);
        
        return schedulerService.getSchedulerStatus();
    }
}
