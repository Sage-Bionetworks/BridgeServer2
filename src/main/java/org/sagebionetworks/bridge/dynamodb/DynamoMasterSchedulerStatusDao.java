package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.sagebionetworks.bridge.models.DateTimeHolder;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@Component
public class DynamoMasterSchedulerStatusDao implements MasterSchedulerStatusDao {
    
    static final String SCHEDULER_STATUS_HASH_KEY = "BridgeMasterScheduler";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "masterSchedulerStatusMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DateTimeHolder getLastProcessedTime() {
        DynamoMasterSchedulerStatus statusKey = new DynamoMasterSchedulerStatus();
        statusKey.setHashKey(SCHEDULER_STATUS_HASH_KEY);
        
        DynamoMasterSchedulerStatus statusMapper = mapper.load(statusKey);
        if (statusMapper == null) {
            return new DateTimeHolder(null);
        }
        return new DateTimeHolder(new DateTime(statusMapper.getLastProcessedTime()));
    }
}
