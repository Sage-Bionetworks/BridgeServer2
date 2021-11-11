package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.BridgeUtils.getElement;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CREATED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.INSTALL_LINK_SENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_BURST;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.DELETE_INSTANCE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.CREATE_INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity events that are scoped to a person participating in a specific study. 
 * Unlike v1 of activity events, these events maintain a history of their changes 
 * (if they are mutable). They also include some metadata that the earlier event 
 * system could not maintain, including a client time zone and their relationship 
 * to study bursts. This API will replace the v1 API, so some events that span 
 * all studies (like the creation of an account) are also in the events returned 
 * by this service. 
 * 
 * The code in this class to insert an enrollment event, when there is no enrollment
 * event in the tables, is backfill code for accounts that were created and enrolled 
 * in a study before the deployment of study-specific events. There is no reason to 
 * save them in the table at this point since the records should only be accessed 
 * through this service.
 */
@Component
public class StudyActivityEventService {
    private static Logger LOG = LoggerFactory.getLogger(StudyActivityEventService.class);
    
    static final String CREATED_ON_FIELD = CREATED_ON.name().toLowerCase();
    static final String ENROLLMENT_FIELD = ENROLLMENT.name().toLowerCase();
    static final String INSTALL_LINK_SENT_FIELD = INSTALL_LINK_SENT.name().toLowerCase();
    static final List<String> GLOBAL_EVENTS_OF_INTEREST = ImmutableList.of(
            CREATED_ON_FIELD, INSTALL_LINK_SENT_FIELD);

    private StudyActivityEventDao dao;
    private StudyService studyService;
    private AccountService accountService;
    private ActivityEventService activityEventService;
    private Schedule2Service scheduleService;
    
    @Autowired
    final void setStudyActivityEventDao(StudyActivityEventDao dao) {
        this.dao = dao;
    }
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setSchedule2Service(Schedule2Service scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    DateTime getCreatedOn() { 
        return DateTime.now();
    }

    /**
     * Delete an event if it is mutable (basically only custom events). If the event was the originating
     * (triggering) event for a set of study burst events, those events will be deleted as well.
     * 
     * @param event
     *      the event to be deleted 
     * @param showError
     *      if false (the default), this method returns quietly regardless of the outcome of publishing 
     *      the event. If true, it will throw a BadRequestException if any event cannot be deleted.
     */
    public void deleteEvent(StudyActivityEvent event, boolean showError) {
        checkNotNull(event);
        
        Validate.entityThrowingException(DELETE_INSTANCE, event);
        
        StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                event.getUserId(), event.getStudyId(), event.getEventId());

        if (event.getUpdateType().canDelete(mostRecent, event)) {
            dao.deleteEvent(event);
            Study study = studyService.getStudy(event.getAppId(), event.getStudyId(), true);
            Schedule2 schedule = scheduleService.getScheduleForStudy(study.getAppId(), study).orElse(null);
            if (schedule != null) {
                deleteStudyBurstEvents(schedule, event);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("User " + event.getUserId() + " failed to delete study event: " + event.getEventId());
            }
            if (showError) {
                throw new BadRequestException("Study event failed to delete: " + event.getEventId() + ".");    
            }
        }
    }
    
    /**
     * Publish an event. If the event is being created for the first time and it is the originating (triggering)
     * event for a study burst, the set of study burst events will be created. Editing of both the event
     * and any study burst events is thereafter governed by the update type of the event and study bursts. If 
     * both are mutable or future_only, then updating the originating event will update all the study burst 
     * events, unless the <code>updateBursts</code> flag is set to false.
     *  
     * @param event
     *      the event to publish
     * @param showError
     *      if false (the default), this method returns quietly regardless of the outcome of publishing the event.
     *      If true, it will throw a BadRequestException if any event cannot be published.
     * @param updateBursts
     *      if true (the default), this method will update study burst events based on their update type. If false, 
     *      study burst events will not be updated if the update would be an edit of existing events (if the origin 
     *      event is being published for the first time, then this flag has to be ignored so the study bursts are 
     *      published).
     */
    public void publishEvent(StudyActivityEvent event, boolean showError, boolean updateBursts) {
        checkNotNull(event);

        event.setCreatedOn(getCreatedOn());
        
        Validate.entityThrowingException(CREATE_INSTANCE, event);
        
        StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                event.getUserId(), event.getStudyId(), event.getEventId());
        
