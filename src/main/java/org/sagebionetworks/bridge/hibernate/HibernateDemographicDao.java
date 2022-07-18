package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.hibernate.query.Query;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicId;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.springframework.stereotype.Component;

@Component
public class HibernateDemographicDao implements DemographicDao {
    private static final String SELECT_COUNT = "SELECT COUNT(*) ";

    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public void saveDemographic(Demographic demographic) {
        hibernateHelper.saveOrUpdate(demographic);
    }

    @Override
    public void saveDemographicUser(DemographicUser demographicUser) {
        hibernateHelper.saveOrUpdate(demographicUser);
    }

    @Override
    public void deleteDemographic(String appId, String studyId, String userId, String categoryName) {
        DemographicUser demographicUser = getDemographicUser(appId, studyId, userId);
        hibernateHelper.deleteById(Demographic.class, new DemographicId(demographicUser.getId(), categoryName));
    }

    @Override
    public void deleteDemographicUser(String appId, String studyId, String userId) {
        DemographicUser demographicUser = getDemographicUser(appId, studyId, userId);
        hibernateHelper.deleteById(DemographicUser.class, demographicUser.getId());
    }

    @Override
    public Demographic getDemographic(String appId, String studyId, String userId, String categoryName) {
        DemographicUser demographicUser = getDemographicUser(appId, studyId, userId);
        return demographicUser.getDemographics().get(categoryName);
    }

    @Override
    public DemographicUser getDemographicUser(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("from DemographicUser du");
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
        return existingDemographicUser;
    }

    @Override
    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("from DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        where.append("du.studyId = :studyId", "studyId", studyId);
        int count = hibernateHelper.queryCount(SELECT_COUNT + builder.getQuery(), builder.getParameters());
        List<DemographicUser> existingDemographicUsers = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, DemographicUser.class);
        return new PagedResourceList<>(existingDemographicUsers, count)
                .withRequestParam("offsetBy", offsetBy)
                .withRequestParam("pageSize", pageSize);
    }
}
