package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class DynamoParticipantDataDao implements ParticipantDataDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantStateMapper")
    final void setParticipantStateMapper(DynamoDBMapper participantStateMapper) {
        this.mapper = participantStateMapper;
    }

    @Override
    public ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId,
                                                                                        String offsetKey, int pageSize) {
        checkNotNull(userId);
        checkNotNull(configId);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId);

        Condition configIdCondition = new Condition().withAttributeValueList(new AttributeValue(configId));
        // TODO: I need to check this, did I do this correctly?

        DynamoDBQueryExpression<DynamoParticipantData> query =
                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashKey)
                        .withRangeKeyCondition("configId", configIdCondition);
        List<DynamoParticipantData> results = mapper.query(DynamoParticipantData.class, query);

        return new ForwardCursorPagedResourceList<DynamoParticipantData>(results, offsetKey);
    }

    @Override
    public ForwardCursorPagedResourceList<ParticipantData> getParticipantDataV4(final String userId, final String configId,
                                                                   final String offsetKey, final int pageSize) {
        //TODO: why do we use final for these params but not the others?
        checkNotNull(userId);
        checkNotNull(configId);

        int pageSizeWithIndicatorRecord = pageSize + 1; //TODO: what is indicatorRecord?
        DynamoParticipantData hashKey = new DynamoParticipantData();

        DynamoDBQueryExpression<DynamoParticipantData> query = new DynamoDBQueryExpression<DynamoParticipantData>()
                .withHashKeyValues(hashKey)
                .withLimit(pageSizeWithIndicatorRecord);

        QueryResultPage<DynamoParticipantData> page = mapper.queryPage(DynamoParticipantData.class, query);

        List<ParticipantData> list = Lists.newArrayListWithCapacity(pageSizeWithIndicatorRecord);
        for (int i = 0, len = page.getResults().size(); i < len; i++) {
            ParticipantData oneParticipant = page.getResults().get(i);
            list.add(i, oneParticipant);
        }

        String nextPageOffsetKey = null;
        if (list.size() == pageSizeWithIndicatorRecord) {
            nextPageOffsetKey = Iterables.getLast(list).getConfigId(); // TODO: is this right? We want to get the range key essentially
        }

        int limit = Math.min(list.size(), pageSize);
        return new ForwardCursorPagedResourceList<ParticipantData>(list.subList(0, limit), nextPageOffsetKey);
        //TODO: is this the correct way of using the PagedResourceList?
    }

    @Override
    public void saveParticipantData(String data) {
        checkNotNull(data);
        mapper.save(data);
    }

    @Override
    public void deleteParticipantData(String userId, String configId) {
        checkNotNull(userId);
        checkNotNull(configId);

        DynamoParticipantData hashkey = new DynamoParticipantData();
        hashkey.setUserId(userId);
        hashkey.setConfigId(configId);

        DynamoParticipantData participantDataRecord = mapper.load(hashkey);
        if (participantDataRecord != null) {
            mapper.delete(participantDataRecord);
        }
    }

    //TODO: organize imports once more finalized
}
