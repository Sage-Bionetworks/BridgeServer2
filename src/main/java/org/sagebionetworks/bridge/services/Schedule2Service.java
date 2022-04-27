package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_CREATE_SCHEDULES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_SCHEDULES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_SCHEDULES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDIES;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.INSTANCE;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.HasGuid;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.participantschedules.ParticipantSchedule;
import org.sagebionetworks.bridge.models.schedules2.participantschedules.ParticipantScheduleGenerator;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge v2 schedules. These replace the older SchedulePlans and their APIs. The 
 * lifecycle of a schedule is now closely related to the one study it is referenced
 * by, so the Schedule2Service is always called by the study service and that 
 * service will prevent changes once a study is in production.
 */
@Component
public class Schedule2Service {
    private static final Logger LOG = LoggerFactory.getLogger(Schedule2Service.class);

    private OrganizationService organizationService;
    
    private StudyService studyService;
    
    private StudyActivityEventService studyActivityEventService;
    
    private Schedule2Dao dao;
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    @Autowired
    final void setScheduleDao(Schedule2Dao dao) {
        this.dao = dao;
    }
    
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    /**
     * Get all schedules in the system, paged, irrespective of their owning organizations. Schedules are primary
     * entities and can be manipulated independently of other entities in the system.
     */
    public PagedResourceList<Schedule2> getSchedules(String appId, int offsetBy, int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        // Cannot match on organization or study; this call has to be made by a developer or admin
        CAN_READ_SCHEDULES.checkAndThrow();
        
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getSchedules(appId, offsetBy, pageSize, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    /**
     * Get all the schedules for an organization, paged. Schedules are primary entities and can be 
     * manipulated independently of other entities in the system.
     */
    public PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, String ownerId, int offsetBy,
            int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        if (StringUtils.isBlank(ownerId)) {
            throw new BadRequestException("Caller is not a member of an organization");
        }
        CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, ownerId);

        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getSchedulesForOrganization(appId, ownerId, offsetBy, pageSize, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }

    /**
     * Get an individual schedule.
     */
    public Schedule2 getSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        Schedule2 schedule = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, schedule.getOwnerId());

