package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoMasterSchedulerStatus;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("MasterSchedulerStatus")
@JsonDeserialize(as = DynamoMasterSchedulerStatus.class)
public interface MasterSchedulerStatus extends BridgeEntity {
    
    public static MasterSchedulerStatus create() {
        return new DynamoMasterSchedulerStatus();
    }
    
    /**
     * The hashkey is always BridgeMasterScheduler.
     */
    public String getHashKey();
    void setHashKey(String hashKey);
    
    /**
     * The last time the Master Scheduler ran.
     */
    public Long getLastProcessedTime();
    public void setLastProcessedTime(Long lastProcessedTime);


}
