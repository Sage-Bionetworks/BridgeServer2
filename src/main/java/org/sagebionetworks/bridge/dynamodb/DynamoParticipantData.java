package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

import java.util.Objects;

/**
 *
 */
@DynamoDBTable(tableName = "ParticipantData")
public class DynamoParticipantData {
    //TODO: does this implement ReportData of ReportType "PARTICIPANT"?

    private String key;
    private ReportDataKey reportDataKey;
    private String userId;
    private String configId;
    private String data;

    @JsonIgnore
    @DynamoDBIgnore
    public ReportDataKey getReportDataKey() {
        return reportDataKey;
    }

    public void setReportDataKey(ReportDataKey reportDataKey) {
        this.reportDataKey = reportDataKey;
    }

    @JsonIgnore
    @DynamoDBHashKey
    public String getKey() {
        // Transfer to the property that is persisted when it is requested.
        if (reportDataKey != null) {
            key = reportDataKey.getKeyString();
        }
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
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
}
