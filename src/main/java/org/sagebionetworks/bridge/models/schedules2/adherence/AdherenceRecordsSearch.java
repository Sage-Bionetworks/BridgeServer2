package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.BridgeUtils.nullSafeImmutableMap;
import static org.sagebionetworks.bridge.BridgeUtils.nullSafeImmutableSet;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Search criteria for retrieving adherence records. The criteria listed in this
 * object are all additive (so adding criteria can easily lead to a situation 
 * where no records are returned, because the criteria conflict with one 
 * another). When no criteria are provided, the defaults of this class ensure
 * that all records will be returned, paged, to the caller. 
 */
@JsonDeserialize(builder = AdherenceRecordsSearch.Builder.class)
public class AdherenceRecordsSearch implements BridgeEntity {
    
    /**
     * Searches must be scoped to a user.
     */
    private final String userId;
    /**
     * Searches must be scoped to a study.
     */
    private final String studyId;
    /**
     * Session or assessment instance GUIDs; any records that exist under these 
     * GUIDs for the caller will be returned. If the assessment is a persistent 
     * assessment, all adherence records for that assessment will be returned 
     * unless includeRepeats = false.
     */
    private final Set<String> instanceGuids;
    /**
     * This cannot be supplied through the API. It is the parsed content of an
     * instanceGuid format that includes the startedOn timestamp, to retrieve
     * specific records when there are multiple time streams (format: 
     * “<guid>@<startedOn>”).
     */
    @JsonIgnore
    private final Map<String,DateTime> instanceGuidStartedOnMap;
    /** 
     * Return adherence records for these assessmentIds (as types).
     */
    private final Set<String> assessmentIds;
    /**
     * Return adherence records for these sessions (as types).
     */
    private final Set<String> sessionGuids;
    /**
     * Return adherence records for these time windows (as types).
     */
    private final Set<String> timeWindowGuids;
    /**
     * If null, return only the records that are found by the search, whether 
     * session or assessment records. Otherwise, return only the type indicated
     * (session or assessment).
     */
    private final AdherenceRecordType adherenceRecordType; 
    /**
     * Include multiple runs of assessments in persistent time windows? These
     * will have the same GUIDs but must have different `startedOn` timestamps.
     * By default this is true.
     */
    private final Boolean includeRepeats;
    /**
     * Only retrieve records whose event timestamp at the time they were recorded
     * is the current timestamp of the event. In other words, records from the 
     * current time series only. If this value is present along with 
     * `eventTimestamps`, the values in the `eventsTimestamps` field will 
     * overwrite the values on the server. This should be set to true in order 
     * to determine if a session, based on a mutable event, should be done again
     * by the participant (because the timestamp has changed). By default this
     * is false.
     */
    private final Boolean currentTimestampsOnly;
    /**
     * Only retrieve records whose event timestamp at the time they were recorded
     * is the timestamp provied for that event ID in this map.
     */
    private final Map<String, DateTime> eventTimestamps;
    /**
     * Return records where `startedOn` value of record is on or after the 
     * `startTime`, if provided.
     */
    private final DateTime startTime;
    /**
     * Return records where the `startedOn` value of record is on or before 
     * the `endTime`, if provided.
     */
    private final DateTime endTime;
    /**
     * The offset index (this API is paged).
     */
    private final Integer offsetBy;
    /**
     * The page size (this API is paged). The default is 250 records.
     */
    private final Integer pageSize;
    /**
     * Sort by the `startedOn` timestamp in either ascending or descending 
     * order. The default is ascending order.
     */
    private final SortOrder sortOrder;
    
