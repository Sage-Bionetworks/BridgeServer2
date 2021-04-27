package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Regardless of how you search, you may want to return assessments, sessions,
 * or both. E.g. a search for session instance guids, but only return the 
 * assessments for those sessions. Add enum for this.
 * 
 * 
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
     * session or assessment instance GUIDs (or GUIDs created by the client);
     * any records that exist under these GUIDs for the caller will be returned.
     * If the assessment is a persistent assessment, all adherence records for
     * that assessment will be returned unless includeRepeats = false.
     */
    private final Set<String> instanceGuids;
    /** 
     * return adherence records for these assessmentIds (as types).
     */
    private final Set<String> assessmentIds;
    /**
     * return adherence records for these sessions (as types).
     */
    private final Set<String> sessionGuids;
    /**
     * return adherence records for these time windows (as types).
     */
    private final Set<String> timeWindowGuids;
    /**
     * If null, return only the records that are found by the search, whether 
     * session or assessment records. Otherwise, return the type indicated.
     */
    private final AdherenceRecordType recordType; 
    /**
     * Include multiple runs of assessments in persistent time windows? These
     * will have the same GUIDs but must have different startedOn timestamps.
     */
    private final Boolean includeRepeats;
    /**
     * Only retrieve records whose event timestamps are identical to the values
     * supplied in this map. To correctly determine if participants should redo
     * a session series based on a mutable event, the current event timestamps 
     * should be supplied in this map.
     */
    private final Map<String, DateTime> eventTimestamps;
    /**
     * createdOn value of record is on or after the startTime, if provided.
     */
    private final DateTime startTime;
    /**
     * createdOn value of record is on or before the endTime, if provided.
     */
    private final DateTime endTime;
    
    // API is paged.
    private final Integer offsetBy;
    private final Integer pageSize;
    /**
     * Sort by createdOn timestamp in ascending or descending order (ascending
     * by default).
     */
    private final SortOrder sortOrder;
    
    private AdherenceRecordsSearch(AdherenceRecordsSearch.Builder builder) {
        this.userId = builder.userId;
        this.studyId = builder.studyId;
        this.instanceGuids = builder.instanceGuids;
        this.assessmentIds = builder.assessmentIds;
        this.sessionGuids = builder.sessionGuids;
        this.timeWindowGuids = builder.timeWindowGuids;
        this.recordType = builder.recordType;
        this.includeRepeats = builder.includeRepeats;
        this.eventTimestamps = builder.eventTimestamps;
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

    public Set<String> getAssessmentIds() {
        return assessmentIds;
    }

    public Set<String> getSessionGuids() {
        return sessionGuids;
    }
    
    public Set<String> getTimeWindowGuids() {
        return timeWindowGuids;
    }
    
    public AdherenceRecordType getRecordType() {
        return recordType;
    }

    public Boolean getIncludeRepeats() {
        return includeRepeats;
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

    public static class Builder {
        private String userId;
        private String studyId;
        private Set<String> instanceGuids;
        private Set<String> assessmentIds;
        private Set<String> sessionGuids;
        private Set<String> timeWindowGuids;
        private AdherenceRecordType recordType;
        private Boolean includeRepeats;
        private Map<String, DateTime> eventTimestamps;
        private DateTime startTime;
        private DateTime endTime;
        private Integer offsetBy;
        private Integer pageSize;
        private SortOrder sortOrder;
        
        public Builder copyOf(AdherenceRecordsSearch search) {
            this.userId = search.userId;
            this.studyId = search.studyId;
            this.instanceGuids = ImmutableSet.copyOf(search.instanceGuids);
            this.assessmentIds = ImmutableSet.copyOf(search.assessmentIds);
            this.sessionGuids = ImmutableSet.copyOf(search.sessionGuids);
            this.timeWindowGuids = ImmutableSet.copyOf(search.timeWindowGuids);
            this.recordType = search.recordType;
            this.includeRepeats = search.includeRepeats;
            this.eventTimestamps = search.eventTimestamps;
            this.startTime = search.startTime;
            this.endTime = search.endTime;
            this.offsetBy = search.offsetBy;
            this.pageSize = search.pageSize;
            this.sortOrder = search.sortOrder;
            return this;
        }
        
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
        public Builder withRecordType(AdherenceRecordType recordType) {
            this.recordType = recordType;
            return this;
        }
        public Builder withIncludeRepeats(Boolean includeRepeats) {
            this.includeRepeats = includeRepeats;
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
            if (eventTimestamps == null) {
                eventTimestamps = ImmutableMap.of();
            }
            if (pageSize == null) {
                pageSize = Integer.valueOf(500);
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
