package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;

public interface DemographicDao {
    void saveDemographic(Demographic demographic);
    void saveDemographics(DemographicUser demographics);
    void deleteDemographic(String studyId, String userId, String categoryName);
    // userId and categoryName can be null
    DemographicUser getDemographics(String studyId, String userId, String categoryName);
}
