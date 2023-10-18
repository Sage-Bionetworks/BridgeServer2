package org.sagebionetworks.bridge.upload;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;

/** Represents a query for upload table rows. */
public class UploadTableRowQuery implements BridgeEntity {
    private String appId;
    private String studyId;
    private String assessmentGuid;
    private DateTime startTime;
    private DateTime endTime;
    private boolean includeTestData;
    private Integer start;
    private Integer pageSize;

    /**
     * App ID to query for. This never needs to be specified as part of the request and is automatically determined by
     * the server.
     */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /**
     * Study ID to query for. This never needs to be specified as part of the request and is automatically determined
     * by the server.
     */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** Assessment to query for. */
    public String getAssessmentGuid() {
        return assessmentGuid;
    }

    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }

    /** Earliest date-time for rows to query for, inclusive. */
    public DateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    /** Latest date-time for rows to query for, exclusive. */
    public DateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(DateTime endTime) {
        this.endTime = endTime;
    }

    /** Whether to include test data. If not specified, defaults to false. */
    public boolean getIncludeTestData() {
        return includeTestData;
    }

    public void setIncludeTestData(boolean includeTestData) {
        this.includeTestData = includeTestData;
    }

    /** Offset into the result set. If not specified, defaults to 0. */
    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    /**
     * Requested page size of the result set. Cannot be less than 5 or more than 100. If not specified, defaults to 50.
     * If there are fewer rows than requested, this will return all rows.
     */
    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
