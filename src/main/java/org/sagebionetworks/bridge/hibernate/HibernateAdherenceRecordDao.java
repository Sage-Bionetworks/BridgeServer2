package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

@Component
public class HibernateAdherenceRecordDao implements AdherenceRecordDao {
    
    static final String BASE_QUERY = "FROM AdherenceRecords AS ar "
        + "LEFT OUTER JOIN TimelineMetadata AS tm "
        + "ON ar.instanceGuid = tm.guid"; 

    
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public void updateAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);
        
        if (record.getStartedOn() == null && !record.isDeclined()) {
            AdherenceRecordId id = new AdherenceRecordId(record.getUserId(), record.getStudyId(),
                    record.getInstanceGuid(), record.getEventTimestamp(), record.getInstanceTimestamp());
            // Cannot delete if the record is already not there, so check for this.
            AdherenceRecord obj = hibernateHelper.getById(AdherenceRecord.class, id);
            if (obj != null) {
                hibernateHelper.deleteById(AdherenceRecord.class, id);    
            }
        } else {
            hibernateHelper.saveOrUpdate(record);    
        }
    }

    @Override
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(AdherenceRecordsSearch search) {
        checkNotNull(search);
        
        QueryBuilder builder = createQuery(search);
        
        List<AdherenceRecord> records = hibernateHelper.nativeQueryGet(
                "SELECT * " + builder.getQuery(), builder.getParameters(), 
                search.getOffsetBy(), search.getPageSize(), AdherenceRecord.class);

        int total = hibernateHelper.nativeQueryCount(
                "SELECT count(*) " + builder.getQuery(), builder.getParameters());

        return new PagedResourceList<>(records, total, true);
    }

    protected QueryBuilder createQuery(AdherenceRecordsSearch search) {
        QueryBuilder builder = new QueryBuilder();
        builder.append(BASE_QUERY);
        
        WhereClauseBuilder where = builder.startWhere(AND);
        where.appendRequired("ar.userId = :userId", "userId", search.getUserId());
        where.appendRequired("ar.studyId = :studyId", "studyId", search.getStudyId());
        
        // Note that by design, this finds both shared/local assessments with the
        // same ID
        if (!search.getAssessmentIds().isEmpty()) {
            where.append("tm.assessmentId IN :assessmentIds", 
                    "assessmentIds", search.getAssessmentIds());
        }
        if (!search.getSessionGuids().isEmpty()) {
            where.append("tm.sessionGuid IN :sessionGuids", 
                    "sessionGuids", search.getSessionGuids());
        }
        if (!search.getInstanceGuids().isEmpty()) {
            where.append("ar.instanceGuid IN :instanceGuids", 
                    "instanceGuids", search.getInstanceGuids());
        }
        if (!search.getTimeWindowGuids().isEmpty()) {
            where.append("tm.timeWindowGuid IN :timeWindowGuids",
                    "timeWindowGuids", search.getTimeWindowGuids());
        }
        if (FALSE.equals(search.getIncludeRepeats())) {
            // userId has already been set above
            // This doesn’t match records that don’t have startedOn values (like records that are declined),
            // leading to incorrect search results. This is an edge case of declined that comes up in the
            // search for adherence charts, but we don't need to fix it because that search is only for 
            // non-persistent sessions, and these cannot be repeated.
            where.append("ar.startedOn = (SELECT startedOn FROM "
                    + "AdherenceRecords WHERE userId = :userId AND "
                    + "instanceGuid = ar.instanceGuid ORDER BY startedOn "
                    + search.getSortOrder() + " LIMIT 1)");
        }
        where.alternativeMatchedPairs(search.getInstanceGuidStartedOnMap(), 
                "gd", "ar.instanceGuid", "ar.startedOn");
        where.alternativeMatchedPairs(search.getEventTimestamps(), 
                "evt", "tm.sessionStartEventId", "ar.eventTimestamp");
        where.adherenceRecordType(search.getAdherenceRecordType());
        if (search.getStartTime() != null) {
            where.append("ar.startedOn >= :startTime", 
                    "startTime", search.getStartTime().getMillis());
        }
        if (search.getEndTime() != null) {
            where.append("ar.startedOn <= :endTime", 
                    "endTime", search.getEndTime().getMillis());
        }
        builder.append("ORDER BY ar.startedOn " + search.getSortOrder().name());
        return builder;
    }

    @Override
    public void deleteAdherenceRecordPermanently(AdherenceRecord record) {
        checkNotNull(record);

        AdherenceRecordId id = new AdherenceRecordId(record.getUserId(), record.getStudyId(),
                record.getInstanceGuid(), record.getEventTimestamp(), record.getInstanceTimestamp());
        AdherenceRecord existingRecord = hibernateHelper.getById(AdherenceRecord.class, id);
        if (existingRecord != null) {
            hibernateHelper.deleteById(AdherenceRecord.class, id);
        }
    }
}
