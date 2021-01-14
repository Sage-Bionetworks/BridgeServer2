package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

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

    //TODO: equals, hashcode, toString once class is more finalized

    //TODO: organize imports once more finalized
}
