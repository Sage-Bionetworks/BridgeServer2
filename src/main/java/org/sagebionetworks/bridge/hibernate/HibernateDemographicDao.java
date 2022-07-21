package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.hibernate.query.Query;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicId;
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
    public void saveDemographicUser(DemographicUser demographicUser) {
        hibernateHelper.saveOrUpdate(demographicUser);
    }

    @Override
    public void deleteDemographic(String appId, String studyId, String userId, String categoryName) {
        // need to get user first for demographicUserId
        String demographicUserId = getDemographicUserId(appId, studyId, userId);
        hibernateHelper.deleteById(Demographic.class, new DemographicId(demographicUserId, categoryName));
    }

    @Override
    public void deleteDemographicUser(String appId, String studyId, String userId) {
        String demographicUserId = getDemographicUserId(appId, studyId, userId);
        hibernateHelper.deleteById(DemographicUser.class, demographicUserId);
    }

    // can return null
    @Override
    public String getDemographicUserId(String appId, String studyId, String userId) {
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
        return existingDemographicUserId;
    }

    @Override
    public DemographicUser getDemographicUser(String appId, String studyId, String userId) throws BadRequestException {
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
        if (existingDemographicUser == null) {
            throw new BadRequestException("no user demographics were found with the specified parameters");
        }
        return existingDemographicUser;
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
