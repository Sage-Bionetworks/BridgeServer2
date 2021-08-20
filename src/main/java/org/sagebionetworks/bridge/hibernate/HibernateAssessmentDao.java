package org.sagebionetworks.bridge.hibernate;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeUtils.AND_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.isEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

@Component
class HibernateAssessmentDao implements AssessmentDao {
    static final String DELETE_RESOURCES_SQL = "DELETE FROM ExternalResources where appId = :appId AND assessmentId = :assessmentId";
    static final String DELETE_CONFIG_SQL = "DELETE FROM AssessmentConfigs where guid = :guid";
    static final String APP_ID = "appId";
    static final String ASSESSMENT_ID = "assessmentId";
    static final String IDENTIFIER = "identifier";
    static final String REVISION = "revision";
    static final String GUID = "guid";
    static final String OWNER_ID = "ownerId";
        
    static final String SELECT_COUNT = "SELECT COUNT(*)";
    static final String SELECT_ALL = "SELECT *";
    static final String GET_BY_IDENTIFIER = "FROM HibernateAssessment WHERE appId=:appId "+
            "AND identifier=:identifier AND revision=:revision";
    static final String GET_BY_GUID = "FROM HibernateAssessment WHERE appId=:appId AND guid=:guid";
    static final String GET_FROM_ORG = "FROM HibernateAssessment WHERE (appId = :appId AND ownerId = :ownerId)";

    static final String GET_REVISIONS = "FROM HibernateAssessment WHERE appId = :appId AND "
            +"identifier = :identifier";
    static final String GET_REVISIONS2 = "ORDER BY revision DESC";
    static final String EXCLUDE_DELETED = "AND deleted = 0";
    static final String LIMIT_TO_OWNER = "AND ownerId = :ownerId";

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Assessment> getAssessments(String appId, String ownerId, 
            int offsetBy, int pageSize, Set<String> tags, boolean includeDeleted) {
        
        boolean includeTags = !isEmpty(tags);
        
        // Not sure pulling this out into constants is any easier to understand...
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM (");
        builder.append("SELECT DISTINCT identifier as id, MAX(revision) AS rev FROM Assessments");
        builder.append("WHERE appId = :appId");
        if (ownerId != null) {
            builder.append(LIMIT_TO_OWNER, OWNER_ID, ownerId);
        }
        builder.append("GROUP BY identifier) AS latest_assessments");
        builder.append("INNER JOIN Assessments AS a ON a.identifier = latest_assessments.id AND");
        builder.append("a.revision = latest_assessments.rev");
        
        List<String> clauses = new ArrayList<>();
        clauses.add("WHERE appId = :appId");
        if (ownerId != null) {
            clauses.add("ownerId = :ownerId");
            builder.getParameters().put(OWNER_ID, ownerId);
        }
        if (includeTags) {
            clauses.add("guid IN (SELECT DISTINCT assessmentGuid FROM AssessmentTags WHERE tagValue IN :tags)");
            builder.getParameters().put("tags", tags);
        }
        if (!includeDeleted) {
            clauses.add("deleted = 0");
        }
        builder.append(AND_JOINER.join(clauses), APP_ID, appId);
        builder.append("ORDER BY createdOn DESC");
        
        int count = hibernateHelper.nativeQueryCount(
                SELECT_COUNT + " " + builder.getQuery(), builder.getParameters());
        List<HibernateAssessment> assessments = hibernateHelper.nativeQueryGet(
                SELECT_ALL + " " + builder.getQuery(), builder.getParameters(), 
                offsetBy, pageSize, HibernateAssessment.class);
        
        List<Assessment> dtos = assessments.stream().map(Assessment::create).collect(toList());
        return new PagedResourceList<Assessment>(dtos, count, true);
    }
    
    @Override
    public PagedResourceList<Assessment> getAssessmentRevisions(String appId, String ownerId, 
            String identifier, int offsetBy, int pageSize, boolean includeDeleted) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_REVISIONS, APP_ID, appId, IDENTIFIER, identifier);
        if (ownerId != null) {
            builder.append(LIMIT_TO_OWNER, OWNER_ID, ownerId);
        }
        if (!includeDeleted) {
            builder.append(EXCLUDE_DELETED);
        }
        builder.append(GET_REVISIONS2);
        
        int count = hibernateHelper.queryCount(SELECT_COUNT + builder.getQuery(), builder.getParameters());
        
