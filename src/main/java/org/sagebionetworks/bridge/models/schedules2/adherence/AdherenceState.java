package org.sagebionetworks.bridge.models.schedules2.adherence;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public final class AdherenceState {

    private final DateTime now;
    private final String clientTimeZone;
    private final List<TimelineMetadata> metadata;
    private final List<StudyActivityEvent> events;
    private final List<AdherenceRecord> adherenceRecords;
    private final String studyStartEventId;
    
    private final Map<String, EventStream> streamsByEventId;
    private final Map<String, EventStreamDay> streamsByStreamKey;
    private final Map<String, AdherenceRecord> adherenceByGuid;
    private final Map<String, Integer> daysSinceEventByEventId;
    private final Map<String, DateTime> eventTimestampByEventId;
    private final DateTimeZone zone;
    
    public AdherenceState(AdherenceState.Builder builder) {
        // All times should be adjusted to the same time zone. This will be the participant’s 
        // declared time zone if we have it, otherwise it will be the server’s default time zone.
        // In general it doesn’t make a difference, with the exception of edge cases like daylight
        // savings time for short periods of time. We’d prefer to be accurate in the participant’s
        // local time zone if we can be.
        zone = builder.zone;
        now = builder.now.withZone(zone);
        LocalDate localNow = now.toLocalDate();
        
        studyStartEventId = builder.studyStartEventId;
        clientTimeZone = builder.clientTimeZone;
        metadata = builder.metadata;
        events = builder.events;
        adherenceRecords = builder.adherenceRecords;
        streamsByEventId = new HashMap<>();
        daysSinceEventByEventId = new HashMap<>();
        eventTimestampByEventId = new HashMap<>();
        streamsByStreamKey = new HashMap<>();
        
        adherenceByGuid = new HashMap<>();

        for (AdherenceRecord adherenceRecord : adherenceRecords) {
            adherenceByGuid.put(adherenceRecord.getInstanceGuid(), adherenceRecord);
        }
        
        for (StudyActivityEvent event : builder.events) {
            DateTime eventTimestamp = event.getTimestamp().withZone(zone);
            int daysSince = Days.daysBetween(eventTimestamp.toLocalDate(), localNow).getDays();
            
            daysSinceEventByEventId.put(event.getEventId(), daysSince);
            eventTimestampByEventId.put(event.getEventId(), eventTimestamp);
        }
    }

    // Make a clean copy, resetting all the caches.
    public AdherenceState.Builder toBuilder() {
        return new AdherenceState.Builder()
                .withMetadata(metadata)
                .withEvents(events)
                .withAdherenceRecords(adherenceRecords)
                .withNow(now)
                .withClientTimeZone(clientTimeZone)
                .withStudyStartEventId(studyStartEventId);
    }
    
    public List<TimelineMetadata> getMetadata() {
        return metadata;
    }
    // for tests, and not visible from generators
    List<StudyActivityEvent> getEvents() {
        return events;
    }
    // for tests, and not visible from generators
    List<AdherenceRecord> getAdherenceRecords() {
        return adherenceRecords;
    }
    public DateTime getNow() {
        return now;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public String getStudyStartEventId() {
        return studyStartEventId;
    }
    public EventStream getEventStreamById(String eventId) {
        EventStream stream = streamsByEventId.get(eventId);
        if (stream == null) {
            stream = new EventStream();
            stream.setStartEventId(eventId);
            stream.setEventTimestamp(eventTimestampByEventId.get(eventId));
            streamsByEventId.put(eventId, stream);
        }
        return stream;
    }
    public EventStreamDay getEventStreamDayByKey(TimelineMetadata meta) {
        String streamKey = String.format("%s:%s:%s", meta.getSessionGuid(), 
                meta.getSessionStartEventId(), meta.getSessionInstanceStartDay());
        EventStreamDay eventStreamDay = streamsByStreamKey.get(streamKey);
        
        if (eventStreamDay == null) {
            int startDay = meta.getSessionInstanceStartDay();
            String eventId = meta.getSessionStartEventId();
            
            eventStreamDay = new EventStreamDay();
            eventStreamDay.setSessionGuid(meta.getSessionGuid());
            eventStreamDay.setSessionName(meta.getSessionName());
            eventStreamDay.setSessionSymbol(meta.getSessionSymbol());
            eventStreamDay.setStartEventId(meta.getSessionStartEventId());
            eventStreamDay.setWeek(startDay / 7);
            eventStreamDay.setStudyBurstId(meta.getStudyBurstId());
            eventStreamDay.setStudyBurstNum(meta.getStudyBurstNum());
            streamsByStreamKey.put(streamKey, eventStreamDay);
            getEventStreamById(eventId).addEntry(startDay, eventStreamDay);
        }
        return eventStreamDay;
    }
    public AdherenceRecord getAdherenceRecordByGuid(String guid) {
        return adherenceByGuid.get(guid);    
    }
    public Integer getDaysSinceEventById(String eventId) {
        return daysSinceEventByEventId.get(eventId);
    }
    public DateTime getEventTimestampById(String eventId) {
        return eventTimestampByEventId.get(eventId);
    }
    public DateTimeZone getTimeZone() {
        return zone;
    }
    public int calculateAdherencePercentage() {
        return AdherenceUtils.calculateAdherencePercentage(streamsByEventId.values());
    }
    
    /**
     * This only returns the event IDs that are actually being used by streams (so 
     * if a stream is not included, even if there are metadata or adherence records
     * referencing that event ID, it will not be returned by this method). They are
     * sorted alphabetically.
     */
    public List<String> getStreamEventIds() {
        List<String> keysSorted = Lists.newArrayList(streamsByEventId.keySet());
        keysSorted.sort(String::compareToIgnoreCase);
        return keysSorted;
    }
    public static class Builder {
        private List<TimelineMetadata> metadata;
        private List<StudyActivityEvent> events;
        private List<AdherenceRecord> adherenceRecords;
        private DateTime now;
        private String clientTimeZone;
        private DateTimeZone zone;
        private String studyStartEventId;

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
        public Builder withClientTimeZone(String clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }
        public Builder withStudyStartEventId(String studyStartEventId) {
            this.studyStartEventId = studyStartEventId;
            return this;
        }
        public AdherenceState build() {
            checkNotNull(now);
            
            if (metadata == null) {
                metadata = ImmutableList.of();
            }
            if (events == null) {
                events = ImmutableList.of();
            }
            if (adherenceRecords == null) {
                adherenceRecords = ImmutableList.of();
            }
            this.zone = now.getZone();    
            // however, use the subject’s preferred time zone if available
            if (clientTimeZone != null) {
                this.zone = DateTimeZone.forID(clientTimeZone);
            }
            return new AdherenceState(this);
        }
    }
}
