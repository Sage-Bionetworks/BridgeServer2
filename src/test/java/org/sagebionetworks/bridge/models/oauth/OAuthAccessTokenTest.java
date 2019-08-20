package org.sagebionetworks.bridge.models.oauth;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthAccessTokenTest {
    private static final Set<String> SCOPES = ImmutableSet.of("foo", "bar", "baz");
    private static final String TYPE_NAME = "OAuthAccessToken";
    private static final String PROVIDER_USER_ID = "providerUserId";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String VENDOR_ID = "vendorId";
    private static final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC);

    @Test
    public void scopesIsNeverNull() {
        OAuthAccessToken token = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, DATE_TIME, PROVIDER_USER_ID,
                null);
        assertTrue(token.getScopes().isEmpty());
    }

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthAccessToken.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        OAuthAccessToken token = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, DATE_TIME, PROVIDER_USER_ID,
                SCOPES);

        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals(node.get(VENDOR_ID).textValue(), VENDOR_ID);
        assertEquals(node.get(ACCESS_TOKEN).textValue(), ACCESS_TOKEN);
        assertEquals(node.get("expiresOn").textValue(), DATE_TIME.toString());
        assertEquals(node.get(PROVIDER_USER_ID).textValue(), PROVIDER_USER_ID);
        assertEquals(node.get("type").textValue(), TYPE_NAME);

        JsonNode scopesNode = node.get("scopes");
        assertEquals(scopesNode.size(), 3);
        Set<String> serializedScopes = new HashSet<>();
        for (JsonNode oneScopeNode : scopesNode) {
            serializedScopes.add(oneScopeNode.textValue());
        }
        assertEquals(serializedScopes, SCOPES);

        assertEquals(node.size(), 6);

        OAuthAccessToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAccessToken.class);
        assertEquals(deser.getVendorId(), VENDOR_ID);
        assertEquals(deser.getAccessToken(), ACCESS_TOKEN);
        assertEquals(deser.getExpiresOn().toString(), DATE_TIME.toString());
        assertEquals(deser.getProviderUserId(), PROVIDER_USER_ID);
        assertEquals(deser.getScopes(), SCOPES);

        assertEquals(deser, token);
    }
}
