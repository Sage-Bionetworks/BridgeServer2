package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;
import org.springframework.stereotype.Component;

@Component
public class HibernateAlertDao implements AlertDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public void createAlert(Alert alert) {
        hibernateHelper.create(alert);
    }

    @Override
    public Optional<Alert> getAlert(String studyId, String appId, String userId, AlertCategory category) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.studyId = :studyId", "studyId", studyId);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.userId = :userId", "userId", userId);
        where.append("a.category = :category", "category", category);
        return hibernateHelper.queryGetOne(builder.getQuery(), builder.getParameters(), Alert.class);
    }

    @Override
    public Optional<Alert> getAlertById(String alertId) {
        return Optional.ofNullable(hibernateHelper.getById(Alert.class, alertId));
    }

    @Override
    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.studyId = :studyId", "studyId", studyId);
        builder.append("ORDER BY createdOn DESC");
        int count = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        List<Alert> alerts = hibernateHelper.queryGet(builder.getQuery(), builder.getParameters(), offsetBy, pageSize,
                Alert.class);
        return new PagedResourceList<>(alerts, count, true)
                .withRequestParam("offsetBy", offsetBy)
                .withRequestParam("pageSize", pageSize);
    }

    @Override
    public void deleteAlerts(List<String> alertIds) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.id in (:alertIds)", "alertIds", alertIds);
        hibernateHelper.query(builder.getQuery(), builder.getParameters());
    }
}
