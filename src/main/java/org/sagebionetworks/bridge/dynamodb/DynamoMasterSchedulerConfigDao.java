package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.MasterSchedulerConfigDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.Lists;

@Component
public class DynamoMasterSchedulerConfigDao implements MasterSchedulerConfigDao {
    
    /**
     * DynamoDB save expression for conditional puts if and only if the row doesn't already exist. This save expression
     * is executed on the row that would be written to. We only need to check the hash key, since the entire row won't
     * exist (including both the hash key and the range key).
     */
    private static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpectedEntry("key", new ExpectedAttributeValue(false));
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "masterSchedulerConfigMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MasterSchedulerConfig> getAllSchedulerConfig() {
        DynamoMasterSchedulerConfig configKey = new DynamoMasterSchedulerConfig();
        // Mapper.scan
        DynamoDBQueryExpression<DynamoMasterSchedulerConfig> query = new DynamoDBQueryExpression<DynamoMasterSchedulerConfig>()
                .withHashKeyValues(configKey);
        PaginatedQueryList<DynamoMasterSchedulerConfig> results = mapper.query(DynamoMasterSchedulerConfig.class, query);
        
        List<MasterSchedulerConfig> list = Lists.newArrayListWithCapacity(results.size());
        for (DynamoMasterSchedulerConfig config : results) {
            list.add(config);
        }
        return null;
    }

    @Override
    public MasterSchedulerConfig getSchedulerConfig(String scheduleId) {
        checkArgument(isNotBlank(scheduleId));
        
        DynamoMasterSchedulerConfig configKey = new DynamoMasterSchedulerConfig();
        configKey.setScheduleId(scheduleId);
        
        return mapper.load(configKey);
    }

    @Override
    public void createSchedulerConfig(MasterSchedulerConfig config) {
        checkArgument(isNotBlank(config.getScheduleId()));
        DynamoMasterSchedulerConfig ddbConfig = (DynamoMasterSchedulerConfig) config;
        ddbConfig.setVersion(null);
        
        // Call DDB to create.
        try {
            mapper.save(ddbConfig, DOES_NOT_EXIST_EXPRESSION);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(ddbConfig);
        }
    }

    @Override
    public MasterSchedulerConfig updateSchedulerConfig(MasterSchedulerConfig config) {
        checkArgument(isNotBlank(config.getScheduleId()));
        
        try {
            mapper.save(config);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(config);
        }
        return config;
    }

    @Override
    public void deleteSchedulerConfig(String scheduleId) {
        checkArgument(isNotBlank(scheduleId));
        
        DynamoMasterSchedulerConfig configKey = new DynamoMasterSchedulerConfig();
        configKey.setScheduleId(scheduleId);
        
        mapper.delete(configKey);
    }
}
