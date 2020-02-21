package org.sagebionetworks.bridge.dao;

import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;

public interface AssessmentDao {
    /**
     * The paged resource list returned from the DAO contains only the current page of results
     * and the total number of records. Request parameters should be added by the service.
     */
    PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, 
            int pageSize, Set<String> tags, boolean includeDeleted);

    /**
     * The paged resource list returned from the DAO contains only the current page of results
     * and the total number of records. Request parameters should be added by the service.
     */
    PagedResourceList<Assessment> getAssessmentRevisions(
            String appId, String identifier, int offsetBy, int pageSize, boolean includeDeleted);
    
    Optional<Assessment> getAssessment(String appId, String guid);
    
    Optional<Assessment> getAssessment(String appId, String identifier, int revision);
    
    /**
     * This performs a merge of the assessment with persisted objects, including tags. 
     * It can be called to create or update an assessment.
     */
    Assessment saveAssessment(Assessment assessment);
    
    /**
     * Publication changes two objects at the same time and requires a transaction. Method returns 
     * the original assessment updated to reflect that it is now derived from the shared assessment.
     */
    Assessment publishAssessment(Assessment original, Assessment assessmentToPublish);
    
    /**
     * This is an actual delete from the database.
     */
    void deleteAssessment(Assessment assessment);
}