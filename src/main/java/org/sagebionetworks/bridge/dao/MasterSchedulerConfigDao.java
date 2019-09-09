package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;

public interface MasterSchedulerConfigDao {
    
    /**
     * Get all the scheduler config objects.
     */
    List<MasterSchedulerConfig> getSchedulerConfig();
    
    /**
     * Get a specific scheduler configuration object.
     */
    public MasterSchedulerConfig getSchedulerConfig(String scheduleId);
    
    /**
     * Create a scheduler configuration object. If the object already exists, 
     * a copy will be created.
     */
    public MasterSchedulerConfig createSchedulerConfig(MasterSchedulerConfig config);
    
    /**
     * Update an existing scheduler config.
     */
    public MasterSchedulerConfig updateSchedulerConfig(MasterSchedulerConfig config);
    
    /**
     * Delete an individual scheduler config by marking it as deleted. The record 
     * will not be returned from the APIs but it is still in the database.
     */
    public void deleteSchedulerConfig(String scheduleId);
}
