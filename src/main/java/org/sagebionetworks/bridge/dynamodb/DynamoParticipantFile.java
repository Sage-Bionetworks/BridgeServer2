package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

@DynamoDBTable(tableName = "ParticipantFiles")
public class DynamoParticipantFile implements ParticipantFile {
    /**
     * the file Id for this ParticipantFile.
     * Unique in the scope of the user (StudyParticipant).
     */
    private String fileId;

    /**
     * The StudyParticipant who owns this file.
     */
    private String userId;

    /**
     * The time when this file is created.
     */
    private DateTime createdOn;

    public DynamoParticipantFile(String fileId, String userId, DateTime createdOn) {
        this.fileId = fileId;
        this.userId = userId;
        this.createdOn = createdOn;
    }

    public DynamoParticipantFile(String fileId, String userId) {
        this(fileId, userId, null);
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    @DynamoDBRangeKey(attributeName = "fileId")
    public String getFileId() {
        return this.fileId;
    }

    @Override
    @DynamoDBHashKey(attributeName = "userId")
    public String getUserId() {
        return this.userId;
    }

    @Override
    @DynamoDBAttribute(attributeName = "createdOn")
    public DateTime getCreatedOn() {
        return this.createdOn;
    }
}
