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
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

@Component
public class HibernateAssessmentResourceDao implements AssessmentResourceDao {
    static final String DELETE_QUERY = "DELETE FROM ExternalResources WHERE guid = :guid";
    
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
        if (minRevision != null) {
            clauses.add("createdAtRevision >= :minRevision");
            builder.getParameters().put("minRevision", minRevision);
        }
        if (maxRevision != null) {
            clauses.add("createdAtRevision <= :maxRevision");
            builder.getParameters().put("maxRevision", maxRevision);
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
    public Optional<AssessmentResource> getResource(String guid) {
        HibernateAssessmentResource resource = hibernateHelper.getById(HibernateAssessmentResource.class, guid);
        return (resource == null) ? Optional.empty() : Optional.of(AssessmentResource.create(resource));
    }

    @Override
    public AssessmentResource saveResource(String appId, String assessmentId, AssessmentResource resource) {
        HibernateAssessmentResource hibernateResource = HibernateAssessmentResource.create(resource, appId, assessmentId);
        return hibernateHelper.executeWithExceptionHandling(resource, (session) -> {
            HibernateAssessmentResource retValue = (HibernateAssessmentResource)session.merge(hibernateResource);
            return AssessmentResource.create(retValue);
        });
    }

    @Override
    public void deleteResource(AssessmentResource resource) {
        hibernateHelper.executeWithExceptionHandling(resource, (session) -> {
            NativeQuery<?> query = session.createNativeQuery(DELETE_QUERY);
            query.setParameter("guid", resource.getGuid());
            query.executeUpdate();
            return null;
        });
    }
}
