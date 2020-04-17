package org.sagebionetworks.bridge.spring.controllers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.schedules.ScheduledActivity.SCHEDULED_ACTIVITY_WRITER;
import static org.sagebionetworks.bridge.time.DateUtils.parseZoneFromOffsetString;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.time.DateUtils;

@CrossOrigin
@RestController
public class ScheduledActivityController extends BaseController {
    
    static final TypeReference<ArrayList<ScheduledActivity>> SCHEDULED_ACTIVITY_TYPE_REF = new TypeReference<ArrayList<ScheduledActivity>>() {};
    static final StatusMessage UPDATED_MSG = new StatusMessage("Activities updated.");
    static final String MISSING_TIMESTAMP_ERROR = "startsOn and endsOn are both required and must be ISO 8601 timestamps.";
    static final String AMBIGUOUS_TIMEZONE_ERROR = "startsOn and endsOn must be in the same time zone.";

    private ScheduledActivityService scheduledActivityService;

    @Autowired
    final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    
    @Deprecated
    @GetMapping("/v3/tasks")
    public JsonNode getTasks(@RequestParam(required = false) String until,
            @RequestParam(required = false) String offset, @RequestParam(required = false) String daysAhead) {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(until, offset, daysAhead, null);
        
        return okResultAsTasks(scheduledActivities);
    }
    
    @Deprecated
    @GetMapping(path="/v3/activities", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getScheduledActivities(@RequestParam(required = false) String until,
            @RequestParam(required = false) String offset, @RequestParam(required = false) String daysAhead,
            @RequestParam(required = false) String minimumPerSchedule) throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(until, offset, daysAhead,
                minimumPerSchedule);
        
        ResourceList<ScheduledActivity> list = new ResourceList<>(scheduledActivities);
        
        return SCHEDULED_ACTIVITY_WRITER.writeValueAsString(list);
    }

    @GetMapping("/v3/activities/{activityGuid}")
    public JsonNode getActivityHistory(@PathVariable String activityGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize)
            throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        if (offsetKey == null) {
            offsetKey = offsetBy;
        }
        
        DateTime scheduledOnStartObj = getDateTimeOrDefault(scheduledOnStart, null);
        DateTime scheduledOnEndObj = getDateTimeOrDefault(scheduledOnEnd, null);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = scheduledActivityService.getActivityHistory(
                session.getHealthCode(), activityGuid, scheduledOnStartObj, scheduledOnEndObj, offsetKey, pageSizeInt);

        // If offsetBy was supplied, we return it as a top-level property of the list for backwards compatibility.
        
