package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
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
    
    /**
     * Saves new or updates existing adherence record unless it does not have either a startedOn
     * date or a declined flag. If both startedOn and declined are missing, then a new record will
     * not be saved and any previously existing record will be deleted.
     * If the incoming record will overwrite a previously existing record, the earlier of the
     * two uploadedOn dates will be retained and all unique uploadIds will persist with the saved record.
     */
    @Override
    public void updateAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);
        
        // The record does not need to be persisted if there is no participant activity
        boolean deleteRecord = record.getStartedOn() == null && !record.isDeclined();
    
        // Check if there is an existing record.
        AdherenceRecordId id = new AdherenceRecordId(record.getUserId(), record.getStudyId(),
                record.getInstanceGuid(), record.getEventTimestamp(), record.getInstanceTimestamp());
        AdherenceRecord previousRecord = hibernateHelper.getById(AdherenceRecord.class, id);
        
        if (previousRecord != null) {
            // If the updated record does not have a startedOn date and is not declined,
            // then the previous record can be deleted.
            if (deleteRecord) {
                hibernateHelper.deleteById(AdherenceRecord.class, id);
                return;
            }
            
            // Persisted record keeps the earliest uploadedOn date.
            DateTime previousUploadedOn = previousRecord.getUploadedOn();
            if (previousUploadedOn != null && previousUploadedOn.isBefore(record.getUploadedOn())) {
                record.setUploadedOn(previousUploadedOn);
            }
            
            // Keep uploadIds from both the previous and new record.
            if (previousRecord.getUploadIds() != null) {
                for (String uploadId : previousRecord.getUploadIds()) {
                    record.addUploadId(uploadId);
                }
            }
        }
    
        if (!deleteRecord) {
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
        if (search.getUserId() != null) {
            where.appendRequired("ar.userId = :userId", "userId", search.getUserId());    
        }
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
        where.appendBoolean("declined", search.isDeclined());
        
        if (FALSE.equals(search.getIncludeRepeats())) {
            // This only works on records that have startedOn values...declined records can have a null
            // startedOn and will not appear in a search with includeRepeats=false.
            
            // userId has already been set above
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
