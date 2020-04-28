package org.sagebionetworks.bridge.models.apps;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthProviderTest {
    public static final String CALLBACK_URL = "https://docs.sagebridge.org/crf-module/";
    public static final String INTROSPECT_ENDPOINT = "http://example.com/introspect";

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthProvider.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                CALLBACK_URL, INTROSPECT_ENDPOINT);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(provider);
        assertEquals(node.get("clientId").textValue(), "clientId");
        assertEquals(node.get("secret").textValue(), "secret");
        assertEquals(node.get("endpoint").textValue(), "endpoint");
        assertEquals(node.get("callbackUrl").textValue(), CALLBACK_URL);
        assertEquals(node.get("introspectEndpoint").textValue(), INTROSPECT_ENDPOINT);
        assertEquals(node.get("type").textValue(), "OAuthProvider");
        assertEquals(node.size(), 6);

        OAuthProvider deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthProvider.class);
        assertEquals(deser.getClientId(), "clientId");
        assertEquals(deser.getSecret(), "secret");
        assertEquals(deser.getEndpoint(), "endpoint");
        assertEquals(deser.getCallbackUrl(), CALLBACK_URL);
        assertEquals(deser.getIntrospectEndpoint(), INTROSPECT_ENDPOINT);

        assertEquals(deser, provider);
    }
}
