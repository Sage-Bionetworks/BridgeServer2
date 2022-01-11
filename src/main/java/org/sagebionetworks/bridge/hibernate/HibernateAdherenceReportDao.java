package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.springframework.stereotype.Component;

@Component
public class HibernateAdherenceReportDao implements AdherenceReportDao {

    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public void saveWeeklyAdherenceReport(WeeklyAdherenceReport report) {
        hibernateHelper.saveOrUpdate(report);
    }

    @Override
    public PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            String labelFilter, Integer complianceUnder, Integer offsetBy, Integer pageSize) {
        
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM WeeklyAdherenceReport h");
        if (labelFilter != null) {
            builder.append("JOIN h.labels label");    
        }
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("h.appId = :appId", "appId", appId);
        where.append("h.studyId = :studyId", "studyId", studyId);
        if (complianceUnder != null) {
            where.append("weeklyAdherencePercent < :complianceUnder", "complianceUnder", complianceUnder);
        }
        if (labelFilter != null) {
            where.append("label LIKE :labelFilter", "labelFilter", "%" + labelFilter + "%");
        }
        builder.append("ORDER BY weeklyAdherencePercent");
        
        int total = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        
        List<WeeklyAdherenceReport> reports = hibernateHelper.queryGet("SELECT h " + builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, WeeklyAdherenceReport.class);

        return new PagedResourceList<>(reports, total, true);
    }
}    
