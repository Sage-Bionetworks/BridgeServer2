package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.models.ParticipantData;

import java.util.Objects;

/**
 *
 */
@DynamoDBTable(tableName = "ParticipantData")
public class DynamoParticipantData implements ParticipantData {

    private String healthCode;
    private String identifier;
    private JsonNode data;
    private Long version;

    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getHealthCode() {
        return this.healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
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
    //TODO is this a @DynamoDBAttribute?
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoParticipantData that = (DynamoParticipantData) o;
        return Objects.equals(healthCode, that.healthCode) && Objects.equals(identifier, that.identifier) &&
                Objects.equals(data, that.data) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(healthCode, identifier, data, version);
    }

    @Override
    public String toString() {
        return "DynamoParticipantData{" +
                "healthCode='" + healthCode +
                ", identifier='" + identifier +
                ", data='" + data +
                ", version=" + version +
                '}';
    }
}