    private AdherenceRecordsSearch(AdherenceRecordsSearch.Builder builder) {
        this.userId = builder.userId;
        this.studyId = builder.studyId;
        this.instanceGuids = nullSafeImmutableSet(builder.instanceGuids);
        this.instanceGuidStartedOnMap = nullSafeImmutableMap(builder.instanceGuidStartedOnMap);
        this.assessmentIds = nullSafeImmutableSet(builder.assessmentIds);
        this.sessionGuids = nullSafeImmutableSet(builder.sessionGuids);
        this.timeWindowGuids = nullSafeImmutableSet(builder.timeWindowGuids);
        this.adherenceRecordType = builder.adherenceRecordType;
        this.includeRepeats = builder.includeRepeats;
        this.currentTimestampsOnly = builder.currentTimestampsOnly;
        this.eventTimestamps = nullSafeImmutableMap(builder.eventTimestamps);
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.offsetBy = builder.offsetBy;
        this.pageSize = builder.pageSize;
        this.sortOrder = builder.sortOrder;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getStudyId() {
        return studyId;
    }
    
    public Set<String> getInstanceGuids() {
        return instanceGuids;
    }
    
    public Map<String, DateTime> getInstanceGuidStartedOnMap() {
        return instanceGuidStartedOnMap;
    }

    public Set<String> getAssessmentIds() {
        return assessmentIds;
    }

    public Set<String> getSessionGuids() {
        return sessionGuids;
    }
    
    public Set<String> getTimeWindowGuids() {
        return timeWindowGuids;
    }
    
    public AdherenceRecordType getAdherenceRecordType() {
        return adherenceRecordType;
    }

    public Boolean getIncludeRepeats() {
        return includeRepeats;
    }
    
    public Boolean getCurrentTimestampsOnly() {
        return currentTimestampsOnly;
    }

    public Map<String, DateTime> getEventTimestamps() {
        return eventTimestamps;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public Integer getOffsetBy() {
        return offsetBy;
    }

    public Integer getPageSize() {
        return pageSize;
    }
    
    public SortOrder getSortOrder() {
        return sortOrder;
    }
    
    public AdherenceRecordsSearch.Builder toBuilder() {
        return new AdherenceRecordsSearch.Builder()
                .withUserId(userId)
                .withStudyId(studyId)
                .withInstanceGuids(nullSafeImmutableSet(instanceGuids))
                .withInstanceGuidStartedOnMap(nullSafeImmutableMap(instanceGuidStartedOnMap))
                .withAssessmentIds(nullSafeImmutableSet(assessmentIds))
                .withSessionGuids(nullSafeImmutableSet(sessionGuids))
                .withTimeWindowGuids(nullSafeImmutableSet(timeWindowGuids))
                .withAdherenceRecordType(adherenceRecordType)
                .withIncludeRepeats(includeRepeats)
                .withCurrentTimestampsOnly(currentTimestampsOnly)
                .withEventTimestamps(nullSafeImmutableMap(eventTimestamps))
                .withStartTime(startTime)
                .withEndTime(endTime)
                .withOffsetBy(offsetBy)
                .withPageSize(pageSize)
                .withSortOrder(sortOrder);
    }

    public static class Builder {
        private String userId;
        private String studyId;
        private Set<String> instanceGuids;
        private Map<String, DateTime> instanceGuidStartedOnMap;
        private Set<String> assessmentIds;
        private Set<String> sessionGuids;
        private Set<String> timeWindowGuids;
        private AdherenceRecordType adherenceRecordType;
        private Boolean includeRepeats;
        private Boolean currentTimestampsOnly;
        private Map<String, DateTime> eventTimestamps;
        private DateTime startTime;
        private DateTime endTime;
        private Integer offsetBy;
        private Integer pageSize;
        private SortOrder sortOrder;
        
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withInstanceGuids(Set<String> instanceGuids) {
            this.instanceGuids = instanceGuids;
            return this;
        }
        public Builder withInstanceGuidStartedOnMap(Map<String, DateTime> instanceGuidStartedOnMap) {
            this.instanceGuidStartedOnMap = instanceGuidStartedOnMap;
            return this;
        }
        public Builder withAssessmentIds(Set<String> assessmentIds) {
            this.assessmentIds = assessmentIds;
            return this;
        }
        public Builder withSessionGuids(Set<String> sessionGuids) {
            this.sessionGuids = sessionGuids;
            return this;
        }
        public Builder withTimeWindowGuids(Set<String> timeWindowGuids) {
            this.timeWindowGuids = timeWindowGuids;
            return this;
        }
        public Builder withAdherenceRecordType(AdherenceRecordType adherenceRecordType) {
            this.adherenceRecordType = adherenceRecordType;
            return this;
        }
        public Builder withIncludeRepeats(Boolean includeRepeats) {
            this.includeRepeats = includeRepeats;
            return this;
        }
        public Builder withCurrentTimestampsOnly(Boolean currentTimestampsOnly) {
            this.currentTimestampsOnly = currentTimestampsOnly;
            return this;
        }
        public Builder withEventTimestamps(Map<String, DateTime> eventTimestamps) {
            this.eventTimestamps = eventTimestamps;
            return this;
        }
        public Builder withStartTime(DateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        public Builder withEndTime(DateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        public Builder withOffsetBy(Integer offsetBy) {
            this.offsetBy = offsetBy;
            return this;
        }
        public Builder withPageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }
        public Builder withSortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }
        
        public AdherenceRecordsSearch build() {
            if (instanceGuids == null) {
                instanceGuids = ImmutableSet.of();
            }
            if (instanceGuidStartedOnMap == null) {
                instanceGuidStartedOnMap = ImmutableMap.of();
            }
            if (assessmentIds == null) {
                assessmentIds = ImmutableSet.of();
            }
            if (sessionGuids == null) {
                sessionGuids = ImmutableSet.of();
            }
            if (timeWindowGuids == null) {
                timeWindowGuids = ImmutableSet.of();
            }
            if (includeRepeats == null) {
                includeRepeats = Boolean.TRUE;
            }
            if (currentTimestampsOnly == null) {
                currentTimestampsOnly = Boolean.FALSE;
            }
            if (eventTimestamps == null) {
                eventTimestamps = ImmutableMap.of();
            }
            if (pageSize == null) {
                pageSize = Integer.valueOf(DEFAULT_PAGE_SIZE);
            }
            if (offsetBy == null) {
                offsetBy = Integer.valueOf(0);
            }
            if (sortOrder == null) {
                sortOrder = SortOrder.ASC;
            }
            return new AdherenceRecordsSearch(this);
        }
    }
}
