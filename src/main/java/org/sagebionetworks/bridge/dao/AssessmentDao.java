package org.sagebionetworks.bridge.dao;

import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;

public interface AssessmentDao {
    static final String APP_ID = "appId";
    static final String IDENTIFIER = "identifier";
    static final String REVISION = "revision";
    static final String GUID = "guid";
    
    PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, 
            int pageSize, Set<String> categories, Set<String> tags, boolean includeDeleted);

    PagedResourceList<Assessment> getAssessmentRevisions(
            String appId, String identifier, int offsetBy, int pageSize, boolean includeDeleted);
    
    Optional<Assessment> getAssessment(String appId, String guid);
    
    Optional<Assessment> getAssessment(String appId, String identifier, int revision);
    
    Assessment createAssessment(Assessment assessment);
    
    Assessment updateAssessment(Assessment assessment);
    
    /**
     * Publication changes two objects at the same time and requires a transaction.
     * Returns the original assessment updated to reflect that it is now derived from the 
     * shared assessment.
     */
    Assessment publishAssessment(Assessment original, Assessment assessmentToPublish);
    
    void deleteAssessment(Assessment assessment);
}