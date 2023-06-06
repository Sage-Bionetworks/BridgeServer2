package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExporterSubscriptionResultTest {
    private static final String SUBSCRIPTION_ARN = "arn:aws:sns:us-east-1:111111111111:test-topic:subscription-guid";

    @Test
    public void serialization() {
        // Start with POJO.
        ExporterSubscriptionResult result = new ExporterSubscriptionResult();
        result.setSubscriptionArn(SUBSCRIPTION_ARN);

        // Serialize to JSON.
        JsonNode node = BridgeObjectMapper.get().convertValue(result, JsonNode.class);
        assertEquals(node.size(), 2);
        assertEquals(node.get("subscriptionArn").textValue(), SUBSCRIPTION_ARN);
        assertEquals(node.get("type").textValue(), "ExporterSubscriptionResult");

        // We never de-serialize this from JSON, so we don't need to test this path.
    }
}
