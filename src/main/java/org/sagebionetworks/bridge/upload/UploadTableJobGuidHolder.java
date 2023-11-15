package org.sagebionetworks.bridge.upload;

import org.sagebionetworks.bridge.models.BridgeEntity;

/** Holds a job GUID for an upload table job. */
public class UploadTableJobGuidHolder implements BridgeEntity {
    private final String jobGuid;

    public UploadTableJobGuidHolder(String jobGuid) {
        this.jobGuid = jobGuid;
    }

    public String getJobGuid() {
        return jobGuid;
    }
}
