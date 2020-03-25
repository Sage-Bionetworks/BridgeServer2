package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

public interface AssessmentConfigDao {

    Optional<AssessmentConfig> getAssessmentConfig(String guid);
    
    AssessmentConfig updateAssessmentConfig(String guid, AssessmentConfig config);
    
    void deleteAssessmentConfig(String guid, AssessmentConfig config);
    
}
