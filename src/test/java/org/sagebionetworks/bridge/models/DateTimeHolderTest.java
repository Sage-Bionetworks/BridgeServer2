package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class DateTimeHolderTest {
    
    @Test
    public void canSerialize() throws Exception {
        DateTimeHolder holder = new DateTimeHolder(TIMESTAMP);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(holder);
        
        assertEquals(TIMESTAMP.toString(), node.get("dateTime").textValue());
        assertEquals("DateTimeHolder", node.get("type").textValue());
        
        DateTimeHolder deser = BridgeObjectMapper.get().readValue(node.toString(), DateTimeHolder.class);
        assertEquals(deser.getDateTime(), TIMESTAMP);
    }
}
