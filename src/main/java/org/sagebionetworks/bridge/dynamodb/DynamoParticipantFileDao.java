package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class DynamoParticipantFileDao implements ParticipantFileDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantFileDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey, int pageSize) {
        checkArgument(isNotBlank(userId));

        Map<String, AttributeValue> keyCondition = new HashMap<>();
        keyCondition.put(":val1", new AttributeValue().withS(userId));
        DynamoDBQueryExpression<DynamoParticipantFile> queryExpression = new DynamoDBQueryExpression<>();
        if (offsetKey != null) {
            keyCondition.put(":val2", new AttributeValue().withS(offsetKey));
            queryExpression.withKeyConditionExpression("userId = :val1 and fileId > :val2");
        } else {
            queryExpression.withKeyConditionExpression("userId = :val1");
        }
        queryExpression.withLimit(pageSize).withExpressionAttributeValues(keyCondition).withConsistentRead(true);

        PaginatedQueryList<DynamoParticipantFile> results = mapper.query(DynamoParticipantFile.class, queryExpression);
        List<ParticipantFile> fileResults = results.stream()
                .limit(pageSize).map(i -> (ParticipantFile) i).collect(Collectors.toList());
        String nextPageOffsetKey = null;
        if (fileResults.size() == pageSize) {
            nextPageOffsetKey = fileResults.get(fileResults.size() - 1).getFileId();
        }

        return new ForwardCursorPagedResourceList<>(fileResults, nextPageOffsetKey, true)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
    }

    @Override
    public List<ParticipantFile> getAllFilesForParticipant(String userId) {
        checkArgument(isNotBlank(userId));

        // Make hash key.
        DynamoParticipantFile hashKey = new DynamoParticipantFile(userId, null);
        DynamoDBQueryExpression<DynamoParticipantFile> queryExpression = new DynamoDBQueryExpression<>();
        queryExpression.setHashKeyValues(hashKey);

        // Query.
        PaginatedQueryList<DynamoParticipantFile> results = mapper.query(DynamoParticipantFile.class, queryExpression);

        // Convert to List<ParticipantFile> because of Java type weirdness.
        // Note that we use the stream API instead of ImmutableList.copyOf() because of wonkiness with mocking
        // mapper.query().
        //noinspection SimplifyStreamApiCallChains
        List<ParticipantFile> fileResults = results.stream().collect(Collectors.toList());
        return fileResults;
    }

    @Override
    public Optional<ParticipantFile> getParticipantFile(String userId, String fileId) {
        checkArgument(isNotBlank(userId));
        checkArgument(isNotBlank(fileId));

        ParticipantFile target = new DynamoParticipantFile(userId, fileId);
        target = mapper.load(target);
        if (target == null) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    @Override
    public void uploadParticipantFile(ParticipantFile file) {
        checkNotNull(file);
        checkArgument(isNotBlank(file.getUserId()));
        checkArgument(isNotBlank(file.getFileId()));
        mapper.save(file);
    }

    @Override
    public void deleteParticipantFile(String userId, String fileId) {
        checkArgument(isNotBlank(fileId));
        checkArgument(isNotBlank(userId));

        DynamoParticipantFile deleteTarget = new DynamoParticipantFile(userId, fileId);
        if (mapper.load(deleteTarget) != null) {
            mapper.delete(deleteTarget);
        }
    }

    @Override
    public void batchDeleteParticipantFiles(List<ParticipantFile> fileList) {
        checkNotNull(fileList);
        checkArgument(!fileList.isEmpty());

        List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(fileList);
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
