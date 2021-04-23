package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
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
                record.getStudyId(), record.getGuid(), record.getStartedOn());
        
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
    public PagedResourceList<AdherenceRecord> getAdherenceRecords(
            Map<String,DateTime> events, AdherenceRecordsSearch search) {

        QueryBuilder builder = createQuery(events, search);
        
        List<AdherenceRecord> records = hibernateHelper.nativeQueryGet(
                "SELECT ar.* " + builder.getQuery(), builder.getParameters(), 
                search.getOffsetBy(), search.getPageSize(), AdherenceRecord.class);
        
        int total = hibernateHelper.nativeQueryCount(
                "SELECT count(*) " + builder.getQuery(), builder.getParameters());
        
        return new PagedResourceList<>(records, total, true);
    }

    protected QueryBuilder createQuery(Map<String, DateTime> events, AdherenceRecordsSearch search) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM AdherenceRecords AS ar");
        builder.append("LEFT OUTER JOIN TimelineMetadata AS tm");
        builder.append("ON ar.guid = tm.guid");
        builder.append("WHERE ar.userId = :userId", "userId", search.getUserId());
        builder.append("AND ar.studyId = :studyId", "studId", search.getStudyId());
        if (!search.getAssessmentIds().isEmpty()) {
            builder.append("AND tm.assessmentId IN :assessmentIds", 
                    "assessmentIds", search.getAssessmentIds());
        }
        if (!search.getSessionGuids().isEmpty()) {
            builder.append("AND tm.sessionGuid IN :sessionGuids", 
                    "sessionGuids", search.getSessionGuids());
        }
        if (!search.getInstanceGuids().isEmpty()) {
            builder.append("AND tm.guid IN :instanceGuids", 
                    "instanceGuids", search.getInstanceGuids());
        }
        if (FALSE.equals(search.getIncludeRepeats())) {
            builder.append("AND ar.startedOn = (SELECT startedOn FROM "
                    + "AdherenceRecords WHERE userId = :userId AND "
                    + "guid = ar.guid ORDER BY startedOn LIMIT 1");
        }
        // Note: if startEventId is set, then we only care about that timestamp, 
        // and don't need to add the whole map...we can make an optimization
        // we can make in that case.
        if (TRUE.equals(search.getCurrentTimeseriesOnly())) {
            List<String> phrases = new ArrayList<>();
            for (Map.Entry<String, DateTime> entry : events.entrySet()) {
                String id = entry.getKey();
                long ts = entry.getValue().getMillis();
                phrases.add(format("(tm.sessionStartEventId = '%s' AND ar.eventTimestamp = %s)", id, ts));
                String orPhrases = Joiner.on(" OR ").join(phrases);
                builder.append("AND (" + orPhrases + ")");
            }
        }
        // We will validate that all three required fields have been set
        if (search.getStartDay() != null) {
            builder.append("AND tm.sessionStartEventId = :eventId", 
                    "eventId", search.getStartEventId());
            builder.append("AND tm.sessionInstanceEndDay >= :startDay", 
                    "startDay", search.getStartDay());
            builder.append("AND tm.sessionInstanceStartDay <= :endDay", 
                    "endDay", search.getEndDay());
        }
        builder.append("ORDER BY ar.startedOn;");
        return builder;
    }
}
