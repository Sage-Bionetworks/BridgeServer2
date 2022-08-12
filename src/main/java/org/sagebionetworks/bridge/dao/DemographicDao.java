package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    DemographicUser saveDemographicUser(DemographicUser demographicUser, String appId, String studyId, String userId);

    void deleteDemographic(String demographicId);

    void deleteDemographicUser(String demographicUserId);

    Optional<Demographic> getDemographic(String demographicId);

    Optional<String> getDemographicUserId(String appId, String studyId, String userId);

    Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId);

    PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy, int pageSize);
}
