package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoMasterSchedulerStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

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
