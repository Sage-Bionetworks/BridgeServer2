package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoMasterSchedulerConfig;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("MasterSchedulerConfig")
@JsonDeserialize(as = DynamoMasterSchedulerConfig.class)
public interface MasterSchedulerConfig extends BridgeEntity {
    
    public static MasterSchedulerConfig create() {
        return new DynamoMasterSchedulerConfig();
    }
    
    /**
     * The unique schedule ID.
     */
    public String getScheduleId();
    public void setScheduleId(String scheduleId);
    
    /**
     * The cron expression indicating when the schedule should run.
     */
    public String getCronSchedule();
    public void setCronSchedule(String cronSchedule);
    
    /**
     * The template for the request to be sent.
     */
    public JsonNode getRequestTemplate();
    public void setRequestTemplate(JsonNode requestTemplate);
    
    /**
     * The SQS queue URL to send requests to.
     */
    public String getSqsQueueUrl();
    public void setSqsQueueUrl(String sqsQueueUrl);
}
