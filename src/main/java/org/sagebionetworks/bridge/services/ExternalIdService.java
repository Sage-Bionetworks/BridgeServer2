package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;

import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.validators.ExternalIdValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

/**
 * Service for managing external IDs. These methods can be called whether or not strict validation of IDs is enabled. 
 * If it's enabled, reservation and assignment will work as expected, otherwise these silently do nothing. The external 
 * ID will be associated via the Enrollment collection, thus assignment of an external ID associates an account 
 * to a study (and removing an external ID removes that assignment).
 */
@Component
public class ExternalIdService {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private ExternalIdDao externalIdDao;
    
    private StudyService studyService;

    @Autowired
    public final void setExternalIdDao(ExternalIdDao externalIdDao) {
        this.externalIdDao = externalIdDao;
    }
    
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    public Optional<ExternalIdentifier> getExternalId(String appId, String externalId) {
        checkNotNull(appId);
        
        if (StringUtils.isBlank(externalId)) {
            return Optional.empty();
        }
        return externalIdDao.getExternalId(appId, externalId);
    }
    
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(
            String offsetKey, Integer pageSize, String idFilter, Boolean assignmentFilter) {
        
        if (pageSize == null) {
            pageSize = BridgeConstants.API_DEFAULT_PAGE_SIZE;
        }
        // Unlike other APIs that only accept API_MINIMUM_PAGE_SIZE, we have client use cases 
        // where we just want to retrieve one external ID.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }

        String appId = BridgeUtils.getRequestContext().getCallerAppId();
        return externalIdDao.getExternalIds(appId, offsetKey, pageSize, idFilter, assignmentFilter);
    }
    
    public void createExternalId(ExternalIdentifier externalId, boolean isV3) {
        checkNotNull(externalId);
        
        String appId = BridgeUtils.getRequestContext().getCallerAppId();
        externalId.setAppId(appId);
        
        // In this one  case, we can default the value for the caller and avoid an error. Any other situation
        // is going to generate a validation error
        Set<String> callerStudyIds = BridgeUtils.getRequestContext().getOrgSponsoredStudies();
        if (externalId.getStudyId() == null && callerStudyIds.size() == 1) {
            externalId.setStudyId( Iterables.getFirst(callerStudyIds, null) );
        }
        
        ExternalIdValidator validator = new ExternalIdValidator(studyService, isV3);
        Validate.entityThrowingException(validator, externalId);
        
        // Note that this external ID must be unique across the whole app, not just a study, or else
        // it cannot be used to identify the study, and that's a significant purpose behind the 
        // association of the two
        if (externalIdDao.getExternalId(appId, externalId.getIdentifier()).isPresent()) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", externalId.getIdentifier());
        }
        externalIdDao.createExternalId(externalId);
    }
    
    public void deleteExternalIdPermanently(App app, ExternalIdentifier externalId) {
        checkNotNull(app);
        checkNotNull(externalId);
        
        ExternalIdentifier existing = externalIdDao.getExternalId(app.getIdentifier(), externalId.getIdentifier())
                .orElseThrow(() -> new EntityNotFoundException(ExternalIdentifier.class));
        if (BridgeUtils.filterForStudy(existing) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        externalIdDao.deleteExternalId(externalId);
    }
    
    /**
     * There is a substantial amount of set-up that must occur before this call can be 
     * made, and the associated account record must be updated as well. See 
     * ParticipantService.beginAssignExternalId() which performs this setup, and is 
     * always called before the participant service calls this method. This method is 
     * not simply a method to update an external ID record.
     */
    public void commitAssignExternalId(ExternalIdentifier externalId) {
        if (externalId != null) {
            externalIdDao.commitAssignExternalId(externalId);    
        }
    }
    
    public void unassignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkNotNull(account.getAppId());
        checkNotNull(account.getHealthCode());
        
        if (externalId != null) {
            externalIdDao.unassignExternalId(account, externalId);
        }
    }
    
}
