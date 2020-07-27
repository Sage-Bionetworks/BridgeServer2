package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

import javax.annotation.Resource;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class DynamoParticipantFileDao implements ParticipantFileDao {
    private DynamoDBMapper mapper;

    @Resource(name = "participantFileDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PagedResourceList<ParticipantFile> getParticipantFiles(String userId, int start, int offset) {
        return null;
    }

    @Override
    public Optional<ParticipantFile> getParticipantFile(String userId, String fileId) {
        return Optional.empty();
    }

    @Override
    public void uploadParticipantFile(ParticipantFile file) {
        checkNotNull(file);
        checkArgument(isNotBlank(file.getUserId()));
        checkArgument(isNotBlank(file.getFileId()));
        mapper.save(file);
    }

    @Override
    public void deleteParticipantFile(String fileId, String userId) {
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
