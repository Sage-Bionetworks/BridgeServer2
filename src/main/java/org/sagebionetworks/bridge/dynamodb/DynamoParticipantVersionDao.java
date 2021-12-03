package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantVersionDao;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;

@Component
public class DynamoParticipantVersionDao implements ParticipantVersionDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantVersionDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void createParticipantVersion(ParticipantVersion participantVersion) {
        // Clear version to guarantee that we're creating a new row instead of potentially updating an existing one.
        DynamoParticipantVersion dynamoParticipantVersion = (DynamoParticipantVersion) participantVersion;
        dynamoParticipantVersion.setVersion(null);
        mapper.save(dynamoParticipantVersion);
    }

    @Override
    public void deleteParticipantVersionsForHealthCode(String appId, String healthCode) {
        // First, query the records we need to delete.
        List<ParticipantVersion> participantVersionsToDelete = getAllParticipantVersionsForHealthCode(appId,
                healthCode);

        // Next, batch delete.
        if (!participantVersionsToDelete.isEmpty()) {
            List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(participantVersionsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    @Override
    public List<ParticipantVersion> getAllParticipantVersionsForHealthCode(String appId, String healthCode) {
        DynamoParticipantVersion key = new DynamoParticipantVersion();
        key.setAppId(appId);
        key.setHealthCode(healthCode);

        DynamoDBQueryExpression<DynamoParticipantVersion> query = new DynamoDBQueryExpression<DynamoParticipantVersion>()
                .withHashKeyValues(key);
        List<DynamoParticipantVersion> participantVersionList = queryHelper(query);

        // Because of typing issues, we need to convert the list.
        return ImmutableList.copyOf(participantVersionList);
    }

    @Override
    public Optional<ParticipantVersion> getLatestParticipantVersionForHealthCode(String appId, String healthCode) {
        DynamoParticipantVersion key = new DynamoParticipantVersion();
        key.setAppId(appId);
        key.setHealthCode(healthCode);

        // We use queryPage() instead of query() because we only need 1 result and limit is per page.
        DynamoDBQueryExpression<DynamoParticipantVersion> query = new DynamoDBQueryExpression<DynamoParticipantVersion>()
                .withHashKeyValues(key).withScanIndexForward(false).withLimit(1);
        QueryResultPage<DynamoParticipantVersion> queryResultPage = mapper.queryPage(DynamoParticipantVersion.class,
                query);
        List<DynamoParticipantVersion> participantVersionList = queryResultPage.getResults();

        if (participantVersionList.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(participantVersionList.get(0));
        }
    }

    @Override
    public Optional<ParticipantVersion> getParticipantVersion(String appId, String healthCode,
            int participantVersion) {
        DynamoParticipantVersion key = new DynamoParticipantVersion();
        key.setAppId(appId);
        key.setHealthCode(healthCode);
        key.setParticipantVersion(participantVersion);

        return Optional.ofNullable(mapper.load(key));
    }

    // Helper method that wraps around mapper.query(). Because of typing issues, mapper.query() is hard to mock.
    List<DynamoParticipantVersion> queryHelper(DynamoDBQueryExpression<DynamoParticipantVersion> query) {
        return mapper.query(DynamoParticipantVersion.class, query);
    }
}
