package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import org.hibernate.query.Query;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.springframework.stereotype.Component;

@Component
public class HibernateDemographicDao implements DemographicDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public DemographicUser saveDemographicUser(DemographicUser demographicUser) {
        return hibernateHelper.saveOrUpdate(demographicUser);
    }

    @Override
    public void deleteDemographic(String demographicId) {
        hibernateHelper.deleteById(Demographic.class, demographicId);
    }

    @Override
    public void deleteDemographicUser(String demographicUserId) {
        hibernateHelper.deleteById(DemographicUser.class, demographicUserId);
    }

    @Override
    public Optional<String> getDemographicUserId(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT du.id FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        where.append("du.studyId = :studyId", "studyId", studyId);
        where.append("du.userId = :userId", "userId", userId);
        // manually execute to use query.uniqueResult
        String existingDemographicUserId = hibernateHelper.executeWithExceptionHandling(null, session -> {
            Query<String> query = session.createQuery(builder.getQuery(), String.class);
            for (Map.Entry<String, Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            return query.uniqueResult();
        });
        return Optional.ofNullable(existingDemographicUserId);
    }

    @Override
    public Optional<Demographic> getDemographic(String demographicId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Demographic d");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("d.id = :demographicId", "demographicId", demographicId);
        // manually execute to use query.uniqueResult
        Demographic existingDemographic = hibernateHelper.executeWithExceptionHandling(null, session -> {
            Query<Demographic> query = session.createQuery(builder.getQuery(), Demographic.class);
            for (Map.Entry<String, Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            return query.uniqueResult();
        });
        return Optional.ofNullable(existingDemographic);
    }

    @Override
    public Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        where.append("du.studyId = :studyId", "studyId", studyId);
        where.append("du.userId = :userId", "userId", userId);
        // manually execute to use query.uniqueResult
        DemographicUser existingDemographicUser = hibernateHelper.executeWithExceptionHandling(null, session -> {
            Query<DemographicUser> query = session.createQuery(builder.getQuery(), DemographicUser.class);
            for (Map.Entry<String, Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            return query.uniqueResult();
        });
        return Optional.ofNullable(existingDemographicUser);
    }

    @Override
    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        where.append("du.studyId = :studyId", "studyId", studyId);
        int count = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        List<DemographicUser> existingDemographicUsers = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, DemographicUser.class);
        return new PagedResourceList<>(existingDemographicUsers, count)
                .withRequestParam("offsetBy", offsetBy)
                .withRequestParam("pageSize", pageSize);
    }
}
