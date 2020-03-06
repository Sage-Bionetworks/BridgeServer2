package org.sagebionetworks.bridge.hibernate;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeUtils.AND_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.isEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResource;

@Component
class HibernateAssessmentDao implements AssessmentDao {
    static final String APP_ID = "appId";
    static final String IDENTIFIER = "identifier";
    static final String REVISION = "revision";
    static final String GUID = "guid";
        
    static final String SELECT_COUNT = "SELECT COUNT(*)";
    static final String SELECT_ALL = "SELECT *";
    static final String GET_BY_IDENTIFIER = "FROM HibernateAssessment WHERE appId=:appId "+
            "AND identifier=:identifier AND revision=:revision";
    static final String GET_BY_GUID = "FROM HibernateAssessment WHERE appId=:appId AND guid=:guid";

    static final String GET_REVISIONS = "FROM HibernateAssessment WHERE appId = :appId AND "
            +"identifier = :identifier";
    static final String GET_REVISIONS2 = "ORDER BY revision DESC";
    static final String EXCLUDE_DELETED = "AND deleted = 0";

    static final String BATCH_LOGICAL_DELETE = "UPDATE ExternalResources SET deleted = 1, version = "
            +"(version+1) WHERE appId = :appId AND assessmentId = :assessmentId AND deleted = 0";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, int pageSize,
            Set<String> tags, boolean includeDeleted) {
        
        boolean includeTags = !isEmpty(tags);
        
        // Not sure pulling this out into constants is any easier to understand...
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM (");
        builder.append("SELECT DISTINCT identifier as id, MAX(revision) AS rev FROM Assessments");
        builder.append("GROUP BY identifier) AS latest_assessments");
        builder.append("INNER JOIN Assessments AS a ON a.identifier = latest_assessments.id AND");
        builder.append("a.revision = latest_assessments.rev");
        
        List<String> clauses = new ArrayList<>();
        clauses.add("WHERE appId = :appId");
        if (includeTags) {
            clauses.add("guid IN (SELECT DISTINCT assessmentGuid FROM AssessmentTags WHERE tagValue IN :tags)");
            builder.getParameters().put("tags", tags);
        }
        if (!includeDeleted) {
            clauses.add("deleted = 0");
        }
        builder.append(AND_JOINER.join(clauses), "appId", appId);
        builder.append("ORDER BY createdOn DESC");
        
        int count = hibernateHelper.nativeQueryCount(
                "SELECT count(*) " + builder.getQuery(), builder.getParameters());
        List<HibernateAssessment> assessments = hibernateHelper.nativeQueryGet(
                "SELECT * " + builder.getQuery(), builder.getParameters(), 
                offsetBy, pageSize, HibernateAssessment.class);
        
        List<Assessment> dtos = assessments.stream().map(Assessment::create).collect(toList());
        return new PagedResourceList<Assessment>(dtos, count);
    }
    
    public PagedResourceList<Assessment> getAssessmentRevisions(String appId, String identifier, 
            int offsetBy, int pageSize, boolean includeDeleted) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_REVISIONS, APP_ID, appId, IDENTIFIER, identifier);
        if (!includeDeleted) {
            builder.append(EXCLUDE_DELETED);
        }
        builder.append(GET_REVISIONS2);
        
        int count = hibernateHelper.queryCount(SELECT_COUNT + builder.getQuery(), builder.getParameters());
        
        List<HibernateAssessment> assessments = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), offsetBy, pageSize, HibernateAssessment.class);
        
        List<Assessment> dtos = assessments.stream().map(Assessment::create).collect(toList());
        return new PagedResourceList<Assessment>(dtos, count);
    }
    
    @Override
    public Optional<Assessment> getAssessment(String appId, String guid) {
        List<HibernateAssessment> results = hibernateHelper.queryGet(
                GET_BY_GUID, ImmutableMap.of(APP_ID, appId, GUID, guid), null, null, HibernateAssessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Assessment.create(results.get(0)));
    }

    @Override
    public Optional<Assessment> getAssessment(String appId, String identifier, int revision) {
        List<HibernateAssessment> results = hibernateHelper.queryGet(
                GET_BY_IDENTIFIER, ImmutableMap.of(APP_ID, appId, IDENTIFIER, identifier, REVISION, revision), 
                null, null, HibernateAssessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Assessment.create(results.get(0)));
    }
    
    @Override
    public Assessment saveAssessment(String appId, Assessment assessment) {
        HibernateAssessment hibernateAssessment = HibernateAssessment.create(assessment, appId);
        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateAssessment, 
                (session) -> (HibernateAssessment)session.merge(hibernateAssessment));
        return Assessment.create(retValue);
        /*
        return hibernateHelper.executeWithExceptionHandling(assessment, (session) -> {
            HibernateAssessment a = (HibernateAssessment)session.merge(hibernateAssessment);
            return Assessment.create(a);
        });
        */
    }

    @Override
    public void deleteAssessment(String appId, Assessment assessment) {
        HibernateAssessment hibernateAssessment = HibernateAssessment.create(assessment, appId);
        hibernateHelper.executeWithExceptionHandling(hibernateAssessment, (session) -> {
            session.remove(hibernateAssessment);
            return null;
        });
    }

    @Override
    public Assessment publishAssessment(String originAppId, Assessment origin, Assessment dest,
            List<AssessmentResource> destResources) {
        String assessmentId = dest.getIdentifier();
        
        HibernateAssessment hibernateOrigin = HibernateAssessment.create(origin, originAppId);
        HibernateAssessment hibernateDest = HibernateAssessment.create(dest, SHARED_STUDY_ID_STRING);
        List<HibernateAssessmentResource> hibernateDestResources = destResources.stream()
                .map((resource) -> HibernateAssessmentResource.create(resource, SHARED_STUDY_ID_STRING, assessmentId))
                .collect(Collectors.toList());

        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateOrigin, (session) -> {
            // logically delete any existing resources in destination app context 
            NativeQuery<?> query = session.createNativeQuery(BATCH_LOGICAL_DELETE);
            query.setParameter("appId", SHARED_STUDY_ID_STRING);
            query.setParameter("assessmentId", assessmentId);
            query.executeUpdate();
            
            // And persist all of the resources
            for (HibernateAssessmentResource resource : hibernateDestResources) {
                session.persist(resource);
            }
            session.saveOrUpdate(hibernateDest);
            return (HibernateAssessment)session.merge(hibernateOrigin);
        });
        return Assessment.create(retValue);
    }

    @Override
    public Assessment importAssessment(String destAppId, Assessment dest, List<AssessmentResource> destResources) {
        String assessmentId = dest.getIdentifier();
        
        HibernateAssessment hibernateDest = HibernateAssessment.create(dest, destAppId);
        List<HibernateAssessmentResource> hibernateDestResources = destResources.stream()
                .map((resource) -> HibernateAssessmentResource.create(resource, destAppId, assessmentId))
                .collect(Collectors.toList());

        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateDest, (session) -> {
            // logically delete any existing resources in destination app context 
            NativeQuery<?> query = session.createNativeQuery(BATCH_LOGICAL_DELETE);
            query.setParameter("appId", destAppId);
            query.setParameter("assessmentId", assessmentId);
            query.executeUpdate();
            
            // And persist all of the resources
            for (HibernateAssessmentResource resource : hibernateDestResources) {
                session.persist(resource);
            }
            session.merge(hibernateDest);
            return hibernateDest;
        });
        return Assessment.create(retValue);
    }
}