        return schedule;
    }
    
    /**
     * Get the schedule assigned to a study. Access is only checked through 
     * enrollment in the study; administrative calls should be made through the
     * getSchedule() method of this service.
     */
    public Optional<Schedule2> getScheduleForStudy(String appId, Study study) {
        checkNotNull(appId);
        checkNotNull(study);
        
        if (study.getScheduleGuid() == null) {
            return Optional.empty();
        }
        Optional<Schedule2> optional = dao.getSchedule(appId, study.getScheduleGuid());

        if (optional.isPresent()) {
            CAN_READ_STUDIES.checkAndThrow(STUDY_ID, study.getIdentifier());    
        }
        return optional;
    }
    
    /**
     * Get the schedule assigned to a study. Access to the schedule is granted if the caller
     * can access the study.
     */
    public Optional<Schedule2> getScheduleForStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study study = studyService.getStudy(appId, studyId, false);
        if (study == null || study.getScheduleGuid() == null) {
            return Optional.empty();
        }
        Optional<Schedule2> optional = dao.getSchedule(appId, study.getScheduleGuid());

        if (optional.isPresent()) {
            CAN_READ_STUDIES.checkAndThrow(STUDY_ID, study.getIdentifier());    
        }
        return optional;
    }
    
    public Schedule2 createOrUpdateStudySchedule(Study study, Schedule2 schedule) {
        checkNotNull(study);
        checkNotNull(schedule);
        
        Schedule2 existing = null;
        if (study.getScheduleGuid() != null) {
            // this shouldn't come back null if it's set in the study, that would be strange.
            existing = dao.getSchedule(schedule.getAppId(), study.getScheduleGuid()).orElse(null);    
        }
        if (existing != null) {
            // This is an interesting bug that probably exists in the updateSchedule call and 
            // will need to be fixed. It's possible to submit the schedule for another study,
            // with keys and all, to the update API, and at some point we have to check that
            // we're talking about the same object.
            schedule.setGuid(study.getScheduleGuid());
            schedule = updateSchedule(study, existing, schedule);
            
            studyService.updateStudyEtags(study.getAppId(), schedule.getGuid(), schedule.getModifiedOn());

            return schedule;
        }
        schedule = createSchedule(study, schedule);
        study.setScheduleGuid(schedule.getGuid());
        studyService.updateStudy(schedule.getAppId(), study);
        
        studyService.updateStudyEtags(study.getAppId(), schedule.getGuid(), schedule.getModifiedOn());
        
        return schedule;
    }
    
    /**
     * Create a schedule. The schedule will be owned by the caller’s organization (unless
     * an admin or superadmin is making the call and they have specified an organization).
     */
    protected Schedule2 createSchedule(Study study, Schedule2 schedule) {
        checkNotNull(schedule);
        
        CAN_CREATE_SCHEDULES.checkAndThrow();
        
        String callerAppId = RequestContext.get().getCallerAppId();
        String callerOrgMembership = RequestContext.get().getCallerOrgMembership();
        boolean isAdmin = RequestContext.get().isInRole(ADMIN, SUPERADMIN);
        
        DateTime createdOn = getCreatedOn();
        schedule.setAppId(callerAppId);
        schedule.setGuid(generateGuid());
        schedule.setCreatedOn(createdOn);
        schedule.setModifiedOn(createdOn);
        schedule.setVersion(0L);
        schedule.setPublished(false);
        schedule.setDeleted(false);
        
        // If no id was provided, or the caller is not an administrator, than use the 
        // caller’s organization as the owning organization (schedule must have an 
        // owner, though admins can specify a different organization than their own).
        if (schedule.getOwnerId() == null || !isAdmin) {
            schedule.setOwnerId(callerOrgMembership);    
        }

        // Verify the owner ID (this is also caught by the database, but reports the 
        // error differently than we'd like). If there's no ownerId, don't do this because
        // it is shortly going to be a validation error
        if (schedule.getOwnerId() != null) {
            organizationService.getOrganization(schedule.getAppId(), schedule.getOwnerId());    
        }
        preValidationCleanup(study, schedule, (hasGuid) -> hasGuid.setGuid(generateGuid()));
        
        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.createSchedule(schedule);
    }
    
    /**
     * Update a schedule. Will throw an exception once the schedule is published. Ownership
     * cannot be changed once a schedule is created.
     */
    protected Schedule2 updateSchedule(Study study, Schedule2 existing, Schedule2 schedule) {
        checkNotNull(existing);
        checkNotNull(schedule);
        
        if (existing.isDeleted() && schedule.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        } 
        if (existing.isPublished()) {
            throw new PublishedEntityException(existing);
        }
        
        schedule.setCreatedOn(existing.getCreatedOn());
        schedule.setModifiedOn(getModifiedOn());
        schedule.setOwnerId(existing.getOwnerId());
        schedule.setPublished(false);
        preValidationCleanup(study, schedule, (hasGuid) -> {
          if (hasGuid.getGuid() == null) {
              hasGuid.setGuid(generateGuid());
          }
        });

        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.updateSchedule(schedule);
    }
    
    /**
     * Publish this schedule so it can no longer be modified. 
     */
    public Schedule2 publishSchedule(String appId, String guid) {
        
        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());    
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        if (existing.isPublished()) {
            throw new PublishedEntityException(existing);
        }
        existing.setPublished(true);
        existing.setModifiedOn(getModifiedOn());
        
        studyService.updateStudyEtags(appId, guid, existing.getModifiedOn());
        
        return dao.updateSchedule(existing);
    }
    
    /**
     * Logically delete this schedule. It is still available to callers who have a 
     * reference to the schedule.
     */
    protected void deleteSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        
        studyService.removeStudyEtags(appId, guid);
        
        dao.deleteSchedule(existing);
    }
    
    /**
     * Physically delete a schedule. Currently there is no protection against 
     * breaking the integrity of the system by permanently deleting a schedule; 
     * this method is for integration tests and admin cleanup.
     */
    public void deleteSchedulePermanently(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);

        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        
        studyService.removeStudyEtags(appId, guid);
        
        dao.deleteSchedulePermanently(existing);
    }
    
    public Timeline getTimelineForSchedule(String appId, String guid) {
        Schedule2 schedule = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        // This is calculated so quickly it is not worth caching
        return Scheduler.INSTANCE.calculateTimeline(schedule);
    }
    
    public List<TimelineMetadata> getScheduleMetadata(String guid) {
        checkNotNull(guid);

        return dao.getScheduleMetadata(guid);
    }
    
    public Optional<TimelineMetadata> getTimelineMetadata(String instanceGuid) {
        checkNotNull(instanceGuid);
        return dao.getTimelineMetadata(instanceGuid);
    }
    
    public List<TimelineMetadata> getSessionAssessmentMetadata(String instanceGuid) {
        checkNotNull(instanceGuid);
        return dao.getAssessmentsForSessionInstance(instanceGuid);
    }
    
    public void deleteAllSchedules(String appId) {
        checkNotNull(appId);
        
        dao.deleteAllSchedules(appId);
    }
    
    /**
     * Set GUIDs on objects that don't have them; clean up event keys or set
     * them to null if they're not valid, so they will fail validation.
     */
    void preValidationCleanup(Study study, Schedule2 schedule, Consumer<HasGuid> consumer) {
        checkNotNull(study);
        checkNotNull(schedule);

        StudyActivityEventIdsMap map = new StudyActivityEventIdsMap();
        map.addCustomEvents(study.getCustomEvents());
        map.addStudyBursts(schedule.getStudyBursts());

        for (Session session : schedule.getSessions()) {
            consumer.accept(session);
            session.setSchedule(schedule);
            for (TimeWindow window : session.getTimeWindows()) {
                consumer.accept(window);
            }
            List<String> events = session.getStartEventIds().stream()
                .map(s -> formatActivityEventId(map, s))
                .collect(toList());
            session.setStartEventIds(events);
        }
    }
    
    public ParticipantSchedule getParticipantSchedule(String appId, String studyId, Account account) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(account);
        
        Stopwatch watch = Stopwatch.createStarted();

        List<StudyActivityEvent> events = studyActivityEventService.getRecentStudyActivityEvents(
                account.getAppId(), studyId, account.getId()).getItems();

        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withEvents(events);
        builder.withNow(getCreatedOn());
        builder.withClientTimeZone(DateTimeZone.getDefault().getID());
        if (account.getClientTimeZone() != null) {
            builder.withClientTimeZone(account.getClientTimeZone());    
        }
        AdherenceState state = builder.build();
        
        ParticipantSchedule participantSchedule = null;
        Schedule2 schedule = getScheduleForStudy(appId, studyId)
                    .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        participantSchedule = ParticipantScheduleGenerator.INSTANCE.generate(state, timeline);

        watch.stop();
        LOG.info("Participant schedule took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return participantSchedule;
    }

}