        List<HibernateAssessment> assessments = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), offsetBy, pageSize, HibernateAssessment.class);
        
        List<Assessment> dtos = assessments.stream().map(Assessment::create).collect(toList());
        return new PagedResourceList<Assessment>(dtos, count, true);
    }
    
    @Override
    public Optional<Assessment> getAssessment(String appId, String ownerId, String guid) {
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_BY_GUID, APP_ID, appId, GUID, guid);
        if (ownerId != null) {
            builder.append(LIMIT_TO_OWNER, OWNER_ID, ownerId); 
        }
        
        List<HibernateAssessment> results = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), null, null, HibernateAssessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Assessment.create(results.get(0)));
    }

    @Override
    public Optional<Assessment> getAssessment(String appId, String ownerId, String identifier, int revision) {
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_BY_IDENTIFIER, APP_ID, appId, IDENTIFIER, identifier, REVISION, revision);
        if (ownerId != null) {
            builder.append(LIMIT_TO_OWNER, OWNER_ID, ownerId); 
        }
        List<HibernateAssessment> results = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), null, null, HibernateAssessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Assessment.create(results.get(0)));
    }
    
    @Override
    public Assessment createAssessment(String appId, Assessment assessment, AssessmentConfig config) {
        HibernateAssessment hibernateAssessment = HibernateAssessment.create(appId, assessment);
        HibernateAssessmentConfig hibernateConfig = HibernateAssessmentConfig.create(assessment.getGuid(), config);
        
        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateAssessment, (session) -> {
            session.persist(hibernateConfig);
            return (HibernateAssessment)session.merge(hibernateAssessment);
        });
        return Assessment.create(retValue);
    }

    
    @Override
    public Assessment updateAssessment(String appId, Assessment assessment) {
        // If you do not receive back the managed object from the executeWithExceptionHandling() method, and THEN
        // convert it to a non-managed object, the version will not be updated. It appears that the update of the 
        // Java object happens as part of the transaction commit, or something like that.
        HibernateAssessment hibernateAssessment = HibernateAssessment.create(appId, assessment);
        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateAssessment, 
                (session) -> (HibernateAssessment)session.merge(hibernateAssessment));
        return Assessment.create(retValue);
    }

    @Override
    public void deleteAssessment(String appId, Assessment assessment) {
        // If this is the last revision (logically deleted or not), also delete the resources.
        int count = getAssessmentRevisions(appId, null, assessment.getIdentifier(), 0, 1, true).getTotal();
        HibernateAssessment hibernateAssessment = HibernateAssessment.create(appId, assessment);
        
        hibernateHelper.executeWithExceptionHandling(hibernateAssessment, (session) -> {
            String assessmentId = hibernateAssessment.getIdentifier();
            if (count == 1) {
                NativeQuery<?> query = session.createNativeQuery(DELETE_RESOURCES_SQL);
                query.setParameter(APP_ID, appId);
                query.setParameter(ASSESSMENT_ID, assessmentId);
                query.executeUpdate();
            }
            NativeQuery<?> query = session.createNativeQuery(DELETE_CONFIG_SQL);
            query.setParameter(GUID, hibernateAssessment.getGuid());
            query.executeUpdate();
            
            session.remove(hibernateAssessment);
            return null;
        });
    }

    @Override
    public Assessment publishAssessment(String originAppId, Assessment origin, Assessment dest,
            AssessmentConfig destConfig) {
        HibernateAssessment hibernateOrigin = HibernateAssessment.create(originAppId, origin);
        HibernateAssessment hibernateDest = HibernateAssessment.create(SHARED_APP_ID, dest);
        HibernateAssessmentConfig hibernateDestConfig = HibernateAssessmentConfig.create(dest.getGuid(), destConfig);

        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateOrigin, (session) -> {
            // And persist all of the resources
            session.saveOrUpdate(hibernateDestConfig);
            session.saveOrUpdate(hibernateDest);
            return (HibernateAssessment)session.merge(hibernateOrigin);
        });
        return Assessment.create(retValue);
    }

    @Override
    public Assessment importAssessment(String destAppId, Assessment dest, AssessmentConfig destConfig) {
        HibernateAssessment hibernateDest = HibernateAssessment.create(destAppId, dest);
        HibernateAssessmentConfig hibernateConfig = HibernateAssessmentConfig.create(dest.getGuid(), destConfig);
        
        HibernateAssessment retValue = hibernateHelper.executeWithExceptionHandling(hibernateDest, (session) -> {
            session.saveOrUpdate(hibernateConfig);
            session.merge(hibernateDest);
            return hibernateDest;
        });
        return Assessment.create(retValue);
    }

    @Override
    public boolean hasAssessmentFromOrg(String appId, String orgId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append(SELECT_COUNT);
        builder.append(GET_FROM_ORG, APP_ID, appId, OWNER_ID, orgId);
        int resultCount = hibernateHelper.queryCount(builder.getQuery(), builder.getParameters());
        if (resultCount != 0) {
            return true;
        }
        Map<String, Object> params = builder.getParameters();
        params.put(APP_ID, "shared");
        params.put(OWNER_ID, appId + ":" + orgId);
        resultCount = hibernateHelper.queryCount(builder.getQuery(), builder.getParameters());
        return resultCount != 0;
    }
}
