package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
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
        queryExpression.withKeyConditionExpression("userId = :val1")
                .withExpressionAttributeValues(keyCondition)
                .withLimit(pageSize)
                .setConsistentRead(true);
        if (offsetKey != null) {
            HashMap<String, AttributeValue> startKey = new HashMap<>();
            startKey.put("fileId", new AttributeValue().withS(offsetKey));
            queryExpression.withExclusiveStartKey(startKey);
        }

        PaginatedQueryList<DynamoParticipantFile> results = mapper.query(DynamoParticipantFile.class, queryExpression);
        // No lazy fetching; results.size() method will force the mapper to query all results up to pageSize.
        String nextPageOffsetKey = results.size() < pageSize ? null : results.get(pageSize - 1).getFileId();

        List<ParticipantFile> fileResults = results.stream().map(i -> (ParticipantFile) i).collect(Collectors.toList());
        return new ForwardCursorPagedResourceList<>(fileResults, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
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
}
