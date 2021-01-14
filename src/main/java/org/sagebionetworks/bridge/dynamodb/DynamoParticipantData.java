package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.ParticipantData;

import java.util.Objects;

/**
 *
 */
@DynamoDBTable(tableName = "ParticipantData")
public class DynamoParticipantData implements ParticipantData {

    private String userId;
    private String configId;
    private String data;
    private Long version;

    //TODO: figure out which methods require a @JsonIgnore annotation
    //TODO: figure out which methods require a @DynamoDBIgnore annotation

    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getUserId () {
        return this.userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDBRangeKey
    @Override
    public String getConfigId() {
        return this.configId;
    }

    @Override
    public void setConfigId(String configId) {
        this.configId = configId;
    }

    @Override
    public String getData() {
        return this.data;
    }

    @Override
    public void setData(String data) {
        this.data = data;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoParticipantData that = (DynamoParticipantData) o;
        return Objects.equals(userId, that.userId) && Objects.equals(configId, that.configId) &&
                Objects.equals(data, that.data) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, configId, data, version);
    }

    @Override
    public String toString() {
        return "DynamoParticipantData{" +
                "userId='" + userId +
                ", configId='" + configId +
                ", data='" + data +
                ", version=" + version +
                '}';
    }

    //TODO: organize imports once more finalized
}
