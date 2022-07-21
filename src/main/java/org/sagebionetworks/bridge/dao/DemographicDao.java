package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    DemographicUser saveDemographicUser(DemographicUser demographicUser);

    void deleteDemographic(String demographicId);

    void deleteDemographicUser(String appId, String studyId, String userId);

    Demographic getDemographic(String demographicId);
    
    String getDemographicUserId(String appId, String studyId, String userId);

    DemographicUser getDemographicUser(String appId, String studyId, String userId);

    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
