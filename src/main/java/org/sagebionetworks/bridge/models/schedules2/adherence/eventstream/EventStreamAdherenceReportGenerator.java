package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateProgress;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateSessionState;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public class EventStreamAdherenceReportGenerator {
    
    public static final EventStreamAdherenceReportGenerator INSTANCE = new EventStreamAdherenceReportGenerator();

    public EventStreamAdherenceReport generate(AdherenceState state) {
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

            // Skip entries that are not currently active, according to the server.
            if (state.showActive() && (daysSinceEvent == null || (startDay > daysSinceEvent || endDay < daysSinceEvent))) {
                continue;
            }

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
        }
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setActiveOnly(state.showActive());
        report.setTimestamp(state.getNow());
        report.setClientTimeZone(state.getClientTimeZone());
        report.setAdherencePercent(state.calculateAdherencePercentage());
        for (String eventId : state.getStreamEventIds()) {
            report.getStreams().add(state.getEventStreamById(eventId));
        }
        report.setProgression(calculateProgress(state, report.getStreams()));
        return report;
    }
}
