package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.OR_JOINER;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AccountTestFilter;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceStatistics;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceStatisticsEntry;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

@Component
public class HibernateAdherenceReportDao implements AdherenceReportDao {
    
    private static final TypeReference<List<WeeklyAdherenceReportRow>> ROWS_LIST = new TypeReference<List<WeeklyAdherenceReportRow>>() {};
    
    // For this to work, the default ONLY_FULL_GROUP_BY sql_mode flag must be removed from MySQL. None of our environments
    // have this value, but local installations will need to create the ~/.my.cnf file as described in the README.md file.
    static final String STATISTICS_SQL = "SELECT label, count(*) as total, any_value(rows) FROM WeeklyAdherenceReportLabels "
            +"labels LEFT JOIN WeeklyAdherenceReports AS reports ON labels.appId = reports.appId AND labels.studyId = "
            +"reports.studyId and labels.userId = reports.userId WHERE reports.appId = :appId AND reports.studyId = :studyId "
            +"GROUP BY label";
    
    static final String ALL_ACTIVE_SQL = "SELECT count(distinct userId) AS count FROM BridgeDB.WeeklyAdherenceReports WHERE "
            +"appId = :appId AND studyId = :studyId AND weeklyAdherencePercent IS NOT NULL";
    
    static final String UNDER_THRESHOLD_SQL = "SELECT count(distinct userId) AS count FROM BridgeDB.WeeklyAdherenceReports WHERE "
            +"appId = :appId AND studyId = :studyId AND weeklyAdherencePercent < :threshold";

    static final Comparator<String> STRING_COMPARATOR = Comparator.nullsLast((r1, r2) -> r1.compareToIgnoreCase(r2));
    
    static final Comparator<AdherenceStatisticsEntry> ENTRIES_COMPARATOR = (r1, r2) -> {
        int sb = STRING_COMPARATOR.compare(r1.getStudyBurstId(), r2.getStudyBurstId());
        return (sb != 0) ? sb : STRING_COMPARATOR.compare(r1.getSessionName(), r2.getSessionName());
    };

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
    
    private SessionFactory sessionFactory;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Autowired
    final void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
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
        if (search.getAdherenceMin() != null) {
            where.append("h.weeklyAdherencePercent >= :adherenceMin", ADHERENCE_MIN_FIELD, search.getAdherenceMin());    
        }
        if (search.getAdherenceMax() != null) {
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

    @Override
    public AdherenceStatistics getAdherenceStatistics(String appId, String studyId, Integer adherenceThreshold) {
        AdherenceStatistics stats = new AdherenceStatistics();
        
        List<AdherenceStatisticsEntry> entries = new ArrayList<>();
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(STATISTICS_SQL, "appId", appId, "studyId", studyId);
        List<Object[]> results = hibernateHelper.nativeQuery(builder.getQuery(), builder.getParameters());
        for (Object[] oneResult : results) {
            String searchableLabel = (String)oneResult[0];
            BigInteger totalActive = (BigInteger)oneResult[1];
            String rowsJson = (String)oneResult[2];
            WeeklyAdherenceReportRow row = findRow(rowsJson, searchableLabel);
            
            AdherenceStatisticsEntry entry = new AdherenceStatisticsEntry();
            entry.setLabel(row.getLabel());
            entry.setSearchableLabel(row.getSearchableLabel());
            entry.setSessionName(row.getSessionName());
            entry.setWeekInStudy(row.getWeekInStudy());
            entry.setStudyBurstId(row.getStudyBurstId());
            entry.setStudyBurstNum(row.getStudyBurstNum());
            entry.setTotalActive(totalActive.intValue());
            entries.add(entry);
        }
        
        builder = new QueryBuilder();
        builder.append(ALL_ACTIVE_SQL, "appId", appId, "studyId", studyId);
        Integer total = getCount(builder);

        builder = new QueryBuilder();
        builder.append(UNDER_THRESHOLD_SQL, "appId", appId, "studyId", studyId, "threshold", adherenceThreshold);
        Integer noncompliant = getCount(builder);
        
        Integer compliant = (total != null && noncompliant != null) ? total - noncompliant : null;
        
        entries.sort(ENTRIES_COMPARATOR);
        
        stats.setAdherenceThresholdPercentage(adherenceThreshold);
        stats.setNoncompliant(noncompliant);
        stats.setCompliant(compliant);
        stats.setTotalActive(total);
        stats.setEntries(entries);
        
        return stats;
    }
    
    private WeeklyAdherenceReportRow findRow(String rowsJson, String searchableLabel) {
        try {
            List<WeeklyAdherenceReportRow> rows = BridgeObjectMapper.get().readValue(rowsJson, ROWS_LIST);
            for (WeeklyAdherenceReportRow oneRow : rows) {
                if (oneRow.getSearchableLabel().equals(searchableLabel)) {
                    return oneRow;
                }
            }
        } catch (JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
        // The row JSON is a compound value and the searchableLabel must be one of the values in the row... this is
        // why it doesn't matter which set of rows we get, because we'l put that specific entry out. If it’s not 
        // there at all...something is not correct.
        throw new BridgeServiceException("Weekly report rows do not include searchableLabel: " + searchableLabel);
    }
    
    private Integer getCount(QueryBuilder builder) {
        try (Session session = sessionFactory.openSession()) {  
            @SuppressWarnings("unchecked")
            NativeQuery<Integer> query = session.createNativeQuery(builder.getQuery());
            query.addScalar("count", StandardBasicTypes.INTEGER);
            for (Map.Entry<String, Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            if (query.list().isEmpty()) {
                return null;
            }
            return ((List<Integer>)query.list()).get(0).intValue();
        }
    }
}    
