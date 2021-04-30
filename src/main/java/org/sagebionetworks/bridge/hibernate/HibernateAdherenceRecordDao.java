package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

@Component
public class HibernateAdherenceRecordDao implements AdherenceRecordDao {
    
    static final String BASE_QUERY = "FROM AdherenceRecords AS ar "
        + "LEFT OUTER JOIN TimelineMetadata AS tm "
        + "ON ar.instanceGuid = tm.guid "
        + "WHERE ar.userId = :userId AND ar.studyId = :studyId"; 

    
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public void updateAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);
        hibernateHelper.saveOrUpdate(record);    
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
        builder.append(BASE_QUERY, "userId", 
                search.getUserId(), "studyId", search.getStudyId());
        
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
            builder.append("AND ar.instanceGuid IN :instanceGuids", 
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
                    + "instanceGuid = ar.instanceGuid ORDER BY startedOn LIMIT 1)");
        }
        builder.alternativeMatchedPairs(search.getInstanceGuidStartedOnMap(), 
                "gd", "ar.instanceGuid", "ar.startedOn");
        builder.alternativeMatchedPairs(search.getEventTimestamps(), 
                "evt", "tm.sessionStartEventId", "ar.eventTimestamp");
        if (search.getRecordType() != null) {
            if (search.getRecordType() == SESSION) {
                builder.append("AND tm.assessmentGuid IS NULL");
            } else {
                builder.append("AND tm.assessmentGuid IS NOT NULL");
            }
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
        builder.append("ORDER BY ar.startedOn " + search.getSortOrder().name());
        return builder;
    }
}
