package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@Component
public class DynamoMasterSchedulerStatusDao implements MasterSchedulerStatusDao {
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "masterSchedulerStatusMapper")
    final void setMasterSchedulerStatusMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    // getLastProcessedTime
    @Override
    public Long getLastProcessedTime() {
        DynamoMasterSchedulerStatus statusKey = new DynamoMasterSchedulerStatus();
        statusKey.setHashKey("BridgeMasterSheduler");
        
        return mapper.load(statusKey).getLastProcessedTime();
    }
}
