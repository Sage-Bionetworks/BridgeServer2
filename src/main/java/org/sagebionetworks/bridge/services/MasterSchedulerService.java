package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.List;

import org.sagebionetworks.bridge.dao.MasterSchedulerConfigDao;
import org.sagebionetworks.bridge.dao.MasterSchedulerStatusDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.DateTimeHolder;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.validators.MasterSchedulerConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MasterSchedulerService {
    
    private MasterSchedulerConfigDao schedulerConfigDao;

    private MasterSchedulerStatusDao schedulerStatusDao;

    @Autowired
    final void setSchedulerConfigDao(MasterSchedulerConfigDao schedulerConfigDao) {
        this.schedulerConfigDao = schedulerConfigDao;
    }
    
    @Autowired
    final void setSchedulerStatusDao(MasterSchedulerStatusDao schedulerStatusDao) {
        this.schedulerStatusDao = schedulerStatusDao;
    }
    
    public List<MasterSchedulerConfig> getAllSchedulerConfigs() {
        return schedulerConfigDao.getAllSchedulerConfig();
    }

    public MasterSchedulerConfig createSchedulerConfig(MasterSchedulerConfig schedulerConfig) {
        checkNotNull(schedulerConfig);
        
        Validate.entityThrowingException(MasterSchedulerConfigValidator.INSTANCE, schedulerConfig);
        return schedulerConfigDao.createSchedulerConfig(schedulerConfig);
    }

    public MasterSchedulerConfig getSchedulerConfig(String scheduleId) {
        checkNotNull(scheduleId);
        
        MasterSchedulerConfig schedulerConfig = schedulerConfigDao.getSchedulerConfig(scheduleId);
        if (schedulerConfig == null) {
            throw new EntityNotFoundException(MasterSchedulerConfig.class);
        }
        
        return schedulerConfig;
    }

    public MasterSchedulerConfig updateSchedulerConfig(String scheduleId, MasterSchedulerConfig schedulerConfig) {
        checkNotNull(scheduleId);
        checkNotNull(schedulerConfig);
        
        schedulerConfig.setScheduleId(scheduleId);
        Validate.entityThrowingException(MasterSchedulerConfigValidator.INSTANCE, schedulerConfig);
        MasterSchedulerConfig saved = schedulerConfigDao.getSchedulerConfig(scheduleId);
        if (saved == null) {
            throw new EntityNotFoundException(MasterSchedulerConfig.class);
        }
        
        return schedulerConfigDao.updateSchedulerConfig(schedulerConfig);
    }

    public void deleteSchedulerConfig(String scheduleId) {
        checkNotNull(scheduleId);
        
        MasterSchedulerConfig schedulerConfig = schedulerConfigDao.getSchedulerConfig(scheduleId);
        if (schedulerConfig == null) {
            throw new EntityNotFoundException(MasterSchedulerConfig.class);
        }
        
        schedulerConfigDao.deleteSchedulerConfig(scheduleId);
    }
    
    public DateTimeHolder getSchedulerStatus() {
        return schedulerStatusDao.getLastProcessedTime();
    }
}
