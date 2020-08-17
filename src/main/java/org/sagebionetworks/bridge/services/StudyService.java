package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.AuthUtils.checkOrgMembership;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class StudyService {
    
    private StudyDao studyDao;
    
    private OrganizationDao organizationDao;
    
    @Autowired
    final void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    
    @Autowired
    final void setOrganizationDao(OrganizationDao organizationDao) {
        this.organizationDao = organizationDao;
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
        return getStudies(appId, false).stream()
                .map(Study::getId).collect(BridgeCollectors.toImmutableSet());
    }
    
    public List<Study> getStudies(String appId, boolean includeDeleted) {
        checkNotNull(appId);
        
        return studyDao.getStudies(appId, includeDeleted);
    }
    
    public VersionHolder createStudy(String appId, String orgId, Study study) {
        checkNotNull(appId);
        checkNotNull(study);
        
        if (isBlank(orgId)) {
            throw new BadRequestException("Sponsor orgId is required.");
        } else {
            checkOrgMembership(orgId);
        }
        study.setAppId(appId);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
        
        study.setVersion(null);
        study.setDeleted(false);
        DateTime timestamp = DateTime.now();
        study.setCreatedOn(timestamp);
        study.setModifiedOn(timestamp);
        
        Study existing = studyDao.getStudy(appId, study.getId());
        if (existing != null) {
            throw new EntityAlreadyExistsException(Study.class,
                    ImmutableMap.of("id", existing.getId()));
        }
        // I wanted to change the error message because "Organization not found" in this context
        // seems confusing to end users.
        organizationDao.getOrganization(appId, orgId)
            .orElseThrow(() -> new BadRequestException("Sponsoring organization not found."));

        return studyDao.createStudy(study, orgId);
    }

    public VersionHolder updateStudy(String appId, Study study) {
        checkNotNull(appId);
        checkNotNull(study);

        study.setAppId(appId);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
        
        Study existing = getStudy(appId, study.getId(), true);
        if (study.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Study.class);
        }
        study.setCreatedOn(existing.getCreatedOn());
        study.setModifiedOn(DateTime.now());
        
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
}
