package org.sagebionetworks.bridge.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadCsvRequestTest {
    private static final String JOB_GUID = "test-job-guid";

    @Test
    public void serialize() {
        // Start with Java object.
        UploadCsvRequest request = new UploadCsvRequest();
        request.setJobGuid(JOB_GUID);
        request.setAppId(TestConstants.TEST_APP_ID);
        request.setStudyId(TestConstants.TEST_STUDY_ID);
        request.setIncludeTestData(true);

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(request, JsonNode.class);
        assertEquals(jsonNode.size(), 5);
        assertEquals(jsonNode.get("jobGuid").textValue(), JOB_GUID);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertTrue(jsonNode.get("includeTestData").booleanValue());
        assertEquals(jsonNode.get("type").textValue(), "UploadCsvRequest");

        // We never parse this from JSON, so we don't need to test deserialization.
    }
}
