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
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.ANALYSIS;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.COMPLETED;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.IN_FLIGHT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.LEGACY;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.RECRUITMENT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.WITHDRAWN;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyPhase;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Component
public class StudyService {
    
    private static final Map<StudyPhase, Set<StudyPhase>> ALLOWED_PHASE_TRANSITIONS = new ImmutableMap.Builder<StudyPhase, Set<StudyPhase>>()
            .put(LEGACY, ImmutableSet.of(DESIGN))
            .put(DESIGN, ImmutableSet.of(RECRUITMENT, WITHDRAWN))
            .put(RECRUITMENT, ImmutableSet.of(IN_FLIGHT, WITHDRAWN))
            .put(IN_FLIGHT, ImmutableSet.of(RECRUITMENT, ANALYSIS, WITHDRAWN))
            .put(ANALYSIS, ImmutableSet.of(RECRUITMENT, IN_FLIGHT, COMPLETED, WITHDRAWN))
            .put(COMPLETED, ImmutableSet.of())
            .put(WITHDRAWN, ImmutableSet.of()).build();
    
    // Legacy studies, and studies created in the design phase, are fully editable/deletable, which was
    // their legacy behavior. In later phases, these no longer become possible. 
    public static final Set<StudyPhase> CAN_EDIT_METADATA = ImmutableSet.of(LEGACY, DESIGN, RECRUITMENT, IN_FLIGHT);
    public static final Set<StudyPhase> CAN_EDIT_CORE = ImmutableSet.of(LEGACY, DESIGN);
    public static final Set<StudyPhase> CAN_DELETE = ImmutableSet.of(LEGACY, DESIGN, COMPLETED, WITHDRAWN);
    
    private StudyDao studyDao;
    
    private SponsorService sponsorService;
    
    private CacheProvider cacheProvider;
    
    private Schedule2Service scheduleService;
    
    @Autowired
    final void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    
    @Autowired
    final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setSchedule2Service(Schedule2Service scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    public void removeScheduleFromStudies(String appId, String scheduleGuid) {
        checkNotNull(appId);
        checkNotNull(scheduleGuid);
        
        studyDao.removeScheduleFromStudies(appId, scheduleGuid);
    }
    
    public Study getStudy(String appId, String studyId, boolean throwsException) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study study = studyDao.getStudy(appId, studyId);
        if (throwsException && study == null) {
            throw new EntityNotFoundException(Study.class);
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
        
        study.setVersion(null);
        study.setDeleted(false);
        DateTime timestamp = DateTime.now();
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
        String orgId = RequestContext.get().getCallerOrgMembership();
        if (setStudySponsor && orgId != null) {
            sponsorService.createStudyWithSponsorship(appId, study.getIdentifier(), orgId);    
        }
        return version;
    }

    public VersionHolder updateStudy(String appId, Study study) {
        checkNotNull(appId);
        checkNotNull(study);

        Study existing = getStudy(appId, study.getIdentifier(), true);
        if (study.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Study.class);
        }
        if (!CAN_EDIT_METADATA.contains(existing.getPhase())) {
            throw new BadRequestException("Study cannot be changed during phase " 
                    + existing.getPhase().label() + ".");
        }
        if (!CAN_EDIT_CORE.contains(existing.getPhase())) {
            if(!Objects.equals(study.getScheduleGuid(), existing.getScheduleGuid())) {
                throw new BadRequestException("Study schedule cannot be changed or removed during phase " 
                        + existing.getPhase().label() + ".");
            }
            study.setCustomEvents(existing.getCustomEvents());
        }
        study.setAppId(appId);
        study.setCreatedOn(existing.getCreatedOn());
        study.setModifiedOn(DateTime.now());
        study.setPhase(existing.getPhase());
        
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
        
        VersionHolder keys = studyDao.updateStudy(study);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, study.getIdentifier());
        cacheProvider.removeObject(cacheKey);
        
        return keys;
    }
    
    public void deleteStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study existing = getStudy(appId, studyId, true);
        
        if (!CAN_DELETE.contains(existing.getPhase())) {
            throw new BadRequestException("Study cannot be deleted during phase " 
                    + existing.getPhase().label());
        }
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        studyDao.updateStudy(existing);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
        cacheProvider.removeObject(cacheKey);
    }
    
    public void deleteStudyPermanently(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        // Throws exception if the element does not exist.
        getStudy(appId, studyId, true);
        studyDao.deleteStudyPermanently(appId, studyId);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
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
     * set.  
     */
    public Study transitionToWithdrawn(String appId, String studyId) {
        return phaseTransition(appId, studyId, WITHDRAWN, null);
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
        study.setPhase(targetPhase);
        
        if (consumer != null) {
            consumer.accept(study);
        }
        studyDao.updateStudy(study);
        
        CacheKey cacheKey = CacheKey.publicStudy(appId, studyId);
        cacheProvider.removeObject(cacheKey);
        
        return study;
    }
}
