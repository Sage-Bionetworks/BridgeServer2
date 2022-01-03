package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateSessionState;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.AbstractAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.google.common.collect.Lists;

public class EventStreamAdherenceReportGenerator extends AbstractAdherenceReportGenerator {

    private EventStreamAdherenceReportGenerator(AbstractAdherenceReportGenerator.Builder builder) {
        super(builder);
    }

    public EventStreamAdherenceReport generate() {
        for (TimelineMetadata meta : metadata) {
            if (meta.isTimeWindowPersistent()) {
                continue;
            }
            int startDay = meta.getSessionInstanceStartDay();
            int endDay = meta.getSessionInstanceEndDay();
            String eventId = meta.getSessionStartEventId();
            Integer daysSinceEvent = daysSinceEventByEventId.get(eventId);

            DateTime timestamp = eventTimestampByEventId.get(eventId);
            LocalDate localDate = (timestamp == null) ? null : timestamp.toLocalDate();
            LocalDate startDate = (localDate == null) ? null : localDate.plusDays(startDay);
            LocalDate endDate = (localDate == null) ? null : localDate.plusDays(endDay);

            // Skip entries that are not currently active, according to the server.
            if (showActive && (daysSinceEvent == null || (startDay > daysSinceEvent || endDay < daysSinceEvent))) {
                continue;
            }

            // Produce one report for each event ID. Create them lazily as we find each eventId;
            EventStream stream = retrieveStream(eventId);
            // (These should be same every time you add a day to the stream.)
            stream.setEventTimestamp(timestamp);
            stream.setDaysSinceEvent(daysSinceEvent);
            stream.setStudyBurstId(meta.getStudyBurstId());
            stream.setStudyBurstNum(meta.getStudyBurstNum());

            // Get the adherence information for this session instance and derive the state of the session
            AdherenceRecord record = adherenceByInstanceGuid.get(meta.getSessionInstanceGuid());
            SessionCompletionState state = calculateSessionState(record, startDay, endDay, daysSinceEvent);

            // Retrieve the event stream. All items in this stream start on the same day, but can end on different days
            EventStreamDay eventStream = retrieveDay(stream, meta);
            eventStream.setStartDay(startDay);
            eventStream.setStartDate(startDate);

            // Create a window entry (windows are flattened in the list of timeline metadata records...all session
            // records in the metadata table are actually session window records)
            EventStreamWindow windowEntry = new EventStreamWindow();
            windowEntry.setSessionInstanceGuid(meta.getSessionInstanceGuid());
            windowEntry.setTimeWindowGuid(meta.getTimeWindowGuid());
            windowEntry.setEndDay(endDay);
            windowEntry.setEndDate(endDate);
            windowEntry.setState(state);
            eventStream.addTimeWindow(windowEntry);
        }

        int percentage = AdherenceUtils.calculateAdherencePercentage(streamsByEventId.values());

        List<String> keysSorted = Lists.newArrayList(streamsByEventId.keySet());
        keysSorted.sort(String::compareToIgnoreCase);

        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setActiveOnly(showActive);
        report.setTimestamp(now);
        report.setClientTimeZone(clientTimeZone);
        report.setAdherencePercent(percentage);
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

        String eventId = meta.getSessionStartEventId();
        int startDay = meta.getSessionInstanceStartDay();

        String streamKey = String.format("%s:%s:%s", meta.getSessionGuid(), eventId, startDay);
        EventStreamDay eventStreamDay = streamsByStreamKey.get(streamKey);
        if (eventStreamDay == null) {
            eventStreamDay = new EventStreamDay();
            eventStreamDay.setSessionGuid(meta.getSessionGuid());
            eventStreamDay.setSessionName(meta.getSessionName());
            eventStreamDay.setSessionSymbol(meta.getSessionSymbol());
            eventStreamDay.setWeek(startDay / 7);
            eventStreamDay.setStudyBurstId(meta.getStudyBurstId());
            eventStreamDay.setStudyBurstNum(meta.getStudyBurstNum());
            streamsByStreamKey.put(streamKey, eventStreamDay);
            stream.addEntry(startDay, eventStreamDay);
        }
        return eventStreamDay;
    }
    
    public static class Builder extends AbstractAdherenceReportGenerator.Builder {
        @SuppressWarnings("unchecked")
        public EventStreamAdherenceReportGenerator build() {
            return new EventStreamAdherenceReportGenerator(this);
        }
    }
}
