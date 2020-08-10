package org.sagebionetworks.bridge.hibernate;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeUtils.AND_JOINER;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentResourceDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceId;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

@Component
public class HibernateAssessmentResourceDao implements AssessmentResourceDao {
    static final String DELETE_QUERY = "DELETE FROM ExternalResources WHERE appId = :appId AND guid = :guid";
    static final String ALL_RESOURCES_QUERY = "from HibernateAssessmentResource WHERE appId = :appId " + 
            "AND assessmentId = :assessmentId AND deleted = 0";
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<AssessmentResource> getResources(String appId, String assessmentId, Integer offsetBy,
            Integer pageSize, Set<ResourceCategory> categories, Integer minRevision, Integer maxRevision,
            boolean includeDeleted) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.append("from HibernateAssessmentResource");
        List<String> clauses = new ArrayList<>();
        clauses.add("WHERE appId = :appId AND assessmentId = :assessmentId");
        builder.getParameters().put("assessmentId", assessmentId);
        builder.getParameters().put("appId", appId);
        if (maxRevision != null) {
            clauses.add("(minRevision is null OR minRevision <= :maxRevision)");
            builder.getParameters().put("maxRevision", maxRevision);
        }
        if (minRevision != null) {
            clauses.add("(maxRevision is null OR maxRevision >= :minRevision)");
            builder.getParameters().put("minRevision", minRevision);
        }
        if (!includeDeleted) {
            clauses.add("deleted = 0");
        }
        if (categories != null && !categories.isEmpty()) {
            clauses.add("category in :categories");
            builder.getParameters().put("categories", categories);
        }
        builder.append(AND_JOINER.join(clauses));
        builder.append("ORDER BY title ASC");
        
        int total = hibernateHelper.queryCount("SELECT COUNT(DISTINCT guid) " + builder.getQuery(), builder.getParameters());
        
        List<HibernateAssessmentResource> resources = hibernateHelper.queryGet(builder.getQuery(), 
                builder.getParameters(), offsetBy, pageSize, HibernateAssessmentResource.class);
        
        List<AssessmentResource> dtos = resources.stream().map(AssessmentResource::create).collect(toList());
        return new PagedResourceList<AssessmentResource>(dtos, total);
    }
    
    @Override
    public Optional<AssessmentResource> getResource(String appId, String guid) {
        AssessmentResourceId id = new AssessmentResourceId(appId, guid);
        HibernateAssessmentResource resource = hibernateHelper.getById(HibernateAssessmentResource.class, id);
        return (resource == null) ? Optional.empty() : Optional.of(AssessmentResource.create(resource));
    }

    @Override
    public AssessmentResource saveResource(String appId, String assessmentId, AssessmentResource resource) {
        // If you do not receive back the managed object from the executeWithExceptionHandling() method, and THEN
        // convert it to a non-managed object, the version will not be updated. It appears that the update of the 
        // Java object happens as part of the transaction commit, or something like that.
        HibernateAssessmentResource hibernateResource = HibernateAssessmentResource.create(resource, appId, assessmentId);
        HibernateAssessmentResource retValue = hibernateHelper.executeWithExceptionHandling(hibernateResource,
                (session) -> {
                    session.saveOrUpdate(hibernateResource);
                    return hibernateResource;
                });
        return AssessmentResource.create(retValue);
    }
    
    @Override
    public List<AssessmentResource> saveResources(String appId, String assessmentId, List<AssessmentResource> resources) {
        List<HibernateAssessmentResource> hibernateResources = resources.stream()
                .map(res -> HibernateAssessmentResource.create(res, appId, assessmentId))
                .collect(toList());
                
        List<HibernateAssessmentResource> savedResources = hibernateHelper.executeWithExceptionHandling(hibernateResources, 
            (session) -> hibernateResources.stream()
                        .map(hr -> (HibernateAssessmentResource)session.merge(hr))
                        .collect(toList()));
        return savedResources.stream()
                .map(res -> AssessmentResource.create(res))
                .collect(toList());
    }

    @Override
    public void deleteResource(String appId, AssessmentResource resource) {
        hibernateHelper.executeWithExceptionHandling(resource, (session) -> {
            NativeQuery<?> query = session.createNativeQuery(DELETE_QUERY);
            query.setParameter("appId", appId);
            query.setParameter("guid", resource.getGuid());
            query.executeUpdate();
            return null;
        });
    }
}
