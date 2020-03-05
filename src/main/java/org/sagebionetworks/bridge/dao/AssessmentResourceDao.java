package org.sagebionetworks.bridge.dao;

import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

public interface AssessmentResourceDao {

    PagedResourceList<AssessmentResource> getResources(String assessmentId, Integer offsetBy, Integer pageSize,
            Set<ResourceCategory> categories, Integer minRevision, Integer maxRevision, boolean includeDeleted);
    
    Optional<AssessmentResource> getResource(String assessmentId, String guid);

    AssessmentResource saveResource(String assessmentId, AssessmentResource resource);

    void deleteResource(String assessmentId, AssessmentResource resource);
}
