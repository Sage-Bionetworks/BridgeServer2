package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static com.google.common.base.Preconditions.checkNotNull;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class EventStreamAdherenceReportGenerator {

    private final boolean showActive;
    private final DateTime now;
    private final List<TimelineMetadata> metadata;
    private final Map<String, EventStream> streamsByEventId;
    private final Map<String, EventStreamDay> streamsByStreamKey;
    private final Map<String, AdherenceRecord> adherenceMap;
    private final Map<String, Integer> daysSinceEventMap;
    private final Map<String, DateTime> eventTimestampMap;

    public EventStreamAdherenceReportGenerator(EventStreamAdherenceReportGenerator.Builder builder) {
        showActive = builder.showActiveOnly;
        now = builder.now;
        metadata = builder.metadata;
        streamsByEventId = new HashMap<>();
        daysSinceEventMap = new HashMap<>();
        eventTimestampMap = new HashMap<>();
        streamsByStreamKey = new HashMap<>();
        adherenceMap = builder.adherenceRecords.stream().collect(toMap(AdherenceRecord::getInstanceGuid, (a) -> a));
        for (StudyActivityEvent event : builder.events) {
            int daysSince = Days.daysBetween(
                    event.getTimestamp().withZone(builder.now.getZone()).toLocalDate(), 
                    builder.now.toLocalDate()).getDays();
            
            daysSinceEventMap.put(event.getEventId(), daysSince);
            eventTimestampMap.put(event.getEventId(), event.getTimestamp());
        }
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
            
            // NOTE: are these times in the right time zone?
            DateTime timestamp = eventTimestampMap.get(eventId);
            LocalDate localDate = (timestamp == null) ? null : timestamp.toLocalDate();
            LocalDate startDate = (localDate == null) ? null : localDate.plusDays(startDay);
            LocalDate endDate = (localDate == null) ? null : localDate.plusDays(endDay);
            
            // Skip entries that are not currently active, according to the server.
            if (showActive && (startDay > daysSinceEvent || endDay < daysSinceEvent)) {
                continue;
            }

            // We produce one report for each event ID. Create them lazily as we find them.
            EventStream stream = retrieveStream(eventId);
            // These should be same every time you add a day to the stream
            stream.setEventTimestamp(timestamp);
            stream.setDaysSinceEvent(daysSinceEvent);
            stream.setStudyBurstId(meta.getStudyBurstId());
            stream.setStudyBurstNum(meta.getStudyBurstNum());
            
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
            
            EventStreamDay eventStream = retrieveDay(stream, meta);
            eventStream.setStartDay(startDay);
            eventStream.setStartDate(startDate);
            eventStream.addTimeWindow(windowEntry);
        }
        
        long compliantSessions = streamsByEventId.values().stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> COMPLIANT.contains(tw.getState()))
                .collect(counting());
        long noncompliantSessions = streamsByEventId.values().stream()
                .flatMap(es -> es.getByDayEntries().values().stream())
                .flatMap(list -> list.stream())
                .flatMap(esd -> esd.getTimeWindows().stream())
                .filter(tw -> NONCOMPLIANT.contains(tw.getState()))
                .collect(counting());
        long unkSessions = streamsByEventId.values().stream()
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
        
        List<String> keysSorted = Lists.newArrayList(streamsByEventId.keySet());
        keysSorted.sort(String::compareToIgnoreCase);
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setActiveOnly(showActive);
        report.setTimestamp(now);
        report.setAdherencePercent((int)(percentage*100));
        for (String key : keysSorted) {
            report.getStreams().add(streamsByEventId.get(key));
        }
        return report;
    }
    
    private EventStream retrieveStream(String eventId) {
        EventStream stream = streamsByEventId.get(eventId);
        if (stream == null) {
            stream = new EventStream();
            stream.setStartEventId(eventId);
            streamsByEventId.put(eventId, stream);
        }
        return stream;
    }
    
    
    private EventStreamDay retrieveDay(EventStream stream, TimelineMetadata meta) {
        checkNotNull(meta.getSessionGuid());
        
        String eventId = checkNotNull(meta.getSessionStartEventId());
        int startDay = checkNotNull(meta.getSessionInstanceStartDay());
        
        String streamKey = String.format("%s:%s:%s", meta.getSessionGuid(), eventId, startDay);
        EventStreamDay eventStreamDay = streamsByStreamKey.get(streamKey);
        if (eventStreamDay == null) {
            eventStreamDay = new EventStreamDay();
            eventStreamDay.setSessionGuid(meta.getSessionGuid());
            eventStreamDay.setSessionName(meta.getSessionName());
            eventStreamDay.setSessionSymbol(meta.getSessionSymbol());
            eventStreamDay.setWeek(startDay/7);    
            eventStreamDay.setStudyBurstId(meta.getStudyBurstId());
            eventStreamDay.setStudyBurstNum(meta.getStudyBurstNum());
            streamsByStreamKey.put(streamKey, eventStreamDay);
            stream.addEntry(startDay, eventStreamDay);
        }
        return eventStreamDay;
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
            if (metadata == null) {
                metadata = ImmutableList.of();
            }
            if (events == null) {
                events = ImmutableList.of();
            }
            if (adherenceRecords == null) {
                adherenceRecords = ImmutableList.of();
            }
            return new EventStreamAdherenceReportGenerator(this);
        }
    }
}
