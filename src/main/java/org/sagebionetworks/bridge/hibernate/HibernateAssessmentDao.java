package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.models.TagUtils.toTagSet;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Tag;
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

    static final String GET_LATEST_REVISIONS = "SELECT * FROM (SELECT DISTINCT identifier as id, "
            +"MAX(revision) AS rev FROM Assessments WHERE appId = :appId";
    static final String GET_LATEST_REVISIONS2 = "GROUP BY identifier) AS latest_assessments "
            +"INNER JOIN Assessments a ON a.identifier = latest_assessments.id "
            +"AND a.revision = latest_assessments.rev "
            +"ORDER BY createdOn DESC";
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
            Set<String> categories, Set<String> tags, boolean includeDeleted) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_LATEST_REVISIONS, APP_ID, appId);
        if (!includeDeleted) {
            builder.append(EXCLUDE_DELETED);
        }
        builder.append(GET_LATEST_REVISIONS2);
        
        List<Assessment> assessments = hibernateHelper.nativeQueryGet(
                builder.getQuery(), builder.getParameters(), offsetBy, pageSize, Assessment.class);
        
        Set<Tag> catTags = toTagSet(categories, "category");
        Set<Tag> tagTags = toTagSet(tags, "tag");
        List<Assessment> filtered = assessments.stream()
            .filter(a -> a.getCategories().containsAll(catTags) && a.getTags().containsAll(tagTags))
            .collect(toImmutableList());
        
        // subList cannot take a value greater than the list size, so prevent this.
        int limit = Math.min(filtered.size(), offsetBy+pageSize);
        if (offsetBy > limit) {
            return new PagedResourceList<Assessment>(ImmutableList.of(), filtered.size());
        }
        return new PagedResourceList<Assessment>(filtered.subList(offsetBy, limit), filtered.size());
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
        // Note that in this version, we cannot detach the instance, which may cause errors
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    @Override
    public Optional<Assessment> getAssessment(String appId, String identifier, int revision) {
        List<Assessment> results = hibernateHelper.queryGet(
                GET_BY_IDENTIFIER, ImmutableMap.of(APP_ID, appId, IDENTIFIER, identifier, REVISION, revision), 
                null, null, Assessment.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }
    
    @Override
    public Assessment createAssessment(Assessment assessment) {
        return hibernateHelper.executeWithExceptionHandling(assessment, 
                (session) -> (Assessment)session.merge(assessment));
    }

    @Override
    public Assessment updateAssessment(Assessment assessment) {
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
