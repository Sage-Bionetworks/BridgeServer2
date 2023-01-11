package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;

public interface DemographicValidationDao {
    public DemographicValuesValidationConfig saveDemographicValuesValidationConfig(
            DemographicValuesValidationConfig validationConfig);

    public Optional<DemographicValuesValidationConfig> getDemographicValuesValidationConfig(String appId,
            String studyId, String categoryName);

    public void deleteDemographicValuesValidationConfig(String appId, String studyId, String categoryName)
            throws EntityNotFoundException;

    public void deleteAllValidationConfigs(String appId, String studyId);
}
