package org.sagebionetworks.bridge.upload;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadTableJobGuidHolderTest {
    private static final String JOB_GUID = "test-job-guid";

    @Test
    public void serialize() {
        // Start with Java object.
        UploadTableJobGuidHolder jobGuidHolder = new UploadTableJobGuidHolder(JOB_GUID);

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(jobGuidHolder, JsonNode.class);
        assertEquals(jsonNode.size(), 2);
        assertEquals(jsonNode.get("jobGuid").textValue(), JOB_GUID);
        assertEquals(jsonNode.get("type").textValue(), "UploadTableJobGuidHolder");

        // We never parse this from JSON, so we don't need to test deserialization.
    }
}
