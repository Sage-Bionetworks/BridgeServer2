package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static org.sagebionetworks.bridge.AuthUtils.CAN_ACCESS_ADHERENCE_DATA;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.ResourceList.ASSESSMENT_IDS;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.EVENT_TIMESTAMPS;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_REPEATS;
import static org.sagebionetworks.bridge.models.ResourceList.INSTANCE_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.RECORD_TYPE;
import static org.sagebionetworks.bridge.models.ResourceList.SESSION_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.SORT_ORDER;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.TIME_WINDOW_GUIDS;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.INSTANCE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AdherenceService {

    private AdherenceRecordDao dao;
    
    private AppService appService;

    private ActivityEventService activityEventService;
    
    private Schedule2Service scheduleService;
    
    @Autowired
    final void setAdherenceRecordDao(AdherenceRecordDao dao) {
        this.dao = dao;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Autowired
    final void setSchedule2Service(Schedule2Service scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    public void updateAdherenceRecords(String appId, String healthCode, List<AdherenceRecord> recordList) {
        checkNotNull(recordList);
        
        if (recordList.isEmpty()) {
            throw new BadRequestException("No adherence records submitted for update.");
        }
        // It would be nice to bundle this somehow. There might be successful
        // records before and after a failed record, for example. If we had an IEE
        // that took more than one entity, than we could possibly do that.
        for (AdherenceRecord record : recordList) {
            Validate.entityThrowingException(INSTANCE, record);
            
            CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                    AuthEvaluatorField.STUDY_ID, record.getStudyId(), 
                    AuthEvaluatorField.USER_ID, record.getUserId());
            
            dao.updateAdherenceRecord(record);

            if (record.getFinishedOn() != null) {
                TimelineMetadata meta = scheduleService.getTimelineMetadata(record.getInstanceGuid())
                        .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
                if (meta.getAssessmentInstanceGuid() == null) {
                    activityEventService.publishSessionFinishedEvent(
                            record.getStudyId(), healthCode, meta.getSessionGuid(), record.getFinishedOn());
                } else {
                    // Shared and local assessment ID are conceptually different but not 
                    // differentiated for events scheduling. It might be helpful to end
                    // users or we might need to change this.
                    activityEventService.publishAssessmentFinishedEvent(
                            record.getStudyId(), healthCode, meta.getAssessmentId(), record.getFinishedOn());
                }                
            }
        }
    }
    
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(String appId, 
            AdherenceRecordsSearch search) {
        
        Set<String> originalInstanceGuids = ImmutableSet.copyOf(search.getInstanceGuids());
        
        search = cleanupSearch(requireNonNull(appId), requireNonNull(search));
        
        Validate.entityThrowingException(AdherenceRecordsSearchValidator.INSTANCE, search);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, search.getStudyId(), 
                AuthEvaluatorField.USER_ID, search.getUserId());
        
        return dao.getAdherenceRecords(search)
                .withRequestParam(ASSESSMENT_IDS, search.getAssessmentIds())
                .withRequestParam(END_TIME, search.getEndTime())
                .withRequestParam(EVENT_TIMESTAMPS, search.getEventTimestamps())
                .withRequestParam(INCLUDE_REPEATS, search.getIncludeRepeats())
                .withRequestParam(INSTANCE_GUIDS, originalInstanceGuids)
                .withRequestParam(OFFSET_BY, search.getOffsetBy())
                .withRequestParam(PAGE_SIZE, search.getPageSize())
                .withRequestParam(RECORD_TYPE, search.getRecordType())
                .withRequestParam(SESSION_GUIDS, search.getSessionGuids())
                .withRequestParam(SORT_ORDER, search.getSortOrder())
                .withRequestParam(START_TIME, search.getStartTime())
                .withRequestParam(STUDY_ID, search.getStudyId())
                .withRequestParam(TIME_WINDOW_GUIDS, search.getTimeWindowGuids());
    }

    protected AdherenceRecordsSearch cleanupSearch(String appId, AdherenceRecordsSearch search) {
        // optimization: skip all this if not relevant to the search
        boolean skipFixes = search.getEventTimestamps().isEmpty() &&
                            search.getInstanceGuids().isEmpty();
        if (skipFixes) {
            return search;
        }
        AdherenceRecordsSearch.Builder builder = new AdherenceRecordsSearch.Builder()
                .copyOf(search);
        
        // This fixes things like failing to put a "custom:" prefix on a custom event.
        if (!search.getEventTimestamps().isEmpty()) {
            App app = appService.getApp(appId);
            
            Map<String, DateTime> fixedMap = new HashMap<>();
            for (Map.Entry<String, DateTime> entry : search.getEventTimestamps().entrySet()) {
                String eventId = entry.getKey();
                String fixedEventId = formatActivityEventId(app.getCustomEvents().keySet(), eventId);
                
                if (fixedEventId != null) {
                    fixedMap.put(fixedEventId, entry.getValue());    
                }
            }
            builder.withEventTimestamps(fixedMap);
        }
        // parse any compound instance IDs that include a startedOn value, and move them 
        // to a different map.
        if (!search.getInstanceGuids().isEmpty()) {
            Map<String, DateTime> map = new HashMap<>();
            Set<String> instanceGuids = new HashSet<>();
            
            for (String instanceGuid : search.getInstanceGuids()) {
                String[] split = instanceGuid.split(":", 2);
                if (split.length == 2) {
                    map.put(split[0], DateTime.parse(split[1]));
                } else {
                    instanceGuids.add(instanceGuid);
                }
            }
            builder.withInstanceGuidStartedOnMap(map);
            builder.withInstanceGuids(instanceGuids);
        }
        return builder.build();
    }
}
