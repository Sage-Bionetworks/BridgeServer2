package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class LabelTest {

    @Test
    public void canSerialize() throws Exception {
        Label label = new Label("en", "Value");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(label);
        assertEquals(node.size(), 3);
        assertEquals(node.get("lang").textValue(), "en");
        assertEquals(node.get("value").textValue(), "Value");
        assertEquals(node.get("type").textValue(), "Label");
        
        Label retValue = BridgeObjectMapper.get().readValue(node.toString(), Label.class);
        assertEquals(retValue.getLang(), "en");
        assertEquals(retValue.getValue(), "Value");
    }
    
}
