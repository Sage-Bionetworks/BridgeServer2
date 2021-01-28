package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.models.ParticipantData;

/**
 *
 */
@DynamoDBTable(tableName = "ParticipantData")
public class DynamoParticipantData implements ParticipantData {

    private String userId;
    private String identifier;
    private JsonNode data;
    private Long version;

    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getUserId() {
        return this.userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
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
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public JsonNode getData() {
        return this.data;
    }

    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }

    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "DynamoParticipantData{" +
                "userId='" + userId +
                ", identifier='" + identifier +
                ", data='" + data +
                ", version=" + version +
                '}';
    }
}
