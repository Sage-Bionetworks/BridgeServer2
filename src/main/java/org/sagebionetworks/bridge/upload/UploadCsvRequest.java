package org.sagebionetworks.bridge.upload;

/** Worker request to generate the CSVs of all uploads for a given study. */
public class UploadCsvRequest {
    // Note that this doesn't include some options available in the worker, because we're not using them for now.
    // Specifically, we're not using assessment filter, upload date range, or zip file suffix. We might add these in
    // a future feature request.

    private String jobGuid;
    private String appId;
    private String studyId;
    private boolean includeTestData;

    /** Unique GUID for this job. */
    public String getJobGuid() {
        return jobGuid;
    }

    public void setJobGuid(String jobGuid) {
        this.jobGuid = jobGuid;
    }

    /** App to generate CSVs for. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Study to generate CSVs for. */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** Whether to include test data in the CSVs. Defaults to false. */
    public boolean isIncludeTestData() {
        return includeTestData;
    }

    public void setIncludeTestData(boolean includeTestData) {
        this.includeTestData = includeTestData;
    }
}
