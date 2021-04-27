package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

@Component
public class HibernateAdherenceRecordDao implements AdherenceRecordDao {
    
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public AdherenceRecord get(AdherenceRecord record) {
        AdherenceRecordId key = new AdherenceRecordId(record.getUserId(),
                record.getStudyId(), record.getInstanceGuid(), record.getStartedOn());
        
        return hibernateHelper.getById(AdherenceRecord.class, key);
    }

    @Override
    public void create(AdherenceRecord record) {
        hibernateHelper.create(record);
    }

    @Override
    public void update(AdherenceRecord record) {
        hibernateHelper.update(record);
    }

    @Override
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(AdherenceRecordsSearch search) {

        QueryBuilder builder = createQuery(search);
        
        List<AdherenceRecord> records = hibernateHelper.nativeQueryGet(
                "SELECT * " + 
                    builder.getQuery(), builder.getParameters(), 
                search.getOffsetBy(), search.getPageSize(), AdherenceRecord.class);
        
        int total = hibernateHelper.nativeQueryCount(
                "SELECT count(*) " + builder.getQuery(), builder.getParameters());
        
        return new PagedResourceList<>(records, total, true);
    }

    protected QueryBuilder createQuery(AdherenceRecordsSearch search) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM AdherenceRecords AS ar");
        builder.append("LEFT OUTER JOIN TimelineMetadata AS tm");
        builder.append("ON ar.guid = tm.guid");
        builder.append("WHERE ar.userId = :userId AND ar.studyId = :studyId", 
                "userId", search.getUserId(),
                "studyId", search.getStudyId());
        
        // Note that these IDs can be the same in shared vs. local apps. Do we care?
        if (!search.getAssessmentIds().isEmpty()) {
            builder.append("AND tm.assessmentId IN :assessmentIds", 
                    "assessmentIds", search.getAssessmentIds());
        }
        if (!search.getSessionGuids().isEmpty()) {
            builder.append("AND tm.sessionGuid IN :sessionGuids", 
                    "sessionGuids", search.getSessionGuids());
        }
        if (!search.getInstanceGuids().isEmpty()) {
            builder.append("AND ar.guid IN :instanceGuids", 
                    "instanceGuids", search.getInstanceGuids());
        }
        if (!search.getTimeWindowGuids().isEmpty()) {
            builder.append("AND tm.timeWindowGuid IN :timeWindowGuids",
                    "timeWindowGuids", search.getTimeWindowGuids());
        }
        if (FALSE.equals(search.getIncludeRepeats())) {
            // userId has already been set above
            builder.append("AND ar.startedOn = (SELECT startedOn FROM "
                    + "AdherenceRecords WHERE userId = :userId AND "
                    + "guid = ar.guid ORDER BY startedOn LIMIT 1)");
        }
        if (!search.getEventTimestamps().isEmpty()) {
            builder.eventTimestamps(search.getEventTimestamps());
        }
        if (search.getStartTime() != null) {
            builder.append("AND ar.startedOn >= :startTime", 
                    "startTime", search.getStartTime().getMillis());
        } else {
            // filter out marker records, which the above search will also do
            // (we validate the startTime is 2020 or later)
            builder.append("AND ar.startedOn > 0");
        }
        if (search.getEndTime() != null) {
            builder.append("AND ar.startedOn <= :endTime", 
                    "endTime", search.getEndTime().getMillis());
        }
        if (search.getRecordType() != null) {
            if (search.getRecordType() == SESSION) {
                builder.append("AND tm.assessmentGuid IS NULL");
            } else if (search.getRecordType() == ASSESSMENT) {
                builder.append("AND tm.assessmentGuid IS NOT NULL");
            }
        }
        builder.append("ORDER BY ar.startedOn " + search.getSortOrder().name());
        return builder;
    }
}
