package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public interface SchedulePlanDao {

    List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, String studyIdentifier, boolean includeDeleted);
    
    SchedulePlan getSchedulePlan(String studyIdentifier, String guid);
    
    SchedulePlan createSchedulePlan(String studyIdentifier, SchedulePlan plan);
    
    SchedulePlan updateSchedulePlan(String studyIdentifier, SchedulePlan plan);
    
    void deleteSchedulePlan(String studyIdentifier, String guid);
    
    void deleteSchedulePlanPermanently(String studyIdentifier, String guid);
    
}
