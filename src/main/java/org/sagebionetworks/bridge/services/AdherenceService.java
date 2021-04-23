package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_ACCESS_ADHERENCE_DATA;
import static org.sagebionetworks.bridge.models.ResourceList.CURRENT_TIME_SERIES_ONLY;
import static org.sagebionetworks.bridge.models.ResourceList.END_DAY;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_REPEATS;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.START_DAY;
import static org.sagebionetworks.bridge.models.ResourceList.START_EVENT_ID;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.INSTANCE;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.validators.AdherenceRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AdherenceService {

    private AdherenceRecordDao dao;
    
    private ActivityEventService activityEventService;
    
    private AccountService accountService;
    
    @Autowired
    final void setAdherenceRecordDao(AdherenceRecordDao dao) {
        this.dao = dao;
    }
    
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    public void createAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);
        
        Validate.entityThrowingException(AdherenceRecordValidator.INSTANCE, record);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                STUDY_ID, record.getStudyId(), USER_ID, record.getUserId());
        
        AdherenceRecord exists = dao.get(record);
        if (exists != null) {
            throw new EntityAlreadyExistsException(AdherenceRecord.class, ImmutableMap.of(
                "userId", record.getUserId(),
                "studyId", record.getStudyId(),
                "guid", record.getGuid(), 
                "startedOn", record.getStartedOn()));
        }
        dao.create(record);
    }
    
    public void updateAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);
        
        Validate.entityThrowingException(AdherenceRecordValidator.INSTANCE, record);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                STUDY_ID, record.getStudyId(), USER_ID, record.getUserId());
        
        AdherenceRecord exists = dao.get(record);
        if (exists == null) {
            throw new EntityNotFoundException(AdherenceRecord.class);
        }
        
        // Should we allow them to change startedOn? Seems like no?
        
        dao.update(record);
    }
    
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(String appId, 
            AdherenceRecordsSearch search) {
        
        Validate.entityThrowingException(INSTANCE, search);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                STUDY_ID, search.getStudyId(), USER_ID, search.getUserId());
        
        String healthCode = accountService.getHealthCodeForAccount(
                AccountId.forId(appId, search.getUserId()));
        // this can happen under strange circumstances, like adding an account
        // directly to the database. We might want to log this.
        if (healthCode == null) {
            return new PagedResourceList<>(ImmutableList.of(), 0, true);
        }

        // Because we know we only use events when returning records for the 
        // current time series (only), we can optimize by skipping this retrieval
        Map<String, DateTime> events = ImmutableMap.of();
        if (TRUE.equals(search.getCurrentTimeseriesOnly())) {
            events = activityEventService.getActivityEventMap(
                    appId, search.getStudyId(), healthCode);
        }

        return dao.getAdherenceRecords(events, search)
                .withRequestParam(ResourceList.STUDY_ID, search.getStudyId())
                .withRequestParam(INCLUDE_REPEATS, search.getIncludeRepeats())
                .withRequestParam(CURRENT_TIME_SERIES_ONLY, search.getCurrentTimeseriesOnly())
                .withRequestParam(START_EVENT_ID, search.getStartEventId())
                .withRequestParam(START_DAY, search.getStartDay())
                .withRequestParam(END_DAY, search.getEndDay())
                .withRequestParam(OFFSET_BY, search.getOffsetBy())
                .withRequestParam(PAGE_SIZE, search.getPageSize());
    }
}
