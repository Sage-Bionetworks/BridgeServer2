package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;

public interface Schedule2Dao {
    
    public PagedResourceList<Schedule2> getSchedules(String appId, 
            int offsetBy, int pageSize, boolean includeDeleted);
    
    public PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, 
            String ownerId, int offsetBy, int pageSize, boolean includeDeleted);

    public Optional<Schedule2> getSchedule(String appId, String guid);
    
    public Schedule2 createSchedule(Schedule2 schedule);
    
    public Schedule2 updateSchedule(Schedule2 schedule);
    
    public void deleteSchedule(Schedule2 schedule);
    
    public void deleteSchedulePermanently(Schedule2 schedule);
    
}
