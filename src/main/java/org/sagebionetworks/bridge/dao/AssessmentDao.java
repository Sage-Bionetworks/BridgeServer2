package org.sagebionetworks.bridge.dao;

import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;

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

    Assessment createAssessment(String appId, Assessment assessment, AssessmentConfig config);
    
    /**
     * This performs a merge of the assessment with persisted objects, including tags. 
     * It can be called to create or update an assessment.
     */
    Assessment updateAssessment(String appId, Assessment assessment);
    
    /**
     * Publish an assessment in an origin app context into the shared app context. If the assessment 
     * already exists under the same identifiers, the caller of this method must be a member of the 
     * owning organization of the published assessment.
     */
    Assessment publishAssessment(String originAppId, Assessment origin, Assessment dest, AssessmentConfig originConfig);

    /**
     * Copy an assessment from the shared context to a local context so it can be used. If the 
     * assessment already exists in the local context.
     */
    Assessment importAssessment(String destAppId, Assessment dest, AssessmentConfig destConfig);
    
    /**
     * This is an actual delete from the database.
     */
    void deleteAssessment(String appId, Assessment assessment);
}