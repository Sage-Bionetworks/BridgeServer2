package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class TimestampHolderTest {
    
    private static final long TIME_STAMP = DateTime.parse("2018-03-27T18:30-07:00").getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        TimestampHolder holder = new TimestampHolder(TIME_STAMP);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(holder);
        
        assertEquals(TIME_STAMP, node.get("timestamp").asLong());
        assertEquals("TimestampHolder", node.get("type").textValue());
        
        TimestampHolder deser = BridgeObjectMapper.get().readValue(node.toString(), TimestampHolder.class);
        assertEquals(deser.getTimestamp(), new Long(TIME_STAMP));
    }
}
