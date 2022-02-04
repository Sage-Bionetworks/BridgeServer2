package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.OR_JOINER;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.AccountTestFilter;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.springframework.stereotype.Component;

@Component
public class HibernateAdherenceReportDao implements AdherenceReportDao {

    static final String SELECT_COUNT = "SELECT COUNT(*) ";
    static final String SELECT_DISTINCT = "SELECT DISTINCT h ";
    static final String ADHERENCE_MIN_FIELD = "adherenceMin";
    static final String ADHERENCE_MAX_FIELD = "adherenceMax";
    static final String ID_FILTER_FIELD = "id";
    static final String PROGRESSION_FILTER_FIELD = "progressionFilters"; 
    static final String LABEL_FILTER_FIELD = "labelFilter";
    static final String STUDY_ID_FIELD = "studyId";
    static final String APP_ID_FIELD = "appId";
    
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public void saveWeeklyAdherenceReport(WeeklyAdherenceReport report) {
        checkNotNull(report);
        hibernateHelper.saveOrUpdate(report);
    }

    @Override
    public PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            AdherenceReportSearch search) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        boolean hasLabels = (search.getLabelFilters() != null && !search.getLabelFilters().isEmpty());
        
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM WeeklyAdherenceReport h");
        if (hasLabels) {
            builder.append("LEFT JOIN h.searchableLabels label");
        }
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("h.appId = :appId", APP_ID_FIELD, appId);
        where.append("h.studyId = :studyId", STUDY_ID_FIELD, studyId);
        if (search.getAdherenceMin() > 0) {
            where.append("h.weeklyAdherencePercent >= :adherenceMin", ADHERENCE_MIN_FIELD, search.getAdherenceMin());    
        }
        if (search.getAdherenceMax() < 100) {
            where.append("h.weeklyAdherencePercent <= :adherenceMax", ADHERENCE_MAX_FIELD, search.getAdherenceMax());    
        }
        if (search.getProgressionFilters() != null && !search.getProgressionFilters().isEmpty()) {
            where.append("h.progression IN :progressionFilters", PROGRESSION_FILTER_FIELD, search.getProgressionFilters());
        }
        if (hasLabels) {
            where.labels(search.getLabelFilters());
        }
        if (search.getTestFilter() == AccountTestFilter.TEST) {
            where.append("h.testAccount = 1");
        } else if (search.getTestFilter() == AccountTestFilter.PRODUCTION) {
            where.append("h.testAccount = 0");
        }
        if (search.getIdFilter() != null) {
            List<String> clauses = new ArrayList<>();
            clauses.add("h.participant.externalId LIKE :id");
            clauses.add("h.participant.identifier LIKE :id");
            clauses.add("h.participant.firstName LIKE :id");
            clauses.add("h.participant.lastName LIKE :id");
            clauses.add("h.participant.email LIKE :id");
            // Hibernate wants the name of the property on Phone ('number') and NOT the name of the 
            // DB column ('phone'). It sort of makes sense, since we're referencing Participant which isn’t
            // even in the database.
            clauses.add("h.participant.phone.number LIKE :id");
            where.append("(" + OR_JOINER.join(clauses) + ")", "id", "%" + search.getIdFilter() + "%");
        }
        builder.append("ORDER BY h.weeklyAdherencePercent, h.participant.lastName, h.participant.firstName, "
                +"h.participant.email, h.participant.phone, h.participant.externalId");

        int total = hibernateHelper.queryCount(SELECT_COUNT + builder.getQuery(), builder.getParameters());
        
        List<WeeklyAdherenceReport> reports = hibernateHelper.queryGet(SELECT_DISTINCT + builder.getQuery(),
                builder.getParameters(), search.getOffsetBy(), search.getPageSize(), WeeklyAdherenceReport.class);

        return new PagedResourceList<>(reports, total, true);
    }
}    
