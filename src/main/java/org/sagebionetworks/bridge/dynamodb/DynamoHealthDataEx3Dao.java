package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataEx3Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

@Component
public class DynamoHealthDataEx3Dao implements HealthDataEx3Dao {
    private DynamoDBMapper mapper;

    @Resource(name = "healthDataEx3DdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public HealthDataRecordEx3 createOrUpdateRecord(HealthDataRecordEx3 record) {
        DynamoHealthDataRecordEx3 dynamoRecord = (DynamoHealthDataRecordEx3) record;

        if (dynamoRecord.getId() == null) {
            // This record doesn't have its ID assigned yet (new record). Create an ID and assign it.
            dynamoRecord.setId(BridgeUtils.generateGuid());
        }

        // Save to DDB.
        mapper.save(dynamoRecord);
        return dynamoRecord;
    }

    @Override
    public void deleteRecordsForHealthCode(String healthCode) {
        // First, query the records we need to delete.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withConsistentRead(false).withIndexName(DynamoHealthDataRecordEx3.HEALTHCODE_CREATEDON_INDEX)
                .withHashKeyValues(key);
        List<DynamoHealthDataRecordEx3> recordsToDelete = queryHelper(query);

        // Next, batch delete.
        if (!recordsToDelete.isEmpty()) {
            List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(recordsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    @Override
    public Optional<HealthDataRecordEx3> getRecord(String id) {
        return Optional.ofNullable(mapper.load(DynamoHealthDataRecordEx3.class, id));
    }

    @Override
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForHealthCode(String healthCode,
            long createdOnStart, long createdOnEnd, int pageSize, String offsetKey) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setHealthCode(healthCode);

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withIndexName(DynamoHealthDataRecordEx3.HEALTHCODE_CREATEDON_INDEX).withHashKeyValues(key);
        return pagingHelper(query, createdOnStart, createdOnEnd, pageSize, offsetKey);
    }

    @Override
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForApp(String appId, long createdOnStart,
            long createdOnEnd, int pageSize, String offsetKey) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setAppId(appId);

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withIndexName(DynamoHealthDataRecordEx3.APPID_CREATEDON_INDEX).withHashKeyValues(key);
        return pagingHelper(query, createdOnStart, createdOnEnd, pageSize, offsetKey);
    }

    @Override
    public ForwardCursorPagedResourceList<HealthDataRecordEx3> getRecordsForAppAndStudy(String appId, String studyId,
            long createdOnStart, long createdOnEnd, int pageSize, String offsetKey) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setAppId(appId);
        key.setStudyId(studyId);

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withIndexName(DynamoHealthDataRecordEx3.APPSTUDYKEY_CREATEDON_INDEX).withHashKeyValues(key);
        return pagingHelper(query, createdOnStart, createdOnEnd, pageSize, offsetKey);
    }

    private ForwardCursorPagedResourceList<HealthDataRecordEx3> pagingHelper(
            DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query, long createdOnStart, long createdOnEnd,
            int pageSize, String offsetKey) {
        // offsetKey is a long epoch milliseconds using the createdOn index. The real createdOnStart is whichever is
        // the larger of the two.
        long indexStart = createdOnStart;
        if (offsetKey != null) {
            long offsetKeyMillis;
            try {
                offsetKeyMillis = Long.parseLong(offsetKey);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Invalid offsetKey " + offsetKey);
            }
            indexStart = Math.max(createdOnStart, offsetKeyMillis);
        }

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(indexStart)),
                        new AttributeValue().withN(String.valueOf(createdOnEnd)));
        query.withRangeKeyCondition("createdOn", rangeKeyCondition);

        // Limit is pageSize+1 so we can calculate the nextOffsetKey.
        query.setLimit(pageSize+1);

        // Can't do consistent reads with global secondary indices.
        query.setConsistentRead(false);

        // Query. Results should be sorted by createdOn, since this is the dynamo range key.
        List<DynamoHealthDataRecordEx3> dynamoRecordList = queryHelper(query);

        // Copy the list, because of generic typing reasons.
        List<HealthDataRecordEx3> recordList = ImmutableList.copyOf(dynamoRecordList);

        // Calculate the nextOffsetKey, if present. We'll know there's a next page if more than pageSize entries is
        // returned.
        String nextOffsetKey = null;
        if (recordList.size() > pageSize) {
            nextOffsetKey = String.valueOf(recordList.get(pageSize).getCreatedOn());
            recordList = recordList.subList(0, pageSize);
        }

        return new ForwardCursorPagedResourceList<>(recordList, nextOffsetKey);
    }

    // Helper method that wraps around mapper.query(). Because of typing issues, mapper.query() is hard to mock.
    List<DynamoHealthDataRecordEx3> queryHelper(DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query) {
        return mapper.query(DynamoHealthDataRecordEx3.class, query);
    }
}
