package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.models.BridgeEntity;

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
     * Include multiple runs of assessments in persistent time windows? These
     * will have the same GUIDs but must have different startedOn timestamps.
     */
    private final Boolean includeRepeats;
    /**
     * Only retrieve records whose event timestamps are identical to the values
     * that are currently recorded for the user on the server. If event timestamps
     * change, this will have the effect of wiping out the adherence data for
     * that stream of events.
     */
    private final Boolean currentTimeseriesOnly;

    // Time-based API can be combined with other criteria
    private final String startEventId;
    private final Integer startDay;
    private final Integer endDay;
    
    // API is paged.
    private final Integer offsetBy;
    private final Integer pageSize;
    
    private AdherenceRecordsSearch(AdherenceRecordsSearch.Builder builder) {
        this.userId = builder.userId;
        this.studyId = builder.studyId;
        this.instanceGuids = builder.instanceGuids;
        this.assessmentIds = builder.assessmentIds;
        this.sessionGuids = builder.sessionGuids;
        this.includeRepeats = builder.includeRepeats;
        this.currentTimeseriesOnly = builder.currentTimeseriesOnly;
        this.startEventId = builder.startEventId;
        this.startDay = builder.startDay;
        this.endDay = builder.endDay;
        this.offsetBy = builder.offsetBy;
        this.pageSize = builder.pageSize;
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

    public Boolean getIncludeRepeats() {
        return includeRepeats;
    }

    public Boolean getCurrentTimeseriesOnly() {
        return currentTimeseriesOnly;
    }

    public String getStartEventId() {
        return startEventId;
    }

    public Integer getStartDay() {
        return startDay;
    }

    public Integer getEndDay() {
        return endDay;
    }

    public Integer getOffsetBy() {
        return offsetBy;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public static class Builder {
        private String userId;
        private String studyId;
        private Set<String> instanceGuids;
        private Set<String> assessmentIds;
        private Set<String> sessionGuids;
        private Boolean includeRepeats;
        private Boolean currentTimeseriesOnly;
        private String startEventId;
        private Integer startDay;
        private Integer endDay;
        private Integer offsetBy;
        private Integer pageSize;
        
        public Builder copyOf(AdherenceRecordsSearch search) {
            this.userId = search.userId;
            this.studyId = search.studyId;
            this.instanceGuids = ImmutableSet.copyOf(search.instanceGuids);
            this.assessmentIds = ImmutableSet.copyOf(search.assessmentIds);
            this.sessionGuids = ImmutableSet.copyOf(search.sessionGuids);
            this.includeRepeats = search.includeRepeats;
            this.currentTimeseriesOnly = search.currentTimeseriesOnly;
            this.startEventId = search.startEventId;
            this.startDay = search.startDay;
            this.endDay = search.endDay;
            this.offsetBy = search.offsetBy;
            this.pageSize = search.pageSize;
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
        public Builder withIncludeRepeats(Boolean includeRepeats) {
            this.includeRepeats = includeRepeats;
            return this;
        }
        public Builder withCurrentTimeseriesOnly(Boolean currentTimeseriesOnly) {
            this.currentTimeseriesOnly = currentTimeseriesOnly;
            return this;
        }
        public Builder withStartEventId(String startEventId) {
            this.startEventId = startEventId;
            return this;
        }
        public Builder withStartDay(Integer startDay) {
            this.startDay = startDay;
            return this;
        }
        public Builder withEndDay(Integer endDay) {
            this.endDay = endDay;
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
            if (includeRepeats == null) {
                includeRepeats = Boolean.TRUE;
            }
            if (currentTimeseriesOnly == null) {
                currentTimeseriesOnly = Boolean.FALSE;
            }
            if (offsetBy == null) {
                offsetBy = Integer.valueOf(0);
            }
            if (pageSize == null) {
                pageSize = Integer.valueOf(1000);
            }
            return new AdherenceRecordsSearch(this);
        }
    }
}
