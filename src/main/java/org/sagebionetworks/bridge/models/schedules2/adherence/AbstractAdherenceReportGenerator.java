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
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.google.common.collect.ImmutableList;

public abstract class AbstractAdherenceReportGenerator {

    protected final String appId;
    protected final String studyId;
    protected final String userId;
    protected final DateTime createdOn;
    protected final boolean showActive;
    protected final DateTime now;
    protected final String clientTimeZone;
    protected final List<TimelineMetadata> metadata;
    protected final List<AdherenceRecord> adherenceRecords;
    protected final List<StudyActivityEvent> events;
    protected final AccountRef account;
    
    protected final DateTimeZone zone;
    
    protected final Map<String, EventStream> streamsByEventId;
    protected final Map<String, EventStreamDay> streamsByStreamKey;
    protected final Map<String, AdherenceRecord> adherenceByInstanceGuid;
    protected final Map<String, Integer> daysSinceEventByEventId;
    protected final Map<String, DateTime> eventTimestampByEventId;
    
    public AbstractAdherenceReportGenerator(AbstractAdherenceReportGenerator.Builder builder) {
        checkNotNull(builder.now, "Now is required");
        
        this.appId = checkNotNull(builder.appId);
        this.studyId = checkNotNull(builder.studyId);
        this.userId = checkNotNull(builder.userId);
        this.createdOn = checkNotNull(builder.createdOn);
        this.showActive = builder.showActiveOnly;
        this.clientTimeZone = builder.clientTimeZone;
        this.metadata = checkNotNull(builder.metadata, "Metadata are required");
        this.adherenceRecords = checkNotNull(builder.adherenceRecords, "Adherence records are required");
        this.events = checkNotNull(builder.events, "Events are required");
        this.account = builder.account;
        
        this.streamsByEventId = new HashMap<>();
        this.eventTimestampByEventId = new HashMap<>();
        this.streamsByStreamKey = new HashMap<>();
        this.adherenceByInstanceGuid = builder.adherenceRecords.stream()
                .collect(toMap(AdherenceRecord::getInstanceGuid, (a) -> a));
        this.daysSinceEventByEventId = new HashMap<>();
        
        LocalDate localNow = builder.now.toLocalDate();
        this.zone = (clientTimeZone != null) ? 
                DateTimeZone.forID(clientTimeZone) : builder.now.getZone();
        for (StudyActivityEvent event : builder.events) {
            DateTime eventTimestamp = event.getTimestamp();
            DateTimeZone selZone = (event.getClientTimeZone() != null) ?
                    DateTimeZone.forID(event.getClientTimeZone()) :
                    zone;
            eventTimestamp = event.getTimestamp().withZone(selZone);
            int daysSince = Days.daysBetween(eventTimestamp.toLocalDate(), localNow).getDays();
            this.daysSinceEventByEventId.put(event.getEventId(), daysSince);
            this.eventTimestampByEventId.put(event.getEventId(), eventTimestamp);
        }
        // In the event that the zone was not retrieved from the now value
        this.now = builder.now.withZone(zone);
    }
    
    public DateTimeZone getTimeZone() {
        return zone;
    }

    public abstract static class Builder {
        private String appId;
        private String studyId;
        private String userId;
        private DateTime createdOn;
        private boolean showActiveOnly;
        private List<TimelineMetadata> metadata = ImmutableList.of();
        private List<StudyActivityEvent> events = ImmutableList.of();
        private List<AdherenceRecord> adherenceRecords = ImmutableList.of();
        private DateTime now;
        private String clientTimeZone;
        private AccountRef account;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        
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
        
        public Builder withClientTimeZone(String clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }

        public Builder withAccount(AccountRef account) {
            this.account = account;
            return this;
        }
        
        public abstract <T extends AbstractAdherenceReportGenerator> T build();
    }    
}
