package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.schedules.MasterSchedulerStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "MasterSchedulerStatus")
public class DynamoMasterSchedulerStatus implements MasterSchedulerStatus {
    
    private String hashKey;
    private Long lastProcessedTime;
    
    @DynamoDBHashKey
    public String getHashKey() {
        return hashKey;
    }
    
    @Override
    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }
    
    @Override
    public Long getLastProcessedTime() {
        return lastProcessedTime;
    }
    
    @Override
    public void setLastProcessedTime(Long lastProcessedTime) {
        this.lastProcessedTime = lastProcessedTime;
    }
}
