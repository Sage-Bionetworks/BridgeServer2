package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize,
            Set<AlertCategory> alertCategories) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.studyId = :studyId", "studyId", studyId);
        where.append("a.category in (:alertCategories)", "alertCategories", alertCategories);
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

    @Override
    public void deleteAlertsForStudy(String appId, String studyId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.studyId = :studyId", "studyId", studyId);
        hibernateHelper.query(builder.getQuery(), builder.getParameters());
    }

    @Override
    public void deleteAlertsForUserInApp(String appId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.userId = :userId", "userId", userId);
        hibernateHelper.query(builder.getQuery(), builder.getParameters());
    }

    @Override
    public void deleteAlertsForUserInStudy(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM Alert a");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("a.appId = :appId", "appId", appId);
        where.append("a.studyId = :studyId", "studyId", studyId);
        where.append("a.userId = :userId", "userId", userId);
        hibernateHelper.query(builder.getQuery(), builder.getParameters());
    }
}
