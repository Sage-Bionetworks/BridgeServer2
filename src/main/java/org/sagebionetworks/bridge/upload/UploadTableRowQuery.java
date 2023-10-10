package org.sagebionetworks.bridge.upload;

import org.joda.time.DateTime;

/** Represents a query for upload table rows. */
public class UploadTableRowQuery {
    private String appId;
    private String studyId;
    private String assessmentId;
    private Integer assessmentRevision;
    private DateTime startDate;
    private DateTime endDate;
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

    /** Assessment ID to query for. If specified, so must assessment revision. */
    public String getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    /** Assessment revision to query for. If specified, so must assessment ID. */
    public Integer getAssessmentRevision() {
        return assessmentRevision;
    }

    public void setAssessmentRevision(Integer assessmentRevision) {
        this.assessmentRevision = assessmentRevision;
    }

    /** Earliest date for rows to query for, inclusive. */
    public DateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(DateTime startDate) {
        this.startDate = startDate;
    }

    /** Latest date for rows to query for, exclusive. */
    public DateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(DateTime endDate) {
        this.endDate = endDate;
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
