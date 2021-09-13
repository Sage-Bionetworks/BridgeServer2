package org.sagebionetworks.bridge.models.worker;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class WorkerRequestTest {
    private static final String SERVICE_NAME = "dummy-service";
    private static final String VALUE = "dummy-value";

    // This is a test class to test JSON serialization of the request body.
    private static class InnerRequest {
        private final String value;

        public InnerRequest(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    public void serialize() {
        // We only serialize, never need to actually deserialize.
        InnerRequest innerRequest = new InnerRequest(VALUE);

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(SERVICE_NAME);
        workerRequest.setBody(innerRequest);

        JsonNode workerRequestNode = BridgeObjectMapper.get().convertValue(workerRequest, JsonNode.class);
        assertEquals(workerRequestNode.size(), 3);
        assertEquals(workerRequestNode.get("service").textValue(), SERVICE_NAME);
        assertEquals(workerRequestNode.get("type").textValue(), "WorkerRequest");

        JsonNode innerRequestNode = workerRequestNode.get("body");
        assertEquals(innerRequestNode.size(), 2);
        assertEquals(innerRequestNode.get("value").textValue(), VALUE);
        assertEquals(innerRequestNode.get("type").textValue(), "InnerRequest");
    }
}
