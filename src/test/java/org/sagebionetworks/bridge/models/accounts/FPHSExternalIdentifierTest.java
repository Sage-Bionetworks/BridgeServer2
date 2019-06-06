package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class FPHSExternalIdentifierTest {

    @Test
    public void canRoundtripSerialize() throws Exception {
        FPHSExternalIdentifier identifier = FPHSExternalIdentifier.create("foo");
        
        String json = BridgeObjectMapper.get().writeValueAsString(identifier);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals(node.get("externalId").asText(), "foo");
        assertEquals(node.get("registered").asBoolean(), false);
        assertEquals(node.get("type").asText(), "ExternalIdentifier");
        assertEquals(node.size(), 3);
        
        json = json.replace("\"registered\":false", "\"registered\":true");
        FPHSExternalIdentifier externalId = BridgeObjectMapper.get().readValue(json, FPHSExternalIdentifier.class);
        assertEquals(externalId.getExternalId(), "foo");
        assertTrue(externalId.isRegistered());
    }
}
