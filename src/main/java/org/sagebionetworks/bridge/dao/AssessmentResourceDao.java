package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

public interface AssessmentResourceDao {

    PagedResourceList<AssessmentResource> getResources(String appId, String assessmentId, Integer offsetBy,
            Integer pageSize, Set<ResourceCategory> categories, Integer minRevision, Integer maxRevision,
            boolean includeDeleted);

    List<AssessmentResource> getAllResources(String appId, String assessmentId);

    Optional<AssessmentResource> getResource(String guid);

    AssessmentResource saveResource(String appId, String assessmentId, AssessmentResource resource);

    void deleteResource(AssessmentResource resource);
}
