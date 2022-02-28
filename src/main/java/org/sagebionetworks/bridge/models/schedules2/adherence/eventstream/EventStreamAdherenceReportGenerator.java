package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateProgress;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateSessionState;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.DayRange;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public class EventStreamAdherenceReportGenerator {
    
    public static final EventStreamAdherenceReportGenerator INSTANCE = new EventStreamAdherenceReportGenerator();

    private static final LocalDate EARLIEST_LOCAL_DATE = LocalDate.parse("1900-01-01");
    private static final LocalDate LATEST_LOCAL_DATE = LocalDate.parse("9999-12-31");

    public EventStreamAdherenceReport generate(AdherenceState state) {
        
        LocalDate earliestDate = LATEST_LOCAL_DATE;
        LocalDate latestDate = EARLIEST_LOCAL_DATE;
        
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (TimelineMetadata meta : state.getMetadata()) {
            if (meta.isTimeWindowPersistent()) {
                continue;
            }
            int startDay = meta.getSessionInstanceStartDay();
            int endDay = meta.getSessionInstanceEndDay();
            String eventId = meta.getSessionStartEventId();
            Integer daysSinceEvent = state.getDaysSinceEventById(eventId);

            DateTime timestamp = state.getEventTimestampById(eventId);
            LocalDate localDate = (timestamp == null) ? null : timestamp.toLocalDate();
            LocalDate startDate = (localDate == null) ? null : localDate.plusDays(startDay);
            LocalDate endDate = (localDate == null) ? null : localDate.plusDays(endDay);

            // Produce one report for each event ID. Create them lazily as we find each eventId;
            EventStream stream = state.getEventStreamById(eventId);
            stream.setDaysSinceEvent(daysSinceEvent);
            stream.setStudyBurstId(meta.getStudyBurstId());
            stream.setStudyBurstNum(meta.getStudyBurstNum());

            // Get the adherence information for this session instance and derive the state of the session
            AdherenceRecord record = state.getAdherenceRecordByGuid(meta.getSessionInstanceGuid());
            SessionCompletionState sessionState = calculateSessionState(record, startDay, endDay, daysSinceEvent);

            // Retrieve the event stream. All items in this stream start on the same day, but can end on different days
            EventStreamDay eventStreamDay = state.getEventStreamDayByKey(meta);
            eventStreamDay.setStartDay(startDay);
            eventStreamDay.setStartDate(startDate);

            // Create a window entry (windows are flattened in the list of timeline metadata records...all session
            // records in the metadata table are actually session window records)
            EventStreamWindow windowEntry = new EventStreamWindow();
            windowEntry.setSessionInstanceGuid(meta.getSessionInstanceGuid());
            windowEntry.setTimeWindowGuid(meta.getTimeWindowGuid());
            windowEntry.setEndDay(endDay);
            windowEntry.setEndDate(endDate);
            windowEntry.setState(sessionState);
            eventStreamDay.addTimeWindow(windowEntry);
            
            if (startDate != null && startDate.isBefore(earliestDate)) {
                earliestDate = startDate;
            }
            if (endDate != null && endDate.isAfter(latestDate)) {
                latestDate = endDate;
            }
            if (min > startDay) {
                min = startDay;
            }
            if (max < endDay) {
                max = endDay;
            }
        }
        
        DayRange dayRange = null;
        if (min <= max) {
            dayRange = new DayRange(min, max);
        }
        DateRange dateRange = null;
        if (earliestDate.isBefore(latestDate)) {
            dateRange = new DateRange(earliestDate, latestDate);
        }
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setTimestamp(state.getNow());
        report.setClientTimeZone(state.getClientTimeZone());
        report.setAdherencePercent(state.calculateAdherencePercentage());
        report.setDayRangeOfAllStreams(dayRange);
        report.setDateRangeOfAllStreams(dateRange);
        for (String eventId : state.getStreamEventIds()) {
            report.getStreams().add(state.getEventStreamById(eventId));
        }
        report.setProgression(calculateProgress(state, report.getStreams()));
        return report;
    }
}
