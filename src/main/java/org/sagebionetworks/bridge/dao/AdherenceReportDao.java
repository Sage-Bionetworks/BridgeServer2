package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceStatistics;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;

public interface AdherenceReportDao {

    void saveWeeklyAdherenceReport(WeeklyAdherenceReport report);
    
    PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            AdherenceReportSearch search);
    
    AdherenceStatistics getWeeklyAdherenceStatistics(String appId, String studyId, Integer adherenceThreshold);
    
}
