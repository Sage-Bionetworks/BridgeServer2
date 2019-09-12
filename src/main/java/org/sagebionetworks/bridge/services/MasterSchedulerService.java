package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.dao.MasterSchedulerConfigDao;
import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.validators.MasterSchedulerConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MasterSchedulerService {
    
    private MasterSchedulerConfigDao schedulerConfigDao;

    @SuppressWarnings("unused")
    private MasterSchedulerStatusDao schedulerStatusDao;

    @Autowired
    public final void setSchedulerConfigDao(MasterSchedulerConfigDao schedulerConfigDao) {
        this.schedulerConfigDao = schedulerConfigDao;
    }
    
    @Autowired
    public final void setSchedulerStatusDao(MasterSchedulerStatusDao schedulerStatusDao) {
        this.schedulerStatusDao = schedulerStatusDao;
    }
    
    public List<MasterSchedulerConfig> getAllSchedulerConfigs() {
        return schedulerConfigDao.getAllSchedulerConfig();
    }

    public MasterSchedulerConfig createSchedulerConfig(MasterSchedulerConfig schedulerConfig) {
        checkNotNull(schedulerConfig.getScheduleId());
        
        Validate.entityThrowingException(MasterSchedulerConfigValidator.INSTANCE, schedulerConfig);
        return schedulerConfigDao.createSchedulerConfig(schedulerConfig);
    }

    public MasterSchedulerConfig getSchedulerConfig(String scheduleId) {
        checkNotNull(scheduleId);
        
        MasterSchedulerConfig schedulerConfig = schedulerConfigDao.getSchedulerConfig(scheduleId);
        if (schedulerConfig == null) {
            throw new BadRequestException("Can't get scheduler config for scheduleId=" + scheduleId 
                    + ": scheduler does not exists");
        }
        
        return schedulerConfig;
    }

    public MasterSchedulerConfig updateSchedulerConfig(String scheduleId, MasterSchedulerConfig schedulerConfig) {
        checkNotNull(scheduleId);
        checkNotNull(schedulerConfig);
        
        Validate.entityThrowingException(MasterSchedulerConfigValidator.INSTANCE, schedulerConfig);
        
        return schedulerConfigDao.updateSchedulerConfig(schedulerConfig);
    }

    public void deleteSchedulerConfig(String scheduleId) {
        checkNotNull(scheduleId);
        
        schedulerConfigDao.deleteSchedulerConfig(scheduleId);
    }
}
