package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_ORG_SPONSORED_STUDIES;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;

import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class StudyService {
    
    private StudyDao studyDao;
    
    private SponsorService sponsorService;
    
    private AppService appService;
    
    @Autowired
    final void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    
    @Autowired
    final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
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
        App app = appService.getApp(appId);
        study.setStudyStartEventId(formatActivityEventId(
                app.getCustomEvents().keySet(), study.getStudyStartEventId()));
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

        study.setAppId(appId);
        Study existing = getStudy(appId, study.getIdentifier(), true);
        if (study.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Study.class);
        }
        study.setCreatedOn(existing.getCreatedOn());
        study.setModifiedOn(DateTime.now());
        study.setPhase(existing.getPhase());
        App app = appService.getApp(appId);
        study.setStudyStartEventId(formatActivityEventId(
                app.getCustomEvents().keySet(), study.getStudyStartEventId()));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
        
        return studyDao.updateStudy(study);
    }
    
    public void deleteStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study existing = getStudy(appId, studyId, true);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        studyDao.updateStudy(existing);
    }
    
    public void deleteStudyPermanently(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        // Throws exception if the element does not exist.
        getStudy(appId, studyId, true);
        studyDao.deleteStudyPermanently(appId, studyId);
    }
    
    public void deleteAllStudies(String appId) {
        checkNotNull(appId);

        studyDao.deleteAllStudies(appId);
    }
}
