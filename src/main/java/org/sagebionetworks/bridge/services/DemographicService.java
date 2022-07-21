package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.validators.DemographicUserValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DemographicService {
    private DemographicDao demographicDao;

    @Autowired
    public final void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    public DemographicUser saveDemographicUser(DemographicUser demographicUser) {
        DemographicUser existingDemographicUser = demographicDao.getDemographicUser(demographicUser.getAppId(),
                demographicUser.getStudyId(), demographicUser.getUserId());
        if (existingDemographicUser == null) {
            demographicUser.setId(generateGuid());
        } else {
            existingDemographicUser.getDemographics().clear();
            demographicDao.saveDemographicUser(existingDemographicUser);
            demographicUser.setId(existingDemographicUser.getId());
        }
        if (demographicUser.getDemographics() != null) {
            for (Demographic demographic : demographicUser.getDemographics().values()) {
                demographic.setId(generateGuid());
            }
        }
        Validate.entityThrowingException(DemographicUserValidator.INSTANCE, demographicUser);
        return demographicDao.saveDemographicUser(demographicUser);
    }

    public void deleteDemographic(String appId, String studyId, String userId, String demographicId)
            throws EntityNotFoundException {
        Demographic existingDemographic = demographicDao.getDemographic(demographicId);
        if (existingDemographic == null) {
            throw new EntityNotFoundException(Demographic.class);
        }
        if (!existingDemographic.getDemographicUser().getUserId().equals(userId)) {
            // user does not own this demographic
            // just give them a 404 because we don't want to expose the existence of another user's demographic data
            throw new EntityNotFoundException(Demographic.class);
        }
        demographicDao.deleteDemographic(demographicId);
    }

    public void deleteDemographicUser(String appId, String studyId, String userId) throws EntityNotFoundException {
        String existingDemographicUserId = demographicDao.getDemographicUserId(appId, studyId, userId);
        if (existingDemographicUserId == null) {
            throw new EntityNotFoundException(DemographicUser.class);
        }
        demographicDao.deleteDemographicUser(appId, studyId, userId);
    }

    public DemographicUser getDemographicUser(String appId, String studyId, String userId) throws EntityNotFoundException {
        DemographicUser existingDemographicUser = demographicDao.getDemographicUser(appId, studyId, userId);
        if (existingDemographicUser == null) {
            throw new EntityNotFoundException(DemographicUser.class);
        }
        return existingDemographicUser;
    }

    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) throws BadRequestException {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return demographicDao.getDemographicUsers(appId, studyId, offsetBy, pageSize);
    }

    private String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
