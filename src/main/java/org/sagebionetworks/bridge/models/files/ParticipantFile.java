package org.sagebionetworks.bridge.models.files;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.BridgeEntity;

public interface ParticipantFile extends BridgeEntity {
    String getFileId();
    String getUserId();
    String getMimeType();
    DateTime getCreatedOn();
}
