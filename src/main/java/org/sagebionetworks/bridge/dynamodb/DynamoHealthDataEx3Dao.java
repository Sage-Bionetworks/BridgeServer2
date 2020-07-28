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
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

@Component
public class DynamoHealthDataEx3Dao implements HealthDataEx3Dao {
    private DynamoDBMapper mapper;

    @Resource(name = "healthDataEx3DdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
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
    public List<HealthDataRecordEx3> getRecordsForHealthCode(String healthCode, long createdOnStart,
            long createdOnEnd) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setHealthCode(healthCode);

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(createdOnStart)),
                        new AttributeValue().withN(String.valueOf(createdOnEnd)));

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withConsistentRead(false).withIndexName(DynamoHealthDataRecordEx3.HEALTHCODE_CREATEDON_INDEX)
                .withHashKeyValues(key).withRangeKeyCondition("createdOn", rangeKeyCondition);
        List<DynamoHealthDataRecordEx3> recordList = queryHelper(query);

        return ImmutableList.copyOf(recordList);
    }

    @Override
    public List<HealthDataRecordEx3> getRecordsForApp(String appId, long createdOnStart, long createdOnEnd) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setAppId(appId);

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(createdOnStart)),
                        new AttributeValue().withN(String.valueOf(createdOnEnd)));

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withConsistentRead(false).withIndexName(DynamoHealthDataRecordEx3.APPID_CREATEDON_INDEX)
                .withHashKeyValues(key).withRangeKeyCondition("createdOn", rangeKeyCondition);
        List<DynamoHealthDataRecordEx3> recordList = queryHelper(query);

        return ImmutableList.copyOf(recordList);
    }

    @Override
    public List<HealthDataRecordEx3> getRecordsForAppAndStudy(String appId, String studyId, long createdOnStart,
            long createdOnEnd) {
        // Hash key.
        DynamoHealthDataRecordEx3 key = new DynamoHealthDataRecordEx3();
        key.setAppId(appId);
        key.setStudyId(studyId);

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(createdOnStart)),
                        new AttributeValue().withN(String.valueOf(createdOnEnd)));

        // Query.
        DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query = new DynamoDBQueryExpression<DynamoHealthDataRecordEx3>()
                .withConsistentRead(false).withIndexName(DynamoHealthDataRecordEx3.APPSTUDYKEY_CREATEDON_INDEX)
                .withHashKeyValues(key).withRangeKeyCondition("createdOn", rangeKeyCondition);
        List<DynamoHealthDataRecordEx3> recordList = queryHelper(query);

        return ImmutableList.copyOf(recordList);
    }

    // Helper method that wraps around mapper.query(). Because of typing issues, mapper.query() is hard to mock.
    List<DynamoHealthDataRecordEx3> queryHelper(DynamoDBQueryExpression<DynamoHealthDataRecordEx3> query) {
        return mapper.query(DynamoHealthDataRecordEx3.class, query);
    }
}
