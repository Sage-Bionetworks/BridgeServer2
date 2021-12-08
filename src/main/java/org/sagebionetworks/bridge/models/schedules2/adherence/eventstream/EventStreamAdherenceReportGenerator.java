package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toMap;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateSessionState;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLIANT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NONCOMPLIANT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNKNOWN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.google.common.collect.Lists;

public class EventStreamAdherenceReportGenerator {

    private final boolean showActive;
    private final DateTime now;
    protected final List<TimelineMetadata> metadata;
    private final Map<String, EventStream> reportsByEventId;
    protected final Map<String, Integer> daysSinceEventMap;
    protected final Map<String, DateTime> eventTimestampMap;
    protected final Map<String, AdherenceRecord> adherenceMap;

    public EventStreamAdherenceReportGenerator(EventStreamAdherenceReportGenerator.Builder builder) {
        this.showActive = builder.showActiveOnly;
        this.metadata = builder.metadata;
        this.daysSinceEventMap = new HashMap<>();
        this.eventTimestampMap = new HashMap<>();
        this.now = builder.now;
        for (StudyActivityEvent event : builder.events) {
            int daysSince = Days.daysBetween(
                    event.getTimestamp().withZone(builder.now.getZone()).toLocalDate(), 
                    builder.now.toLocalDate()).getDays();
            daysSinceEventMap.put(event.getEventId(), daysSince);
            eventTimestampMap.put(event.getEventId(), event.getTimestamp());
        }
        adherenceMap = builder.adherenceRecords.stream()
                .collect(toMap(AdherenceRecord::getInstanceGuid, (a) -> a));
        
        this.reportsByEventId = new HashMap<>();
    }
    
    public EventStreamAdherenceReport generate() {
        for (TimelineMetadata meta : metadata) {
            if (meta.isTimeWindowPersistent()) {
                continue;
            }
            int startDay = meta.getSessionInstanceStartDay();
            int endDay = meta.getSessionInstanceEndDay();
            String eventId = meta.getSessionStartEventId();
            Integer daysSinceEvent = daysSinceEventMap.get(eventId);
            
            // My concern here is that the event timestamp isn't localized, and so these dates
            // aren't localized, and could be confusing or wrong. We do want this in the user's
            // local timestamp, if we can figure that out.
            DateTime timestamp = eventTimestampMap.get(eventId);
            LocalDate localDate = (timestamp == null) ? null : timestamp.toLocalDate();
            LocalDate startDate = (localDate == null) ? null : localDate.plusDays(startDay);
            LocalDate endDate = (localDate == null) ? null : localDate.plusDays(endDay);
            
            // Skip entries that are not currently active, according to the server.
            if (showActive && (startDay > daysSinceEvent || endDay < daysSinceEvent)) {
                continue;
            }
            // We produce one report for each event ID. Create them lazily as we find them.
            EventStream report = reportsByEventId.get(eventId);
            if (report == null) {
                report = new EventStream();
                report.setStartEventId(eventId);
                report.setDaysSinceEvent(daysSinceEvent);
                report.setEventTimestamp(timestamp);
                report.setStudyBurstId(meta.getStudyBurstId());
                report.setStudyBurstNum(meta.getStudyBurstNum());
                reportsByEventId.put(eventId, report);
            }
            // Get the adherence information for this session instance, and from that, the state of the session
            AdherenceRecord record = adherenceMap.get(meta.getSessionInstanceGuid());
            SessionCompletionState state = calculateSessionState(record, startDay, endDay, daysSinceEvent);
            
            // Create the entry for this session, which is technically an entry for the window in the session
            EventStreamWindow windowEntry = new EventStreamWindow();
            windowEntry.setSessionInstanceGuid(meta.getSessionInstanceGuid());
            windowEntry.setTimeWindowGuid(meta.getTimeWindowGuid());
            windowEntry.setEndDay(endDay);
            windowEntry.setEndDate(endDate);
            windowEntry.setState(state);
            
            EventStreamDay eventStream = report.retrieveDay(meta);
            eventStream.setStartDay(startDay);
            eventStream.setStartDate(startDate);
            eventStream.addTimeWindow(windowEntry);
        }
        
        long compliantSessions = reportsByEventId.values().stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> COMPLIANT.contains(tw.getState()))
                .collect(counting());
        long noncompliantSessions = reportsByEventId.values().stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> NONCOMPLIANT.contains(tw.getState()))
                .collect(counting());
        long unkSessions = reportsByEventId.values().stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> UNKNOWN.contains(tw.getState()))
                .collect(counting());
        
        long totalSessions = compliantSessions + noncompliantSessions + unkSessions;
        
        float percentage = 1.0f;
        if (totalSessions > 0) {
            percentage = ((float)compliantSessions / (float)totalSessions);    
        }
        
        List<String> keysSorted = Lists.newArrayList(reportsByEventId.keySet());
        keysSorted.sort(String::compareToIgnoreCase);
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setActiveOnly(showActive);
        report.setTimestamp(now);
        report.setAdherencePercent((int)(percentage*100));
        for (String key : keysSorted) {
            report.getStreams().add(reportsByEventId.get(key));
        }
        // report.setStreams(ImmutableList.copyOf(reportsByEventId.values()));
        return report;
    }
    
    public static class Builder {
        private boolean showActiveOnly;
        private List<TimelineMetadata> metadata;
        private List<StudyActivityEvent> events;
        private List<AdherenceRecord> adherenceRecords;
        private DateTime now;
        
        public Builder withMetadata(List<TimelineMetadata> metadata) {
            this.metadata = metadata;
            return this;
        }
        public Builder withEvents(List<StudyActivityEvent> events) {
            this.events = events;
            return this;
        }
        public Builder withAdherenceRecords(List<AdherenceRecord> adherenceRecords) {
            this.adherenceRecords = adherenceRecords;
            return this;
        }
        public Builder withNow(DateTime now) {
            this.now = now;
            return this;
        }
        public Builder withShowActive(boolean showActiveOnly) {
            this.showActiveOnly = showActiveOnly;
            return this;
        }
        public EventStreamAdherenceReportGenerator build() {
            return new EventStreamAdherenceReportGenerator(this);
        }
    }
}
