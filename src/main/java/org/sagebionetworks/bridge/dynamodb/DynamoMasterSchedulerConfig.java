package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("MasterSchedulerConfig")
@DynamoDBTable(tableName = "MasterSchedulerConfig")
public class DynamoMasterSchedulerConfig implements MasterSchedulerConfig {
    
    private String scheduleId;
    private String cronSchedule;
    private JsonNode requestTemplate;
    private String sqsQueueUrl;
    private Long version;
    
    @DynamoDBHashKey
    @Override
    public String getScheduleId() {
        return scheduleId;
    }
    @Override
    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }
    
    @Override
    public String getCronSchedule() {
        return cronSchedule;
    }
    
    @Override
    public void setCronSchedule(String cronSchedule) {
        this.cronSchedule = cronSchedule;
    }
    
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getRequestTemplate() {
        return requestTemplate;
    }
    
    @Override
    public void setRequestTemplate(JsonNode requestTemplate) {
        this.requestTemplate = requestTemplate;
    }
    
    @Override
    public String getSqsQueueUrl() {
        return sqsQueueUrl;
    }
    
    @Override
    public void setSqsQueueUrl(String sqsQueueUrl) {
        this.sqsQueueUrl = sqsQueueUrl;
    }
    
    /**
     * Version number of a particular scheduler config. This is used to detect concurrent modification. Callers should
     * not modify this value. This will be automatically incremented by Bridge.
     */
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}
