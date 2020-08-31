package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.files.ParticipantFile;

@DynamoDBTable(tableName = "ParticipantFiles")
public class DynamoParticipantFile implements ParticipantFile {
    private String fileId;
    private String userId;
    private DateTime createdOn;
    private String mimeType;
    private String appId;

    @JsonIgnore
    private String uploadUrl;
    @JsonIgnore
    private String downloadUrl;

    public DynamoParticipantFile() {
    }

    public DynamoParticipantFile(String userId, String fileId) {
        this.userId = userId;
        this.fileId = fileId;
    }

    /**
     * the file Id for this ParticipantFile.
     * Unique in the scope of the user (StudyParticipant).
     */
    @Override
    @DynamoDBRangeKey(attributeName = "fileId")
    public String getFileId() {
        return this.fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    /**
     * The StudyParticipant who owns this file.
     */
    @Override
    @DynamoDBHashKey(attributeName = "userId")
    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * The time when this file is created.
     */
    @Override
    @DynamoDBAttribute(attributeName = "createdOn")
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return this.createdOn;
    }

    @JsonDeserialize(using = DateTimeDeserializer.class)
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    @DynamoDBIgnore
    public String getDownloadUrl() {
        return this.downloadUrl;
    }

    @Override
    public void setDownloadUrl(String url) {
        this.downloadUrl = url;
    }

    @Override
    @DynamoDBIgnore
    public String getUploadUrl() {
        return this.uploadUrl;
    }

    @Override
    public void setUploadUrl(String url) {
        this.uploadUrl = url;
    }

    /**
     * The media type of this file.
     */
    @Override
    @DynamoDBAttribute(attributeName = "mimeType")
    public String getMimeType() {
        return this.mimeType;
    }

    @Override
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * The App ID of this file.
     */
    @Override
    @DynamoDBAttribute(attributeName = "appId")
    public String getAppId() { return this.appId; }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }
}
