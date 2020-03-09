package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;

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
    Assessment saveAssessment(String appId, Assessment assessment);
    
    /**
     * Publish an assessment in an origin app context into the shared app context. All the resources 
     * associated to the assessment's ID will be logically deleted and replaced with the resources in 
     * the local context as well. If the assessment already exists under the same identifiers, the 
     * caller of this method must be a member of the owning organization of the published assessment.
     */
    Assessment publishAssessment(String originAppId, Assessment origin, Assessment dest,
            List<AssessmentResource> destResources);

    /**
     * Copy an assessment from the shared context to a local context so it can be used. If the 
     * assessment already exists in the local context, any resources associated to it will be logically
     * deleted and replaced with the resources from the shared context.
     */
    Assessment importAssessment(String destAppId, Assessment dest, List<AssessmentResource> destResources);
    
    /**
     * This is an actual delete from the database.
     */
    void deleteAssessment(String appId, Assessment assessment);
}