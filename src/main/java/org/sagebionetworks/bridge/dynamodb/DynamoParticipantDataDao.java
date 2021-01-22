package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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

//    @Override
//    public ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String offsetKey, int pageSize) {
//        checkNotNull(userId);
//
//        DynamoParticipantData hashKey = new DynamoParticipantData();
//        hashKey.setUserId(userId);
//
//        DynamoDBQueryExpression<DynamoParticipantData> query =
//                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashKey);
//        List<DynamoParticipantData> results = mapper.query(DynamoParticipantData.class, query);
//
//        return new ForwardCursorPagedResourceList<DynamoParticipantData>(results, offsetKey);
//    }

    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String healthCode, String offsetKey, int pageSize) {
        checkNotNull(healthCode);

        int pageSizeWithIndicatorRecord = pageSize + 1;
        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setHealthCode(healthCode);

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
    public ParticipantData getParticipantDataRecord(final String healthCode, final String identifier) {
        checkNotNull(healthCode);
        checkNotNull(identifier);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setHealthCode(healthCode);
        hashKey.setIdentifier(identifier);

        return mapper.load(hashKey);
    }

    @Override
    public void saveParticipantData(ParticipantData data) {
        checkNotNull(data);
        mapper.save(data);
    }

    @Override
    public void deleteAllParticipantData(String healthCode) {
        checkNotNull(healthCode);

        DynamoParticipantData hashkey = new DynamoParticipantData();
        hashkey.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoParticipantData> query =
                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashkey);
        List<DynamoParticipantData> objectsToDelete = mapper.query(DynamoParticipantData.class, query);

        if (!objectsToDelete.isEmpty()) {
            List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(objectsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    @Override
    public void deleteParticipantData(String healthCode, String identifier) {
        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setHealthCode(healthCode);
        hashKey.setIdentifier(identifier);

        DynamoParticipantData participantDataRecord = mapper.load(hashKey);
        if (participantDataRecord != null) {
            mapper.delete(participantDataRecord);
        }
    }
}
