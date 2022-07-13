package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicCollection;

public interface DemographicDao {
    void saveDemographic(Demographic demographic);
    void saveDemographics(DemographicCollection demographics);
    void deleteDemographic(String studyId, String userId, String categoryName);
    // userId and categoryName can be null
    DemographicCollection getDemographics(String studyId, String userId, String categoryName);
}
