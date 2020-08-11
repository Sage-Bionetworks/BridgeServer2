package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

@Component
public class DynamoParticipantFileDao implements ParticipantFileDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantFileDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey, int pageSize) {
        checkNotNull(userId);
        checkArgument(isNotBlank(userId));

        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        ParticipantFile key = new DynamoParticipantFile(userId);
        DynamoDBQueryExpression<ParticipantFile> queryExpression = new DynamoDBQueryExpression<>();
        queryExpression.withHashKeyValues(key).withLimit(pageSize);
        if (offsetKey != null) {
            HashMap<String, AttributeValue> startKey = new HashMap<>();
            startKey.put("fileId", new AttributeValue().withS(offsetKey));
            queryExpression.withExclusiveStartKey(startKey);
        }

        PaginatedQueryList<ParticipantFile> results = mapper.query(ParticipantFile.class, queryExpression);
        // No lazy fetching; results.size() method will force the mapper to query all results up to pageSize.
        String nextPageOffsetKey = results.size() < pageSize ? null : results.get(pageSize - 1).getFileId();

        return new ForwardCursorPagedResourceList<>(results, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize);
    }

    @Override
    public Optional<ParticipantFile> getParticipantFile(String userId, String fileId) {
        checkNotNull(userId);
        checkNotNull(fileId);
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
        checkNotNull(fileId);
        checkNotNull(userId);
        checkArgument(isNotBlank(fileId));
        checkArgument(isNotBlank(userId));

        DynamoParticipantFile deleteTarget = new DynamoParticipantFile(fileId, userId);
        if (mapper.load(deleteTarget) != null) {
            mapper.delete(deleteTarget);
        }
    }
}