        // we will honor this, unless the origin event has never been published, then it *has* to
        // trigger creating of the study bursts.
        if (mostRecent == null) {
            updateBursts = true;
        }
        // Throwing exceptions will prevent study burst updates from happening if 
        // an error occurs in earlier order...so we collect errors and only show 
        // them at the end if we want to throw an exception.
        List<String> failedEventIds = new ArrayList<>();
        if (event.getUpdateType().canUpdate(mostRecent, event)) {
            dao.publishEvent(event);
        } else {
            failedEventIds.add(event.getEventId());
        }
        if (updateBursts) {
            Study study = studyService.getStudy(event.getAppId(), event.getStudyId(), true);
            Schedule2 schedule = scheduleService.getScheduleForStudy(study.getAppId(), study).orElse(null);
            if (schedule != null) {
                createStudyBurstEvents(schedule, event, failedEventIds);
            }
        }
        if (!failedEventIds.isEmpty()) {
            String eventNames = COMMA_SPACE_JOINER.join(failedEventIds);
            if (LOG.isDebugEnabled()) {
                LOG.debug("User " + event.getUserId() + " failed to publish study event(s): " + eventNames);    
            }
            if (showError) {
                throw new BadRequestException("Study event(s) failed to publish: " + eventNames + ".");
            }
        }
    }
    
    /**
     * Get a complete set of all events for this user, where the timestamp of each entry is the most recent timestamp
     * as determined by the <code>createdOn</code> value of the record (in other words, the time of creation as measured
     * by the server, and not the client or the time of the event).
     * 
     * @param appId
     *      the appId of the study
     * @param studyId
     *      the study in which these events have occurred
     * @param userId
     *      the ID of the participant account
     * @return
     *      a complete set of event records for this user, including the most recent record for each event     
     */
    public ResourceList<StudyActivityEvent> getRecentStudyActivityEvents(String appId, String studyId, String userId) {
        checkNotNull(userId);
        checkNotNull(studyId);

        Account account = accountService.getAccount(AccountId.forId(appId, userId))
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        List<StudyActivityEvent> events = dao.getRecentStudyActivityEvents(userId, studyId);
        addEnrollmentIfMissing(account, events, studyId);
        
        // There are some global events related to authentication and account creation that 
        // are useful when working with study-specific events, so add these from the global
        // system.
        Map<String, DateTime> map = activityEventService.getActivityEventMap(appId, account.getHealthCode());
        for (String fieldName : GLOBAL_EVENTS_OF_INTEREST) {
            addIfPresent(events, map, fieldName);    
        }
        return new ResourceList<>(events); 
    }
    
    /**
     * Get a paginated list of all timestamp values for a specific event ID. This method should return a 
     * value for any event that can be found in the map of recent events, including immutable and app-scoped
     * events of interest to the study-scoped APIs (created_on, enrollment, and install_link_sent), though 
     * these will only be one record.
     */
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(
            AccountId accountId, String studyId, String eventId, Integer offsetBy, Integer pageSize) {
        
        if (eventId == null) {
            throw new BadRequestException("Event ID is required");
        }
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        StudyActivityEventIdsMap eventMap = studyService.getStudyActivityEventIdsMap(accountId.getAppId(), studyId);

        String adjEventId = formatActivityEventId(eventMap, eventId);
        if (adjEventId == null) {
            throw new BadRequestException("“" + eventId + "” is not a valid event ID");
        }
        
        // Global events emulate history for a cleaner and less confusing API, but there 
        // will only ever be one value.
        if (GLOBAL_EVENTS_OF_INTEREST.contains(adjEventId)) {
            Map<String, DateTime> map = activityEventService.getActivityEventMap(
                    account.getAppId(), account.getHealthCode());
            List<StudyActivityEvent> events = new ArrayList<>();
            addIfPresent(events, map, adjEventId);
            
            return new PagedResourceList<>(events, 1, true)
                    .withRequestParam(ResourceList.OFFSET_BY, offsetBy)
                    .withRequestParam(ResourceList.PAGE_SIZE, pageSize);    
        }
        
        PagedResourceList<StudyActivityEvent> results = dao.getStudyActivityEventHistory(
                account.getId(), studyId, adjEventId, offsetBy, pageSize);
        
        if (adjEventId.equals(ENROLLMENT_FIELD) && results.getItems().size() == 0) {
            Enrollment en = getElement(account.getEnrollments(), Enrollment::getStudyId, studyId).orElse(null);
            if (en != null) {
                StudyActivityEvent event = new StudyActivityEvent.Builder()
                        .withEventId(ENROLLMENT_FIELD)
                        .withTimestamp(en.getEnrolledOn())
                        .withRecordCount(1).build();
                results = new PagedResourceList<>(ImmutableList.of(event), 1, true);
            }
        }
        return results.withRequestParam(ResourceList.OFFSET_BY, offsetBy)
            .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
    }
    
    private void addIfPresent(List<StudyActivityEvent> events, Map<String, DateTime> map, String field) {
        if (map.containsKey(field)) {
            DateTime ts = map.get(field);
            StudyActivityEvent event = new StudyActivityEvent.Builder()
                    .withEventId(field)
                    .withTimestamp(ts)
                    // the app-scoped events did not have a createdOn date. Copy ts.
                    .withCreatedOn(ts)
                    .withRecordCount(1).build();
            events.add(event);
        }
    }
    
    /**
     * If the triggering event is mutable, study burst events can be created as well. Any errors
     * that occur are collected in the list of failedEventIds. 
     */
    private void createStudyBurstEvents(Schedule2 schedule, StudyActivityEvent event, List<String> failedEventIds) {
        String eventId = event.getEventId();
        
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder()
            .withAppId(event.getAppId())
            .withUserId(event.getUserId())
            .withStudyId(event.getStudyId())
            .withClientTimeZone(event.getClientTimeZone())
            .withCreatedOn(event.getCreatedOn())
            .withObjectType(STUDY_BURST);
        
        for(StudyBurst burst : schedule.getStudyBursts()) {
            if (burst.getOriginEventId().equals(eventId)) {
                builder.withUpdateType(burst.getUpdateType());
                builder.withStudyBurstId(burst.getIdentifier());
                builder.withOriginEventId(burst.getOriginEventId());
                
                Period period = burst.getInterval();
                int len =  burst.getOccurrences().intValue();
                for (int i=0; i < len; i++) {
                    String iteration = Strings.padStart(Integer.toString(i+1), 2, '0');
                    DateTime eventTime = new DateTime(event.getTimestamp()).plus(period);

                    StudyActivityEvent burstEvent = builder
                            .withEventId(null)
                            .withObjectId(burst.getIdentifier())
                            .withAnswerValue(iteration)
                            .withTimestamp(eventTime)
                            .withPeriodFromOrigin(period)
                            .build();
                    
                    // now advance period for the next loop, if there is one.
                    period = period.plus(burst.getInterval());
                    
                    StudyActivityEvent mostRecent = dao.getRecentStudyActivityEvent(
                            burstEvent.getUserId(), burstEvent.getStudyId(), burstEvent.getEventId());

                    // Study bursts also have an update type that must be respected.
                    if (burst.getUpdateType().canUpdate(mostRecent, burstEvent)) {
                        dao.publishEvent(burstEvent);    
                    }  else {
                        failedEventIds.add(burstEvent.getEventId());
                    } 
                }
            }
        }
    }
    
    private void deleteStudyBurstEvents(Schedule2 schedule, StudyActivityEvent event) {
        String eventId = event.getEventId();
        
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder()
            .withAppId(event.getAppId())
            .withUserId(event.getUserId())
            .withStudyId(event.getStudyId())
            .withObjectType(STUDY_BURST);
        
        for(StudyBurst burst : schedule.getStudyBursts()) {
            if (burst.getOriginEventId().equals(eventId)) {
                int len =  burst.getOccurrences().intValue();
                
                for (int i=0; i < len; i++) {
                    String iteration = Strings.padStart(Integer.toString(i+1), 2, '0');

                    StudyActivityEvent burstEvent = builder
                            .withEventId(null)
                            .withObjectId(burst.getIdentifier())
                            .withAnswerValue(iteration)
                            .build();
                    
                    dao.deleteEvent(burstEvent);
                }
            }
        }
    }
    
    /**
     * If events do not include enrollment, you can include it. This provides some migration support.
     */
    private void addEnrollmentIfMissing(Account account, List<StudyActivityEvent> events, String studyId) {
        for (StudyActivityEvent oneEvent : events) {
            if (oneEvent.getEventId().equals(ENROLLMENT_FIELD)) {
                return;
            }
        }
        Enrollment en = getElement(account.getEnrollments(), Enrollment::getStudyId, studyId).orElse(null);
        if (en != null) {
            StudyActivityEvent event = new StudyActivityEvent.Builder()
                    .withEventId(ENROLLMENT_FIELD)
                    .withTimestamp(en.getEnrolledOn())
                    .withRecordCount(1)
                    .build();
            events.add(event);
        }
    }
}