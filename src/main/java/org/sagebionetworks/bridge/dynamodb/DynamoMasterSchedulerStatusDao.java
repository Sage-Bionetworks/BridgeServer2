package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.sagebionetworks.bridge.models.TimestampHolder;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@Component
public class DynamoMasterSchedulerStatusDao implements MasterSchedulerStatusDao {
    
    static final String SCHEDULER_STATUS_HASH_KEY = "BridgeMasterScheduler";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "masterSchedulerStatusMapper")
    final void setMasterSchedulerStatusMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TimestampHolder getLastProcessedTime() {
        DynamoMasterSchedulerStatus statusKey = new DynamoMasterSchedulerStatus();
        statusKey.setHashKey(SCHEDULER_STATUS_HASH_KEY);
        
        DynamoMasterSchedulerStatus status = mapper.load(statusKey);
        if (status == null) {
            return null;
        }
        return new TimestampHolder(status.getLastProcessedTime());
    }
}
