package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class DynamoParticipantDataDao implements ParticipantDataDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantDataMapper")
    final void setParticipantStateMapper(DynamoDBMapper participantStateMapper) {
        this.mapper = participantStateMapper;
    }

    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String userId, String offsetKey, int pageSize) {
        checkNotNull(userId);

        int pageSizeWithIndicatorRecord = pageSize + 1;
        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId);

        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.GE)
                .withAttributeValueList(new AttributeValue().withS(offsetKey));

        DynamoDBQueryExpression<DynamoParticipantData> query = new DynamoDBQueryExpression<DynamoParticipantData>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("offsetKey", rangeKeyCondition) //TODO is the name "offsetKey" okay?
                .withLimit(pageSizeWithIndicatorRecord);

        PaginatedQueryList<DynamoParticipantData> resultPage = mapper.query(DynamoParticipantData.class, query);

        List<ParticipantData> list = ImmutableList.copyOf(resultPage);

        String nextPageOffsetKey = null;
        if (list.size() == pageSizeWithIndicatorRecord) {
            nextPageOffsetKey = Iterables.getLast(list).getIdentifier();
        }

        int limit = Math.min(list.size(), pageSize);
        return new ForwardCursorPagedResourceList<ParticipantData>(list.subList(0, limit), nextPageOffsetKey);
    }

    @Override
    public ParticipantData getParticipantDataRecord(final String userId, final String identifier) {
        checkNotNull(userId);
        checkNotNull(identifier);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId);
        hashKey.setIdentifier(identifier);

        return mapper.load(hashKey);
    }

    @Override
    public void saveParticipantData(ParticipantData data) {
        checkNotNull(data);
        mapper.save(data);
    }

    @Override
    public void deleteAllParticipantData(String userId) {
        checkNotNull(userId);

        DynamoParticipantData hashkey = new DynamoParticipantData();
        hashkey.setUserId(userId);

        DynamoDBQueryExpression<DynamoParticipantData> query =
                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashkey);
        List<DynamoParticipantData> objectsToDelete = mapper.query(DynamoParticipantData.class, query);

        if (!objectsToDelete.isEmpty()) {
            List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(objectsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    @Override
    public void deleteParticipantData(String userId, String identifier) {
        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId);
        hashKey.setIdentifier(identifier);

        DynamoParticipantData participantDataRecord = mapper.load(hashKey);
        if (participantDataRecord != null) {
            mapper.delete(participantDataRecord);
        }
    }
}
