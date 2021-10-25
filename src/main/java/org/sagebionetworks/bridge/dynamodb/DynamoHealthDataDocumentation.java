package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.HealthDataDocumentation}. */
@DynamoDBTable(tableName = "HealthDataDocumentation")
public class DynamoHealthDataDocumentation implements HealthDataDocumentation {
    private String title;
    private String parentId;
    private String identifier;
    private Long version;
    private String documentation;
    private String modifiedBy;
    private Long modifiedOn;

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
    @JsonIgnore
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

    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String getDocumentation() {
        return this.documentation;
    }

    @Override
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getModifiedOn() {
        return this.modifiedOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setModifiedOn(Long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}
