package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.MasterSchedulerConfigDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;

@Component
public class DynamoMasterSchedulerConfigDao implements MasterSchedulerConfigDao {
    
    /**
     * DynamoDB save expression for conditional puts if and only if the row doesn't already exist. This save expression
     * is executed on the row that would be written to. We only need to check the hash key, since the entire row won't
     * exist.
     */
    static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpectedEntry("scheduleId", new ExpectedAttributeValue(false));
    
    static final String SCHEDULE_ID = "scheduleId";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "masterSchedulerConfigMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MasterSchedulerConfig> getAllSchedulerConfig() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression().withConsistentRead(true);
        
        List<DynamoMasterSchedulerConfig> mappings = mapper.scan(DynamoMasterSchedulerConfig.class, scan);
        
        return new ArrayList<MasterSchedulerConfig>(mappings);
    }

    @Override
    public MasterSchedulerConfig getSchedulerConfig(String scheduleId) {
        checkArgument(isNotBlank(scheduleId));
        
        DynamoMasterSchedulerConfig configKey = new DynamoMasterSchedulerConfig();
        configKey.setScheduleId(scheduleId);
        
        return mapper.load(configKey);
    }

    @Override
    public MasterSchedulerConfig createSchedulerConfig(MasterSchedulerConfig config) {
        checkNotNull(config);
        checkArgument(isNotBlank(config.getScheduleId()));
        
        DynamoMasterSchedulerConfig ddbConfig = (DynamoMasterSchedulerConfig) config;
        ddbConfig.setVersion(null);
        
        // Call DDB to create.
        try {
            mapper.save(ddbConfig, DOES_NOT_EXIST_EXPRESSION);
        } catch (ConditionalCheckFailedException ex) {
            throw new EntityAlreadyExistsException(MasterSchedulerConfig.class, SCHEDULE_ID, config.getScheduleId());
        }
        return config;
    }

    @Override
    public MasterSchedulerConfig updateSchedulerConfig(MasterSchedulerConfig config) {
        checkNotNull(config);
        checkArgument(isNotBlank(config.getScheduleId()));
        
        Map<String, ExpectedAttributeValue> map = ImmutableMap.of(
                SCHEDULE_ID, new ExpectedAttributeValue().withValue(new AttributeValue(config.getScheduleId())));
        DynamoDBSaveExpression doesExistExpression = new DynamoDBSaveExpression().withExpected(map);
        
        try {
            mapper.save(config, doesExistExpression);
        } catch (ConditionalCheckFailedException ex) {
            throw new EntityNotFoundException(MasterSchedulerConfig.class);
        }
        return config;
    }

    @Override
    public void deleteSchedulerConfig(String scheduleId) {
        checkArgument(isNotBlank(scheduleId));
        
        MasterSchedulerConfig config = getSchedulerConfig(scheduleId);
        if (config != null) {
            try {
                mapper.delete(config);
            } catch(ConditionalCheckFailedException e) {
                throw new ConcurrentModificationException(config);
            }
        }
        
    }
}
