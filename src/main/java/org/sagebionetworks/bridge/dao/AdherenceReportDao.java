package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;

public interface AdherenceReportDao {

    void saveWeeklyAdherenceReport(WeeklyAdherenceReport report);
    
    PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            String labelFilter, Integer complianceUnder, Integer offsetBy, Integer pageSize);
    
}