        String json = SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
        JsonNode node = MAPPER.readTree(json);
        if (offsetBy != null) {
            ((ObjectNode)node).put(OFFSET_BY, offsetBy);    
        }
        return node;
    }

    @GetMapping(path="/v4/activities/{activityType}/{referentGuid}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getActivityHistoryV3WithActivityType(@PathVariable String activityType,
            @PathVariable String referentGuid, @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        return getActivityHistoryV3Internal(activityType, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey,
                pageSize);
    }
    
    @GetMapping(path="/v4/activities/{referentGuid}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getActivityHistoryV3(@PathVariable String referentGuid,
            @RequestParam(required = false) String scheduledOnStart,
            @RequestParam(required = false) String scheduledOnEnd, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) throws Exception {
        return getActivityHistoryV3Internal(null, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
    }

    @GetMapping(path="/v4/activities", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getScheduledActivitiesByDateRange(@RequestParam String startTime, @RequestParam String endTime)
            throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(session.getAppId());
        
        DateTime startsOnObj = BridgeUtils.getDateTimeOrDefault(startTime, null);
        DateTime endsOnObj = BridgeUtils.getDateTimeOrDefault(endTime, null);
        if (startsOnObj == null || endsOnObj == null) {
            throw new BadRequestException(MISSING_TIMESTAMP_ERROR);
        }
        if (!startsOnObj.getZone().equals(endsOnObj.getZone())) {
            throw new BadRequestException(AMBIGUOUS_TIMEZONE_ERROR);
        }
        DateTime startsOnInclusive = startsOnObj.minusMillis(1);

        DateTimeZone requestTimeZone = startsOnObj.getZone();
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, startsOnInclusive, endsOnObj, 0);

        List<ScheduledActivity> scheduledActivities = scheduledActivityService.getScheduledActivitiesV4(study, context);
        
        DateTimeRangeResourceList<ScheduledActivity> results = new DateTimeRangeResourceList<>(scheduledActivities)
                .withRequestParam(ResourceList.START_TIME, startsOnObj)
                .withRequestParam(ResourceList.END_TIME, endsOnObj);
        return SCHEDULED_ACTIVITY_WRITER.writeValueAsString(results);
    }

    @PostMapping({"/v4/activities", "/v3/tasks", "/v3/activities"})
    public StatusMessage updateScheduledActivities() {
        UserSession session = getAuthenticatedAndConsentedSession();

        List<ScheduledActivity> scheduledActivities = parseJson(SCHEDULED_ACTIVITY_TYPE_REF);
                
        scheduledActivityService.updateScheduledActivities(session.getHealthCode(), scheduledActivities);

        return UPDATED_MSG;
    }

    private String getActivityHistoryV3Internal(String activityType, String referentGuid, String scheduledOnStart,
            String scheduledOnEnd, String offsetKey, String pageSize) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        ActivityType activityTypeObj = ActivityType.fromPlural(activityType);
        DateTime scheduledOnStartObj = getDateTimeOrDefault(scheduledOnStart, null);
        DateTime scheduledOnEndObj = getDateTimeOrDefault(scheduledOnEnd, null);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = scheduledActivityService.getActivityHistory(
                session.getHealthCode(), activityTypeObj, referentGuid, scheduledOnStartObj, scheduledOnEndObj,
                offsetKey, pageSizeInt);
        
        return SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
    }
    
    private <T> JsonNode okResultAsTasks(List<T> list) {
        JsonNode node = MAPPER.valueToTree(new ResourceList<T>(list));
        ArrayNode items = (ArrayNode)node.get("items");
        for (int i=0; i < items.size(); i++) {
            ObjectNode object = (ObjectNode)items.get(i);
            object.put("type", "Task");
            object.remove("healthCode");
            object.remove("schedulePlanGuid");
        }
        return node;
    }
    
    private List<ScheduledActivity> getScheduledActivitiesInternalV3(String untilString, String offset,
            String daysAhead, String minimumPerScheduleString) {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(session.getAppId());
        
        DateTime endsOn = null;
        DateTimeZone requestTimeZone = null;
        int minimumPerSchedule = getIntOrDefault(minimumPerScheduleString, 0);

        if (StringUtils.isNotBlank(untilString)) {
            // Old API, infer time zone from the until parameter. This is not ideal.
            endsOn = DateTime.parse(untilString);
            requestTimeZone = endsOn.getZone();
        } else if (isNotBlank(daysAhead) && isNotBlank(offset)) {
            int numDays = Integer.parseInt(daysAhead);
            requestTimeZone = parseZoneFromOffsetString(offset);
            // When querying for days, we ignore the time of day of the request and query to then end of the day.
            endsOn = DateTime.now(requestTimeZone).plusDays(numDays).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            throw new BadRequestException("Supply either 'until' parameter, or 'daysAhead' parameter.");
        }
        DateTime now = DateTime.now(requestTimeZone);
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, now, endsOn, minimumPerSchedule);
        return scheduledActivityService.getScheduledActivities(study, context);
    }
    
    private ScheduleContext getScheduledActivitiesInternal(UserSession session, DateTimeZone requestTimeZone,
            DateTime startsOn, DateTime endsOn, int minPerSchedule) {
        
        RequestContext reqContext = BridgeUtils.getRequestContext();
        
        ScheduleContext.Builder builder = new ScheduleContext.Builder();
        
        // This time zone is the time zone of the user upon first contacting the server for activities, and
        // ensures that events are scheduled in this time zone. This ensures that a user will receive activities 
        // on the day they contact the server. If it has not yet been captured, this is the first request, 
        // capture and persist it.
        DateTimeZone initialTimeZone = session.getParticipant().getTimeZone();
        if (initialTimeZone == null) {
            initialTimeZone = persistTimeZone(session, requestTimeZone);
        }

        builder.withStartsOn(startsOn);
        builder.withEndsOn(endsOn);
        builder.withInitialTimeZone(initialTimeZone);
        builder.withUserDataGroups(session.getParticipant().getDataGroups());
        builder.withUserSubstudyIds(session.getParticipant().getSubstudyIds());
        builder.withHealthCode(session.getHealthCode());
        builder.withUserId(session.getId());
        builder.withStudyIdentifier(session.getAppId());
        builder.withAccountCreatedOn(session.getParticipant().getCreatedOn());
        builder.withLanguages(getLanguages(session));
        builder.withClientInfo(reqContext.getCallerClientInfo());
        builder.withMinimumPerSchedule(minPerSchedule);
        
        ScheduleContext context = builder.build();
        
        RequestInfo requestInfo = getRequestInfoBuilder(session).withTimeZone(requestTimeZone)
                .withActivitiesAccessedOn(DateUtils.getCurrentDateTime()).build();
        
        requestInfoService.updateRequestInfo(requestInfo);
        
        return context;
    }

    DateTimeZone persistTimeZone(UserSession session, DateTimeZone timeZone) {
        accountService.editAccount(session.getAppId(), session.getHealthCode(),
                account -> account.setTimeZone(timeZone));
        sessionUpdateService.updateTimeZone(session, timeZone);
        return timeZone;
    }
}
