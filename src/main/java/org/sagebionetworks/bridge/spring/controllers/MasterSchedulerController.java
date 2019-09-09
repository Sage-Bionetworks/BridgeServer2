package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.models.studies.Study;
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

import com.fasterxml.jackson.databind.JsonNode;

@CrossOrigin
@RestController
public class MasterSchedulerController extends BaseController {

    static final StatusMessage CREATED_MSG = new StatusMessage("Scheduler config created.");
    static final StatusMessage UPDATED_MSG = new StatusMessage("Scheduler config updated.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Scheduler config deleted.");

    @Autowired
    MasterSchedulerService masterSchedulerService;

    public void setMasterSchedulerService(MasterSchedulerService masterSchedulerService) {
        this.masterSchedulerService = masterSchedulerService;
    }

    @GetMapping("/v3/schedulerconfigs")
    public ResourceList<MasterSchedulerConfig> getAllSchedulerConfigs() {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);
        System.out.println("++++++++++++++++get all schedulers");
        return null;
    }

    @PostMapping("/v3/schedulerconfigs")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createSchedulerConfig() {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        System.out.println(schedulerConfig.toString());
        return CREATED_MSG;
    }

    /**
     * Get a single scheduler config.
     */
    @GetMapping("/v3/schedulerconfigs/{scheduleId}")
    public MasterSchedulerConfig getSchedulerConfig(@PathVariable String scheduleId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        return null;
    }

    /**
     * Update a single scheduler config.
     */
    @PostMapping("/v3/schedulerconfigs/{scheduleId}")
    public StatusMessage updateSchedulerConfig(@PathVariable String scheduleId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        MasterSchedulerConfig schedulerConfig = parseJson(MasterSchedulerConfig.class);
        return UPDATED_MSG;
    }

    /**
     * Delete an individual study report record.
     */
    @DeleteMapping("/v3/schedulerconfigs/{scheduleId}")
    public StatusMessage deleteSchedulerConfig(@PathVariable String scheduleId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        return DELETED_MSG;
    }
}
