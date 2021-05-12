package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.HealthDataDocumentation}. */
@DynamoDBTable(tableName = "HealthDataDocumentation")
public class DynamoHealthDataDocumentation implements HealthDataDocumentation {
    String title;
    String parentId;
    String identifier;
    Long version;
    String s3Key;
    String createdBy;
    DateTime createdOn;
    String modifiedBy;
    DateTime modifiedOn;

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @DynamoDBHashKey
    @Override
    public String getParentId() {
        return this.parentId;
    }

    @Override
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @DynamoDBRangeKey
    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public Long getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String getS3Key() {
        return this.s3Key;
    }

    @Override
    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    @Override
    public String getCreatedBy() {
        return this.createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public DateTime getCreatedOn() {
        return this.createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public String getModifiedBy() {
        return this.modifiedBy;
    }

    @Override
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    @Override
    public DateTime getModifiedOn() {
        return this.modifiedOn;
    }

    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}
