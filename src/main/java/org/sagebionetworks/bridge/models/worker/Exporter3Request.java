package org.sagebionetworks.bridge.models.worker;

/** Worker request for Exporter 3.0. */
public class Exporter3Request {
    private String appId;
    private String recordId;

    /** App ID of the record to be exported. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Record ID of the record to be exported. */
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
}
