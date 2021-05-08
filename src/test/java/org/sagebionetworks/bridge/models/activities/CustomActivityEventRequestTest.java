package org.sagebionetworks.bridge.models.activities;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class CustomActivityEventRequestTest {
    private static final String EVENT_KEY = "my-event";
    private static final String EVENT_TIMESTAMP_STRING = "2018-04-04T16:43:11.357-07:00";
    private static final DateTime EVENT_TIMESTAMP = DateTime.parse(EVENT_TIMESTAMP_STRING);

    @Test
    public void builder() {
        CustomActivityEventRequest req = new CustomActivityEventRequest.Builder().withEventKey(EVENT_KEY)
                .withTimestamp(EVENT_TIMESTAMP).build();
        assertEquals(req.getEventKey(), EVENT_KEY);
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, req.getTimestamp());
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON
        String jsonText = "{\n" +
                "   \"eventKey\":\"" + EVENT_KEY + "\",\n" +
                "   \"timestamp\":\"" + EVENT_TIMESTAMP_STRING + "\"\n" +
                "}";

        // Convert to POJO
        CustomActivityEventRequest req = BridgeObjectMapper.get().readValue(jsonText,
                CustomActivityEventRequest.class);
        assertEquals(req.getEventKey(), EVENT_KEY);
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, req.getTimestamp());

        // Convert back to JSON
        JsonNode node = BridgeObjectMapper.get().convertValue(req, JsonNode.class);
        assertEquals(node.size(), 3);
        assertEquals(node.get("eventKey").textValue(), EVENT_KEY);
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, DateTime.parse(node.get("timestamp").textValue()));
        
        // test alias of eventKey to eventId
        jsonText = "{\n" +
                "   \"eventId\":\"" + EVENT_KEY + "\",\n" +
                "   \"timestamp\":\"" + EVENT_TIMESTAMP_STRING + "\"\n" +
                "}";

        // Convert to POJO
        req = BridgeObjectMapper.get().readValue(jsonText, CustomActivityEventRequest.class);
        assertEquals(req.getEventKey(), EVENT_KEY);
    }
}