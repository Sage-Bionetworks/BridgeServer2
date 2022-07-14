package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExporterSubscriptionRequestTest {
    private static final String TEST_QUEUE_ARN = "arn:aws:sqs:us-east-1:111111111111:test-queue";

    @Test
    public void jsonDeserialization() throws Exception {
        // We only ever de-serialize this from the HTTP request. We never serialize it to JSON.
        // Start with JSON text.
        String requestText = "{" +
                "    \"endpoint\":\"" + TEST_QUEUE_ARN + "\"," +
                "    \"protocol\":\"sqs\"," +
                "    \"attributes\":{\"RawMessageDelivery\":\"true\"}" +
                "}";

        // Convert to POJO.
        ExporterSubscriptionRequest request = BridgeObjectMapper.get().readValue(requestText,
                ExporterSubscriptionRequest.class);
        assertEquals(request.getEndpoint(), TEST_QUEUE_ARN);
        assertEquals(request.getProtocol(), "sqs");

        Map<String, String> attributes = request.getAttributes();
        assertEquals(attributes.size(), 1);
        assertEquals(attributes.get("RawMessageDelivery"), "true");
    }
}
