package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.DemographicDao;
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
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public DemographicUser saveDemographicUser(DemographicUser demographicUser,
            Optional<String> existingDemographicUserId) {
        if (existingDemographicUserId.isPresent()) {
            return hibernateHelper.executeWithExceptionHandling(null, (session) -> {
                session.delete(session.get(DemographicUser.class, existingDemographicUserId.get()));
                session.saveOrUpdate(demographicUser);
                return demographicUser;
            });
        } else {
            return hibernateHelper.saveOrUpdate(demographicUser);
        }
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
        Optional<String> existingDemographicUserId = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), String.class);
        return existingDemographicUserId;
    }

    @Override
    public Optional<Demographic> getDemographic(String demographicId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Demographic d");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("d.id = :demographicId", "demographicId", demographicId);
        Optional<Demographic> existingDemographic = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), Demographic.class);
        return existingDemographic;
    }

    @Override
    public Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        where.append("du.studyId = :studyId", "studyId", studyId);
        where.append("du.userId = :userId", "userId", userId);
        Optional<DemographicUser> existingDemographicUser = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), DemographicUser.class);
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
        return new PagedResourceList<>(existingDemographicUsers, count, true)
                .withRequestParam("offsetBy", offsetBy)
                .withRequestParam("pageSize", pageSize);
    }
}
