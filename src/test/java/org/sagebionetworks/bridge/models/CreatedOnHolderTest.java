package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class CreatedOnHolderTest {

    @Test
    public void canSerialize() throws Exception {
        CreatedOnHolder holder = new CreatedOnHolder(TIMESTAMP);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(holder);
        
        assertEquals(TIMESTAMP.toString(), node.get("createdOn").textValue());
        assertEquals("CreatedOnHolder", node.get("type").textValue());
        
        CreatedOnHolder deser = BridgeObjectMapper.get().readValue(node.toString(), CreatedOnHolder.class);
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
    }
    
    
}
