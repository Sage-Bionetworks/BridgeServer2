package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_ORG_SPONSORED_STUDIES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_TRANSITION_STUDY;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.ALLOWED_PHASE_TRANSITIONS;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.ANALYSIS;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.CAN_DELETE_STUDY;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.CAN_EDIT_STUDY_CORE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.CAN_EDIT_STUDY_METADATA;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.COMPLETED;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.IN_FLIGHT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.RECRUITMENT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.WITHDRAWN;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyPhase;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class StudyService {
    
    @Autowired
    private StudyDao studyDao;
    @Autowired
    private SponsorService sponsorService;
    @Autowired
    private CacheProvider cacheProvider;
    @Autowired
    private Schedule2Service scheduleService;
    @Autowired
    private AccountService accountService;
    
    protected String getDefaultTimeZoneId() { 
        return DateTimeZone.getDefault().getID();
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
    
    /**
     * Find the appropriate time zone for a specific participant. If clientTimeZoneId 
     * exists, that is returned. Otherwise, the study’s time zone is returned. If that
     * doesn’t exist, the system’s time zone is returned.
     */
    public String getZoneId(String appId, String studyId, String clientTimeZoneId) {
        if (clientTimeZoneId != null) {
            return clientTimeZoneId;
        } else {
            Study study = getStudy(appId, studyId, false);
            if (study != null && study.getStudyTimeZone() != null) {
                return study.getStudyTimeZone();
            }
        }
        return getDefaultTimeZoneId();
    }
    
    public void removeStudyEtags(String appId, String scheduleGuid) {
        checkNotNull(appId);
        checkNotNull(scheduleGuid);
        
        List<String> studyIds = studyDao.getStudyIdsUsingSchedule(appId, scheduleGuid);
        for (String studyId : studyIds) {
            CacheKey cacheKey = CacheKey.etag(Schedule2.class, appId, studyId);
            cacheProvider.removeObject(cacheKey);
        }
        studyDao.removeScheduleFromStudies(appId, scheduleGuid);
    }
    
    public void updateStudyEtags(String appId, String scheduleGuid, DateTime timestamp) {
        checkNotNull(appId);
        checkNotNull(scheduleGuid);
        checkNotNull(timestamp);
        
        List<String> studyIds = studyDao.getStudyIdsUsingSchedule(appId, scheduleGuid);
        for (String studyId : studyIds) {
            CacheKey cacheKey = CacheKey.etag(Schedule2.class, appId, studyId);
            cacheProvider.setObject(cacheKey, timestamp);
        }
    }
    
    public Study getStudy(String appId, String studyId, boolean throwsException) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study study = studyDao.getStudy(appId, studyId);
        if (throwsException && study == null) {
            throw new EntityNotFoundException(Study.class);
        }
        if (study != null) {
            CacheKey cacheKey = CacheKey.etag(Study.class, appId, studyId);
            cacheProvider.setObject(cacheKey, study.getModifiedOn());
        }
        return study;
    }
    
    /**
     * Get the list of active study IDs for this app (used to validate criteria 
     * objects throughout the system). Calling this method is preferred to getStudies() 
     * so we can provide a cache for these infrequently changing identifiers.
     */
    public Set<String> getStudyIds(String appId) {
        return getStudies(appId, null, null, false, false)
                .getItems().stream()
                .map(Study::getIdentifier)
                .collect(toSet());
    }

    /** Returns a list of all study IDs in the given app that use the given schedule. */
    public List<String> getStudyIdsUsingSchedule(String appId, String scheduleGuid) {
        checkNotNull(appId);
        checkNotNull(scheduleGuid);
        return studyDao.getStudyIdsUsingSchedule(appId, scheduleGuid);
    }

    public StudyActivityEventIdsMap getStudyActivityEventIdsMap(String appId, String studyId) {
        StudyActivityEventIdsMap map = new StudyActivityEventIdsMap();

        Study study = getStudy(appId, studyId, true);
        map.addCustomEvents(study.getCustomEvents());
        
        Schedule2 schedule = scheduleService.getScheduleForStudy(appId, studyId).orElse(null);
        if (schedule != null) {
            map.addStudyBursts(schedule.getStudyBursts());            
        }
        return map;
    }
    
    public PagedResourceList<Study> getStudies(String appId, Integer offsetBy, Integer pageSize, 
            boolean includeDeleted) {
        return getStudies(appId, offsetBy, pageSize, includeDeleted, true);
    }
    
    // We only scope studies when directly acting for them...getStudyIds() is sometimes used in ways
    // where we're detecting an existing study ID, and that needs to continue to work, even if you're
    // choosing someone else's studyId and you can't see it (it's a good argument for using GUIDs).
    private PagedResourceList<Study> getStudies(String appId, Integer offsetBy, Integer pageSize,
            boolean includeDeleted, boolean scopeStudies) {
        checkNotNull(appId);
        
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        Set<String> studies = null;
        if (scopeStudies && CAN_READ_ORG_SPONSORED_STUDIES.check()) {
            studies = RequestContext.get().getOrgSponsoredStudies();
        }
        return studyDao.getStudies(appId, studies, offsetBy, pageSize, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    public VersionHolder createStudy(String appId, Study study, boolean setStudySponsor) {
        checkNotNull(appId);
        checkNotNull(study);
        
        study.setAppId(appId);
        study.setPhase(DESIGN);
        
        String orgId = RequestContext.get().getCallerOrgMembership();
        
        StudyValidator validator = new StudyValidator(getCustomEventIdsFromSchedule(appId, study.getScheduleGuid()),
                scheduleService, sponsorService);
        Validate.entityThrowingException(validator, study);
        
        study.setVersion(0);
        study.setDeleted(false);
        DateTime timestamp = getDateTime();
        study.setCreatedOn(timestamp);
        study.setModifiedOn(timestamp);
        
        Study existing = studyDao.getStudy(appId, study.getIdentifier());
        if (existing != null) {
            throw new EntityAlreadyExistsException(Study.class, ImmutableMap.of("id", existing.getIdentifier()));
        }
        VersionHolder version = studyDao.createStudy(study);
        // You cannot do this when creating an app because it will fail: the caller's organization will not 
        // yet exist. After initial app creation when accounts are established in the app, it should be 
        // possible to create studies that are associated to the caller's organization (so the study 
        // creator can access the study!).
        if (setStudySponsor && orgId != null) {
            sponsorService.createStudyWithSponsorship(appId, study.getIdentifier(), orgId);    
        }
        CacheKey cacheKey = CacheKey.etag(Study.class, appId, study.getIdentifier());
        cacheProvider.setObject(cacheKey, study.getModifiedOn());

        return version;
    }

    public VersionHolder updateStudy(String appId, Study study) {
        checkNotNull(appId);
        checkNotNull(study);

        Study existing = getStudy(appId, study.getIdentifier(), true);
        if (study.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Study.class);
        }
        if (!CAN_EDIT_STUDY_METADATA.contains(existing.getPhase())) {
            throw new BadRequestException("Study cannot be changed during phase " 
                    + existing.getPhase().label() + ".");
        }
        if (!CAN_EDIT_STUDY_CORE.contains(existing.getPhase())) {
            study.setScheduleGuid(existing.getScheduleGuid());
            study.setCustomEvents(existing.getCustomEvents());
            study.setStudyStartEventId(existing.getStudyStartEventId());
        } else if (existing.getScheduleGuid() != null) {
            study.setScheduleGuid(existing.getScheduleGuid());
        }
        study.setAppId(appId);
        study.setCreatedOn(existing.getCreatedOn());
        study.setModifiedOn(getDateTime());
        study.setPhase(existing.getPhase());

        StudyValidator validator = new StudyValidator(getCustomEventIdsFromSchedule(appId, study.getScheduleGuid()),
                scheduleService, sponsorService);
        Validate.entityThrowingException(validator, study);
        
        VersionHolder keys = studyDao.updateStudy(study);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, study.getIdentifier());
        cacheProvider.removeObject(cacheKey);
        
        cacheKey = CacheKey.etag(Study.class, appId, study.getIdentifier());
        cacheProvider.setObject(cacheKey, study.getModifiedOn());
        
        return keys;
    }
    
    public void deleteStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study existing = getStudy(appId, studyId, true);
        
        RequestContext context = RequestContext.get();
        if (!CAN_DELETE_STUDY.contains(existing.getPhase()) && !context.isInRole(ADMIN)) {
            throw new BadRequestException("Study cannot be deleted during phase " 
                    + existing.getPhase().label());
        }
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        studyDao.updateStudy(existing);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
        cacheProvider.removeObject(cacheKey);
        
        cacheKey = CacheKey.etag(Study.class, appId, studyId);
        cacheProvider.removeObject(cacheKey);
    }
    
    public void deleteStudyPermanently(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study existing = getStudy(appId, studyId, true);
        
        RequestContext context = RequestContext.get();
        if (!CAN_DELETE_STUDY.contains(existing.getPhase()) && !context.isInRole(ADMIN)) {
            throw new BadRequestException("Study cannot be deleted during phase " 
                    + existing.getPhase().label());
        }
        String scheduleGuid = existing.getScheduleGuid();
        
        studyDao.deleteStudyPermanently(appId, studyId);
        if (scheduleGuid != null) {
            scheduleService.deleteSchedulePermanently(appId, scheduleGuid);    
        }
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
        cacheProvider.removeObject(cacheKey);

        cacheKey = CacheKey.etag(Study.class, appId, studyId);
        cacheProvider.removeObject(cacheKey);
    }
    
    public void deleteAllStudies(String appId) {
        checkNotNull(appId);

        studyDao.deleteAllStudies(appId);
    }
    
    public Study transitionToDesign(String appId, String studyId) {
        return phaseTransition(appId, studyId, DESIGN, null);
    }
    
    /**
     * Move a study from the design phase into recruitment for the study. The study 
     * is now “live,” and the schedule (if one is assigned) is published and cannot 
     * be changed further, nor can the study be associated to another schedule 
     * (or no schedule).
     */
    public Study transitionToRecruitment(String appId, String studyId) {
        return phaseTransition(appId, studyId, RECRUITMENT, (study) -> {
            accountService.deleteAllPreviewAccounts(appId, studyId);
            if (study.getScheduleGuid() != null) {
                Schedule2 schedule = scheduleService.getSchedule(appId, study.getScheduleGuid());
                if (!schedule.isPublished()) {
                    scheduleService.publishSchedule(appId, study.getScheduleGuid());
                }
            }
        });
    }
    
    public Study transitionToInFlight(String appId, String studyId) {
        return phaseTransition(appId, studyId, IN_FLIGHT, null);
    }
    
    /**
     * Prevent any additional enrollments into this study, in preparation to move 
     * toward the analysis of study data collected. There may still be participants
     * completing their schedules at this time.
     */
    public Study transitionToAnalysis(String appId, String studyId) {
        return phaseTransition(appId, studyId, ANALYSIS, null);
    }
    
    /**
     * This effects a logical deletion of the study, but the meaning is open to 
     * other data changes to represent a study being closed.   
     */
    public Study transitionToCompleted(String appId, String studyId) {
        return phaseTransition(appId, studyId, COMPLETED, null);
    }
    
    /**
     * Moves a study to the withdrawn state. No further enrollments will be allowed,
     * and data collected for this study should not be uploaded as part of the data
     * set. If this was previously in design, the schedule will be published in order
     * to freeze it from further changes.
     */
    public Study transitionToWithdrawn(String appId, String studyId) {
        return phaseTransition(appId, studyId, WITHDRAWN, (study) -> {
            if (study.getScheduleGuid() != null) {
                Schedule2 schedule = scheduleService.getSchedule(appId, study.getScheduleGuid());
                if (!schedule.isPublished()) {
                    scheduleService.publishSchedule(appId, study.getScheduleGuid());
                }
            }
        });
    }
    
    /**
     * Simple phase transition with no other business logic.
     */
    private Study phaseTransition(String appId, String studyId, StudyPhase targetPhase, Consumer<Study> consumer) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(targetPhase);

        Study study = studyDao.getStudy(appId, studyId);
        if (study == null) {
            throw new EntityNotFoundException(Study.class);
        }
        CAN_TRANSITION_STUDY.checkAndThrow(STUDY_ID, study.getIdentifier());
        
        Set<StudyPhase> allowedTargetPhases = ALLOWED_PHASE_TRANSITIONS.get(study.getPhase());
        if (!allowedTargetPhases.contains(targetPhase)) {
            throw new BadRequestException("Study cannot transition from " + 
                    study.getPhase().label() + " to " + targetPhase.label() + ".");
        }
        if (consumer != null) {
            consumer.accept(study);
        }
        study.setPhase(targetPhase);
        study.setModifiedOn(getDateTime());
        studyDao.updateStudy(study);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
        cacheProvider.removeObject(cacheKey);
        
        cacheKey = CacheKey.etag(Study.class, appId, studyId);
        cacheProvider.setObject(cacheKey, study.getModifiedOn());

        return study;
    }
    
    /**
     * Retrieves the custom event IDs from a schedule. The returned event IDs will 
     * have the "custom:" prefix removed. If scheduleGuid is null, will return
     * an empty set.
     */
    protected Set<String> getCustomEventIdsFromSchedule(String appId, String scheduleGuid) {
        Set<String> existingEventIds = new HashSet<>();
        if (scheduleGuid != null) {
            Schedule2 schedule = scheduleService.getSchedule(appId, scheduleGuid);
            existingEventIds = schedule.getSessions().stream()
                    .flatMap(session -> session.getStartEventIds().stream())
                    .filter(s -> s.startsWith("custom:"))
                    .map(s -> s.substring(7))
                    .collect(Collectors.toSet());
        }
        return existingEventIds;
    }
}
