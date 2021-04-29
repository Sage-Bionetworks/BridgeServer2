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

import com.google.common.collect.Maps;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AdherenceService {

    private AdherenceRecordDao dao;
    
    private AppService appService;
    
    @Autowired
    final void setAdherenceRecordDao(AdherenceRecordDao dao) {
        this.dao = dao;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    public void updateAdherenceRecords(List<AdherenceRecord> recordList) {
        checkNotNull(recordList);
        
        // This will fail with the first record that is invalid, but it will return
        // an error message that indicates the index of that record...
        Errors errors = new MapBindingResult(Maps.newHashMap(), "records");
        for (int i=0, len = recordList.size(); i < len; i++) {
            AdherenceRecord record = recordList.get(i);
            
            errors.pushNestedPath("["+i+"]");
            INSTANCE.validate(record, errors);
            errors.popNestedPath();
            Validate.throwException(errors, record);
        }
        // The only caller to this method set all the studyId/userId fields identically
        // so it is acceptable to test the first item in the list.
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, recordList.get(0).getStudyId(), 
                AuthEvaluatorField.USER_ID, recordList.get(0).getUserId());
        
        dao.updateAdherenceRecords(recordList);
    }
    
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(String appId, 
            AdherenceRecordsSearch search) {
        
        search = cleanupEventIds(requireNonNull(appId), requireNonNull(search));
        
        Validate.entityThrowingException(AdherenceRecordsSearchValidator.INSTANCE, search);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, search.getStudyId(), 
                AuthEvaluatorField.USER_ID, search.getUserId());
        
        return dao.getAdherenceRecords(search)
                .withRequestParam(ASSESSMENT_IDS, search.getAssessmentIds())
                .withRequestParam(END_TIME, search.getEndTime())
                .withRequestParam(EVENT_TIMESTAMPS, search.getEventTimestamps())
                .withRequestParam(INCLUDE_REPEATS, search.getIncludeRepeats())
                .withRequestParam(INSTANCE_GUIDS, search.getInstanceGuids())
                .withRequestParam(OFFSET_BY, search.getOffsetBy())
                .withRequestParam(PAGE_SIZE, search.getPageSize())
                .withRequestParam(RECORD_TYPE, search.getRecordType())
                .withRequestParam(SESSION_GUIDS, search.getSessionGuids())
                .withRequestParam(SORT_ORDER, search.getSortOrder())
                .withRequestParam(START_TIME, search.getStartTime())
                .withRequestParam(STUDY_ID, search.getStudyId())
                .withRequestParam(TIME_WINDOW_GUIDS, search.getTimeWindowGuids());
    }

    protected AdherenceRecordsSearch cleanupEventIds(String appId, AdherenceRecordsSearch search) {
        // This fixes things like failing to put a "custom:" prefix on a custom
        // event. 
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
            search = new AdherenceRecordsSearch.Builder()
                    .copyOf(search)
                    .withEventTimestamps(fixedMap)
                    .build();
        }
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
            search = new AdherenceRecordsSearch.Builder()
                    .copyOf(search)
                    .withGuidToStartedOnMap(map)
                    .withInstanceGuids(instanceGuids)
                    .build();
        }
        return search;
    }
}
