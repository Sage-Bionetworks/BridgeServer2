package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
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
    public PagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId) {
        checkNotNull(userId);
        checkNotNull(configId);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId + configId);
        // TODO: in DynamoReportData, a ReportDataKey is used. Here, is it sufficient to use userId? If so, remove configId from params or add to hashKey

        DynamoDBQueryExpression<DynamoParticipantData> query =
                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashKey);
        List<DynamoParticipantData> results = mapper.query(DynamoParticipantData.class, query);

        return new PagedResourceList<DynamoParticipantData>(results, results.size());
        //TODO: is this the correct way of using the PagedResourceList?
    }

    @Override
    public PagedResourceList<ParticipantData> getParticipantDataV4(final String userId, final String configId,
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

        int limit = Math.min(list.size(), pageSize);
        return new PagedResourceList<ParticipantData>(list.subList(0, limit), limit);
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
