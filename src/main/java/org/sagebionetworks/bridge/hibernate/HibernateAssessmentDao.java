package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.Assessment;

@Component
class HibernateAssessmentDao implements AssessmentDao {
    static final PagedResourceList<Assessment> EMPTY_LIST = new PagedResourceList<Assessment>(ImmutableList.of(), 0);
    static final String SELECT_COUNT = "SELECT COUNT(*) ";
    static final String SELECT_ALL = "SELECT * ";
    
    static final String GET_BY_IDENTIFIER = "FROM Assessment WHERE appId=:appId "+
            "AND identifier=:identifier AND revision=:revision";
    static final String GET_BY_GUID = "FROM Assessment WHERE appId=:appId AND guid=:guid";
    
    private SessionFactory sessionFactory;

    private PersistenceExceptionConverter converter;
    
    @Autowired
    final void setPersistenceExceptionConverter(BasicPersistenceExceptionConverter converter) {
        this.converter = converter;
    }
    
    @Autowired
    final void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    EntityManager getEntityManager() {
        return sessionFactory.createEntityManager();
    }

    @SuppressWarnings("unchecked")
    @Override
    public PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, int pageSize,
            Set<String> categories, Set<String> tags, boolean includeDeleted) {
        
        Set<Tag> catTags = (categories == null) ? ImmutableSet.of() : 
            categories.stream().map(s -> new Tag(s, "category")).collect(toImmutableSet());
        Set<Tag> tagTags = (tags == null) ? ImmutableSet.of() : 
            tags.stream().map(s -> new Tag(s, "tag")).collect(toImmutableSet());
        
        QueryBuilder builder = new QueryBuilder();
        builder.getParameters().put(APP_ID, appId);
        builder.append("SELECT * ");
        builder.append("FROM (SELECT DISTINCT identifier as id, MAX(revision) AS rev FROM Assessments");
        builder.append("WHERE appId = :appId", "appId", appId);
        if (!includeDeleted) {
            builder.append("AND deleted = 0");
        }
        builder.append("GROUP BY identifier) AS latest_assessments");
        builder.append("INNER JOIN Assessments a ON a.identifier = latest_assessments.id");
        builder.append("AND a.revision = latest_assessments.rev");
        builder.append("ORDER BY createdOn DESC");

        EntityManager entityManager = getEntityManager();
        
        Query query = entityManager.createNativeQuery(builder.getQuery(), Assessment.class);
        builder.setParamsOnQuery(query);
        List<Assessment> assessments = query.getResultList();
        
        // I could not create an HQL query that had a subselect AN included the tag table joins to test 
        // for the tag sets... we're planning for dozens of unique assessments so this will do for now.
        List<Assessment> filtered = assessments.stream()
            .filter(a -> a.getCategories().containsAll(catTags) && a.getTags().containsAll(tagTags))
            .collect(toImmutableList());
        
        // subList cannot take a value greater than the list size, so prevent this.
        int limit = Math.min(filtered.size(), offsetBy+pageSize);
        if (offsetBy > limit) {
            return EMPTY_LIST;
        }
        return new PagedResourceList<Assessment>(filtered.subList(offsetBy, limit), filtered.size());
    }
    
    public PagedResourceList<Assessment> getAssessmentRevisions(String appId, String identifier, 
            int offsetBy, int pageSize, boolean includeDeleted) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.getParameters().put(APP_ID, appId);
        builder.append("FROM Assessment WHERE appId = :appId AND identifier = :identifier");
        if (!includeDeleted) {
            builder.append("AND deleted = 0");
        }
        builder.append("ORDER BY revision DESC");
        
        EntityManager entityManager = getEntityManager();
        
        TypedQuery<Long> countQuery = entityManager.createQuery(SELECT_COUNT + builder.getQuery(), Long.class);
        builder.setParamsOnQuery(countQuery);
        countQuery.setParameter(IDENTIFIER, identifier);
        
        List<Long> countResults = countQuery.getResultList();
        Long count = (countResults.isEmpty()) ? null : countResults.get(0);
        
        TypedQuery<Assessment> query = entityManager.createQuery(builder.getQuery(), Assessment.class);
        builder.setParamsOnQuery(query);
        query.setParameter(IDENTIFIER, identifier);
        query.setFirstResult(offsetBy);
        query.setMaxResults(pageSize);
        List<Assessment> assessments = query.getResultList();
        
        return new PagedResourceList<Assessment>(assessments, count.intValue());
    }
    
    @Override
    public Optional<Assessment> getAssessment(String appId, String guid) {
        EntityManager entityManager = getEntityManager();
        TypedQuery<Assessment> query = entityManager.createQuery(GET_BY_GUID, Assessment.class);
        query.setParameter(APP_ID, appId);
        query.setParameter(GUID, guid);
        
        List<Assessment> results = query.getResultList();
        Assessment assessment = null;
        if (!results.isEmpty()) {
            assessment = results.get(0);
            entityManager.detach(assessment);
        }
        return Optional.ofNullable(assessment);
    }

    @Override
    public Optional<Assessment> getAssessment(String appId, String identifier, int revision) {
        EntityManager entityManager = getEntityManager();
        TypedQuery<Assessment> query = entityManager.createQuery(GET_BY_IDENTIFIER, Assessment.class);
        query.setParameter(APP_ID, appId);
        query.setParameter(IDENTIFIER, identifier);
        query.setParameter(REVISION, revision);
        List<Assessment> results = query.getResultList();
        Assessment assessment = (results.isEmpty()) ? null : results.get(0);
        return Optional.ofNullable(assessment);
    }
    
    @Override
    public Assessment createAssessment(Assessment assessment) {
        return execute(assessment, (session) -> (Assessment)session.merge(assessment));
    }

    @Override
    public Assessment updateAssessment(Assessment assessment) {
        return execute(assessment, (session) -> (Assessment)session.merge(assessment));
    }

    @Override
    public void deleteAssessment(Assessment assessment) {
        execute(assessment, (session) -> {
            session.remove(assessment);
            return null;
        });
    }
    
    @Override
    public Assessment publishAssessment(Assessment original, Assessment assessmentToPublish) {
        return execute(original, (session) -> {
            session.saveOrUpdate(assessmentToPublish);
            return (Assessment)session.merge(original);
        });
    }
    
    Assessment execute(Assessment assessment, Function<Session, Assessment> consumer) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                Assessment retValue = consumer.apply(session);
                transaction.commit();
                return retValue;
            } catch(PersistenceException pe) {
                RuntimeException ex = converter.convert(pe, assessment);
                if (ex == pe) {
                    throw new BridgeServiceException(ex);
                } else {
                    throw ex;
                }
            }
        }
    }
}
