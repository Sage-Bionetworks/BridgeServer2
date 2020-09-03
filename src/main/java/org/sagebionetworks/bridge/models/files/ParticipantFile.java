package org.sagebionetworks.bridge.models.files;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFile;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

public interface ParticipantFile extends BridgeEntity {
    static ParticipantFile create() {
        return new DynamoParticipantFile();
    }

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
