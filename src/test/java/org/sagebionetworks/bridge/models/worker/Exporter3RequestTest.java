package org.sagebionetworks.bridge.models.worker;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class Exporter3RequestTest {
    private static final String RECORD_ID = "test-record";

    @Test
    public void serialize() {
        // We only serialize, never need to actually deserialize.
        Exporter3Request ex3Request = new Exporter3Request();
        ex3Request.setAppId(TestConstants.TEST_APP_ID);
        ex3Request.setRecordId(RECORD_ID);

        JsonNode ex3RequestNode = BridgeObjectMapper.get().convertValue(ex3Request, JsonNode.class);
        assertEquals(ex3RequestNode.size(), 3);
        assertEquals(ex3RequestNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(ex3RequestNode.get("recordId").textValue(), RECORD_ID);
        assertEquals(ex3RequestNode.get("type").textValue(), "Exporter3Request");
    }
}
