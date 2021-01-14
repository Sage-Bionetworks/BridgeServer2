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

    @JsonIgnore
    @DynamoDBIgnore
    @Override
    public String getUserId () {
        return this.userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    //TODO: equals, hashcode, toString once class is more finalized

    //TODO: organize imports once more finalized
}
