package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.List;

import javax.annotation.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

@Component
public class HibernateAdherenceRecordDao implements AdherenceRecordDao {
    
    static final String BASE_QUERY = "FROM AdherenceRecords AS ar "
        + "LEFT OUTER JOIN TimelineMetadata AS tm "
        + "ON ar.instanceGuid = tm.guid";
    static final String QUERY_BY_UPLOAD_ID = "FROM AdherenceRecords AS ar JOIN AdherenceUploads AS au " +
            "ON ar.userId = au.userId AND ar.studyId = au.studyID AND ar.instanceGuid = au.instanceGuid " +
            "AND ar.eventTimestamp = au.eventTimestamp AND ar.instanceTimestamp = au.instanceTimestamp " +
            "LEFT OUTER JOIN TimelineMetadata AS tm ON ar.instanceGuid = tm.guid";
    static final String UPLOAD_ID_SUBQUERY = "(SELECT COUNT(DISTINCT uploadId) FROM AdherenceUploads AS au " +
            "WHERE ar.userId = au.userId AND ar.studyId = au.studyId AND ar.instanceGuid = au.instanceGuid " +
            "AND ar.eventTimestamp = au.eventTimestamp AND ar.instanceTimestamp = au.instanceTimestamp)";
    static final String WHERE_HAS_MULTIPLE_UPLOAD_IDS = UPLOAD_ID_SUBQUERY + " > 1";
    static final String WHERE_HAS_NO_UPLOAD_IDS = UPLOAD_ID_SUBQUERY + " = 0";

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
            for (String uploadId : previousRecord.getUploadIds()) {
                record.addUploadId(uploadId);
            }

            // Merge old post-processing attributes. Note that in the call to JsonUtils.mergeNode, later nodes take
            // priority over earlier nodes in the list, so the new record will overwrite the old record for the keys
            // that they share, but won't erase keys that are only in the old record. This is the behavior we want.
            JsonNode mergedAttrNode = JsonUtils.mergeObjectNodes(previousRecord.getPostProcessingAttributes(),
                    record.getPostProcessingAttributes());
            record.setPostProcessingAttributes(mergedAttrNode);

            // If the new record doesn't have post-processing completed on or status, retain the old ones.
            if (record.getPostProcessingCompletedOn() == null) {
                record.setPostProcessingCompletedOn(previousRecord.getPostProcessingCompletedOn());
            }
            if (record.getPostProcessingStatus() == null) {
                record.setPostProcessingStatus(previousRecord.getPostProcessingStatus());
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

        // Special query for upload IDs.
        if (search.getUploadId() != null) {
            builder.append(QUERY_BY_UPLOAD_ID);
        } else {
            builder.append(BASE_QUERY);
        }

        WhereClauseBuilder where = builder.startWhere(AND);
        // Either app ID or user ID are required. If both are specified, we use the user ID.
        // Note that the validator ensures that at least one of these is specified.
        if (search.getUserId() != null) {
            where.appendRequired("ar.userId = :userId", "userId", search.getUserId());    
        } else if (search.getAppId() != null) {
            where.appendRequired("ar.appId = :appId", "appId", search.getAppId());
        } else {
            // The validator should catch this, but just in case...
            throw new BadRequestException("appId or userId is required");
        }
        where.appendRequired("ar.studyId = :studyId", "studyId", search.getStudyId());

        // If upload ID is specified, we use it to find the records.
        if (search.getUploadId() != null) {
            where.appendRequired("au.uploadId = :uploadId", "uploadId", search.getUploadId());
        }

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

        if (search.getEventTimestampStart() != null) {
            where.append("ar.eventTimestamp >= :eventTimestampStart", "eventTimestampStart",
                    search.getEventTimestampStart().getMillis());
        }
        if (search.getEventTimestampEnd() != null) {
            where.append("ar.eventTimestamp < :eventTimestampEnd", "eventTimestampEnd",
                    search.getEventTimestampEnd().getMillis());
        }

        if (search.hasMultipleUploadIds()) {
            where.append(WHERE_HAS_MULTIPLE_UPLOAD_IDS);
        } else if (search.hasNoUploadIds()) {
            where.append(WHERE_HAS_NO_UPLOAD_IDS);
        }

        // Note: This needs to be last, because any call to builder.append() will close out the where clause.
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
