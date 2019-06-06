package org.sagebionetworks.bridge.models.oauth;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthAuthorizationTokenTest {
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthAuthorizationToken.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken("vendorId", "authToken");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals(node.get("vendorId").textValue(), "vendorId");
        assertEquals(node.get("authToken").textValue(), "authToken");
        assertEquals(node.get("type").textValue(), "OAuthAuthorizationToken");
        assertEquals(node.size(), 3);
        
        OAuthAuthorizationToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAuthorizationToken.class);
        assertEquals(deser.getVendorId(), "vendorId");
        assertEquals(deser.getAuthToken(), "authToken");
        
        assertEquals(deser, token);
    }
}
