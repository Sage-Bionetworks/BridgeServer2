package org.sagebionetworks.bridge.models.schedules2;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class MessageTest {
    
    @Test
    public void canSerialize() throws Exception {
        Message message = new Message("ja", "件名", "体");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(message);
        assertEquals(node.size(), 4);
        assertEquals(node.get("lang").textValue(), "ja");
        assertEquals(node.get("subject").textValue(), "件名");
        assertEquals(node.get("body").textValue(), "体");
        assertEquals(node.get("type").textValue(), "Message");
        
        Message deser = BridgeObjectMapper.get().readValue(node.toString(), Message.class);
        assertEquals(deser.getLang(), "ja");
        assertEquals(deser.getSubject(), "件名");
        assertEquals(deser.getBody(), "体");
    }
}
