package org.sagebionetworks.bridge.models.upload;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.models.BridgeEntity;

public class UploadRedriveList implements BridgeEntity {
    private final List<String> uploadIds;

    @JsonCreator
    public UploadRedriveList(@JsonProperty("uploadIds") List<String> uploadIds) {
        this.uploadIds = uploadIds;
    }
    public List<String> getUploadIds() {
        return (uploadIds == null) ? ImmutableList.of() : uploadIds;
    }
}
