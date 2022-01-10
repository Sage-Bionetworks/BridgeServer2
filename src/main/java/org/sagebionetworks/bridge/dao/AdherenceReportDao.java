package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;

public interface AdherenceReportDao {

    public void saveWeeklyAdherenceReport(WeeklyAdherenceReport report);
    
}
