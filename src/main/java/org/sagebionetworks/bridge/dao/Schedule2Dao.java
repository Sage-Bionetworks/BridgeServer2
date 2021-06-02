package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public interface Schedule2Dao {
    
    PagedResourceList<Schedule2> getSchedules(String appId, 
            int offsetBy, int pageSize, boolean includeDeleted);
    
    PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, 
            String orgId, int offsetBy, int pageSize, boolean includeDeleted);

    Optional<Schedule2> getSchedule(String appId, String guid);
    
    Schedule2 createSchedule(Schedule2 schedule);
    
    Schedule2 updateSchedule(Schedule2 schedule);
    
    void deleteSchedule(Schedule2 schedule);
    
    void deleteSchedulePermanently(Schedule2 schedule);
    
    Optional<TimelineMetadata> getTimelineMetadata(String instanceGuid);
    
    List<TimelineMetadata> getAssessmentsForSessionInstance(String instanceGuid);
}
