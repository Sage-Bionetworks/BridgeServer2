package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class DayRangeTest {
    
    @Test
    public void canSerialize() throws Exception {
        DayRange range = new DayRange(1, 2);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(range);
        assertEquals(node.get("min").intValue(), 1);
        assertEquals(node.get("max").intValue(), 2);
        assertEquals(node.get("type").textValue(), "DayRange");
    }

}
