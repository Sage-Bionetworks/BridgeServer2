package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;
import static org.testng.Assert.assertEquals;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class DateTimeHolderTest {
    private static final String TIMESTAMP_STR = "2017-06-08T18:50:21.650-07:00";
    private static final DateTime TIMESTAMP = DateTime.parse(TIMESTAMP_STR);
    
    @Test
    public void builder() {
        DateTimeHolder req = new DateTimeHolder.Builder().withDateTime(TIMESTAMP).build();
        assertDatesWithTimeZoneEqual(TIMESTAMP, req.getDateTime());
    }
    
    @Test
    public void serialize() throws Exception {
        // Start with JSON
        String jsonText = "{\n" +
                "   \"dateTime\":\"" + TIMESTAMP_STR + "\"\n" +
                "}";
        
        // Convert to POJO
        DateTimeHolder req = BridgeObjectMapper.get().readValue(jsonText,
                DateTimeHolder.class);
        assertDatesWithTimeZoneEqual(TIMESTAMP, req.getDateTime());
        
        // Convert back to JSON
        JsonNode node = BridgeObjectMapper.get().convertValue(req, JsonNode.class);
        assertEquals(node.get("type").textValue(), "DateTimeHolder");
        assertDatesWithTimeZoneEqual(TIMESTAMP, DateTime.parse(node.get("dateTime").textValue()));
    }
}
