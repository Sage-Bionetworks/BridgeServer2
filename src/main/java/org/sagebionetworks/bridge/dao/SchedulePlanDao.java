package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public interface SchedulePlanDao {

    List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, String studyIdentifier, boolean includeDeleted);
    
    SchedulePlan getSchedulePlan(String appId, String guid);
    
    SchedulePlan createSchedulePlan(String appId, SchedulePlan plan);
    
    SchedulePlan updateSchedulePlan(String appId, SchedulePlan plan);
    
    void deleteSchedulePlan(String appId, String guid);
    
    void deleteSchedulePlanPermanently(String appId, String guid);
    
}
