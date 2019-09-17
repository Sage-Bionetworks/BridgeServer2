package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;

public interface MasterSchedulerConfigDao {
    
    /**
     * Get all the scheduler config objects.
     */
    List<MasterSchedulerConfig> getAllSchedulerConfig();
    
    /**
     * Get a specific scheduler configuration object.
     */
    public MasterSchedulerConfig getSchedulerConfig(String scheduleId);
    
    /**
     * Create a scheduler configuration object. Cannot create new configs with 
     * duplicate scheduleId.
     */
    public MasterSchedulerConfig createSchedulerConfig(MasterSchedulerConfig config);
    
    /**
     * Update an existing scheduler config.
     */
    public MasterSchedulerConfig updateSchedulerConfig(MasterSchedulerConfig config);
    
    /**
     * Delete an individual scheduler config.
     */
    public void deleteSchedulerConfig(String scheduleId);
}
