package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.reports.ReportData;

import java.util.Objects;

/**
 *
 */
@DynamoDBTable(tableName = "ParticipantState") //TODO: change name from config
public class DynamoParticipantState {
    //TODO: does this implement ReportData of ReportType "PARTICIPANT"?

    private String userId;
    private String configId;
    private String data;

    //TODO: figure out which methods require a @JsonIgnore annotation
    //TODO: figure out which methods require a @DynamoDBIgnore annotation
    public String getUserId () {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    //TODO: @JsonIgnore
    //TODO: @DynamoDBIgnore
    public String getConfigId() {
        return this.configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getData() {
        return this.data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoParticipantState that = (DynamoParticipantState) o;
        return Objects.equals(userId, that.userId) && Objects.equals(configId, that.configId) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, configId, data);
    }

    @Override
    public String toString() {
        return "DynamoParticipantState[" +
                "userId='" + userId +
                ", configId='" + configId +
                ", data='" + data +
                "}";
    }
}
