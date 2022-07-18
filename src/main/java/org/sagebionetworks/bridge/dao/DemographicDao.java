package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    void saveDemographicUser(DemographicUser demographicUser);

    void deleteDemographic(String appId, String studyId, String userId, String categoryName);

    void deleteDemographicUser(String appId, String studyId, String userId);

    Demographic getDemographic(String appId, String studyId, String userId, String categoryName);

    DemographicUser getDemographicUser(String appId, String studyId, String userId);

    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
