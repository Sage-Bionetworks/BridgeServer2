package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

public interface AssessmentConfigDao {

    Optional<AssessmentConfig> getAssessmentConfig(String guid);
    
    AssessmentConfig updateAssessmentConfig(String guid, Assessment assessment, AssessmentConfig config);
    
    AssessmentConfig customizeAssessmentConfig(String guid, AssessmentConfig config);
    
    void deleteAssessmentConfig(String guid, AssessmentConfig config);
}
