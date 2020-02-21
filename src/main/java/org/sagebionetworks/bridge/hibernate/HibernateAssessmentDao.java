package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeUtils.isEmpty;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;

@Component
class HibernateAssessmentDao implements AssessmentDao {
    static final String APP_ID = "appId";
    static final String IDENTIFIER = "identifier";
    static final String REVISION = "revision";
    static final String GUID = "guid";
        
    static final String SELECT_COUNT = "SELECT COUNT(*)";
    static final String SELECT_ALL = "SELECT *";
    static final String GET_BY_IDENTIFIER = "FROM Assessment WHERE appId=:appId "+
            "AND identifier=:identifier AND revision=:revision";
    static final String GET_BY_GUID = "FROM Assessment WHERE appId=:appId AND guid=:guid";

    static final String GET_REVISIONS = "FROM Assessment WHERE appId = :appId AND "
            +"identifier = :identifier";
    static final String GET_REVISIONS2 = "ORDER BY revision DESC";
    static final String EXCLUDE_DELETED = "AND deleted = 0";
    
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
        builder.append("  SELECT DISTINCT identifier as id, MAX(revision) AS rev FROM Assessments"); 
        builder.append("  WHERE appId = :appId GROUP BY identifier", APP_ID, appId);
        builder.append(") AS latest_assessments");
        builder.append("INNER JOIN Assessments AS a ON a.identifier = latest_assessments.id AND");
        builder.append("a.revision = latest_assessments.rev");
        if (includeTags) {
            builder.append("INNER JOIN AssessmentTags AS atag ON a.guid = atag.assessmentGuid");    
        }
        if (includeTags || !includeDeleted) {
            builder.append("WHERE");
            if (includeTags) {
                builder.append("atag.tagValue IN :tags", "tags", tags);    
            }
            if (includeTags && !includeDeleted) {
                builder.append("AND");
            }
            if (!includeDeleted) {
                builder.append("a.deleted = 0");    
            }
        }
        builder.append("ORDER BY createdOn DESC");
        
        int count = hibernateHelper.nativeQueryCount(
                "SELECT count(*) " + builder.getQuery(), builder.getParameters());
        List<Assessment> assessments = hibernateHelper.nativeQueryGet(
                "SELECT * " + builder.getQuery(), builder.getParameters(), 
                offsetBy, pageSize, Assessment.class);
        return new PagedResourceList<Assessment>(assessments, count);
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
        
        List<Assessment> assessments = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), offsetBy, pageSize, Assessment.class);
        
        return new PagedResourceList<Assessment>(assessments, count);
    }
    
    @Override
    public Optional<Assessment> getAssessment(String appId, String guid) {
        List<Assessment> results = hibernateHelper.queryGet(
                GET_BY_GUID, ImmutableMap.of(APP_ID, appId, GUID, guid), null, null, Assessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public Optional<Assessment> getAssessment(String appId, String identifier, int revision) {
        List<Assessment> results = hibernateHelper.queryGet(
                GET_BY_IDENTIFIER, ImmutableMap.of(APP_ID, appId, IDENTIFIER, identifier, REVISION, revision), 
                null, null, Assessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }
    
    @Override
    public Assessment saveAssessment(Assessment assessment) {
        return hibernateHelper.executeWithExceptionHandling(assessment, 
                (session) -> (Assessment)session.merge(assessment));
    }

    @Override
    public void deleteAssessment(Assessment assessment) {
        hibernateHelper.executeWithExceptionHandling(assessment, (session) -> {
            session.remove(assessment);
            return null;
        });
    }
    
    @Override
    public Assessment publishAssessment(Assessment original, Assessment assessmentToPublish) {
        return hibernateHelper.executeWithExceptionHandling(original, (session) -> {
            session.saveOrUpdate(assessmentToPublish);
            return (Assessment)session.merge(original);
        });
    }
}
