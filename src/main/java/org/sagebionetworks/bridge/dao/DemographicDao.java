package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    void saveDemographicUser(DemographicUser demographicUser);

    void deleteDemographic(String appId, String studyId, String userId, String categoryName);

    void deleteDemographicUser(String appId, String studyId, String userId);

    String getDemographicUserId(String appId, String studyId, String userId);

    DemographicUser getDemographicUser(String appId, String studyId, String userId) throws BadRequestException;

    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
