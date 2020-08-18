package org.sagebionetworks.bridge.models.files;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.BridgeEntity;

public interface ParticipantFile extends BridgeEntity {
    String getFileId();
    String getUserId();
    String getMimeType();
    String getAppId();
    DateTime getCreatedOn();
    String getDownloadUrl();
    String getUploadUrl();

    void setFileId(String fileId);
    void setUserId(String userId);
    void setMimeType(String type);
    void setAppId(String appId);
    void setCreatedOn(DateTime createdOn);
    void setDownloadUrl(String url);
    void setUploadUrl(String url);
}
