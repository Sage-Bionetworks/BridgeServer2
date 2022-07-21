package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
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

    public void saveDemographicUser(DemographicUser demographicUser) {
        String existingDemographicUserId = demographicDao.getDemographicUserId(demographicUser.getAppId(),
                demographicUser.getStudyId(), demographicUser.getUserId());
        if (existingDemographicUserId == null) {
            demographicUser.setId(generateGuid());
        } else {
            demographicUser.setId(existingDemographicUserId);
        }
        Validate.entityThrowingException(DemographicUserValidator.INSTANCE, demographicUser);
        demographicDao.saveDemographicUser(demographicUser);
    }

    public void deleteDemographic(String appId, String studyId, String userId, String categoryName) {
        demographicDao.deleteDemographic(appId, studyId, userId, categoryName);
    }

    public void deleteDemographicUser(String appId, String studyId, String userId) {
        demographicDao.deleteDemographicUser(appId, studyId, userId);
    }

    public DemographicUser getDemographicUser(String appId, String studyId, String userId) throws BadRequestException {
        return demographicDao.getDemographicUser(appId, studyId, userId);
    }

    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return demographicDao.getDemographicUsers(appId, studyId, offsetBy, pageSize);
    }

    private String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
