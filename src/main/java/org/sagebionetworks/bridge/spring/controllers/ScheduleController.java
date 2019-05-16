package org.sagebionetworks.bridge.spring.controllers;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;

@CrossOrigin
@RestController
public class ScheduleController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleController.class);
    
    private SchedulePlanService schedulePlanService;
    
    @Autowired
    final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    @Deprecated
    @GetMapping("/api/v1/schedules")
    public ResourceList<Schedule> getSchedulesV1() throws Exception {
        getAuthenticatedAndConsentedSession();
        return new ResourceList<>(ImmutableList.of());
    }
    
    @Deprecated
    @GetMapping("/v3/schedules")
    public String getSchedulesV3() throws Exception {
        List<Schedule> schedules = getSchedulesInternal();
        
        JsonNode node = MAPPER.valueToTree(new ResourceList<Schedule>(schedules));
        ArrayNode items = (ArrayNode)node.get("items");
        for (int i=0; i < items.size(); i++) {
            // If the schedule has this cron string, make it a recurring, "persistent" schedule
            if ("0 0 12 1/1 * ? *".equals(schedules.get(i).getCronTrigger())) {
                ((ObjectNode)items.get(i)).put("scheduleType", ScheduleType.RECURRING.name().toLowerCase());
                ((ObjectNode)items.get(i)).put("persistent", true);
            }
        }
        return node.toString();
    }
    
    @GetMapping("/v4/schedules")
    public ResourceList<Schedule> getSchedules() throws Exception {
        List<Schedule> schedules = getSchedulesInternal();
        return new ResourceList<>(schedules);
    }
    
    private List<Schedule> getSchedulesInternal() {
        UserSession session = getAuthenticatedAndConsentedSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        ClientInfo clientInfo = getClientInfoFromUserAgentHeader();

        ScheduleContext context = new ScheduleContext.Builder()
                .withLanguages(getLanguages(session))
                .withStudyIdentifier(studyId)
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withClientInfo(clientInfo).build();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(clientInfo, studyId, false);

        List<Schedule> schedules = Lists.newArrayListWithCapacity(plans.size());
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            if (schedule != null) {
                schedules.add(schedule);
            } else {
                LOG.warn("Schedule plan "+plan.getLabel()+" has no schedule for user "+session.getId());
            }
        }
        return schedules;
    }
}
