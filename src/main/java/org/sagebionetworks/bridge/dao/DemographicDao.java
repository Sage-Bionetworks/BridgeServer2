package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    void saveDemographic(Demographic demographic);

    void saveDemographics(DemographicUser demographicUser);

    void deleteDemographic(String studyId, String userId, String categoryName);

    DemographicUser getDemographicUser(String appId, String studyId, String userId);

    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
