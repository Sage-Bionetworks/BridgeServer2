package org.sagebionetworks.bridge.models.oauth;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
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
        OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "vendorId", "authToken", "callbackUrl");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("vendorId").textValue(), "vendorId");
        assertEquals(node.get("authToken").textValue(), "authToken");
        assertEquals(node.get("callbackUrl").textValue(), "callbackUrl");
        assertEquals(node.get("type").textValue(), "OAuthAuthorizationToken");
        assertEquals(node.size(), 5);
        
        OAuthAuthorizationToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAuthorizationToken.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getVendorId(), "vendorId");
        assertEquals(deser.getAuthToken(), "authToken");
        assertEquals(deser.getCallbackUrl(), "callbackUrl");
        
        assertEquals(deser, token);
    }
    
    @Test
    public void canDeserializeWithStudy() throws Exception {
        String json = TestUtils.createJson("{'study':'" + TEST_APP_ID + "',"+
                "'vendorId':'vendorId','authToken':'authToken',"+
                "'callbackUrl':'callbackUrl'}");
        
        OAuthAuthorizationToken deser = BridgeObjectMapper.get().readValue(json, OAuthAuthorizationToken.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void canDeserializeWithAppId() throws Exception {
        String json = TestUtils.createJson("{'appId':'" + TEST_APP_ID + "',"+
                "'vendorId':'vendorId','authToken':'authToken',"+
                "'callbackUrl':'callbackUrl'}");
        
        OAuthAuthorizationToken deser = BridgeObjectMapper.get().readValue(json, OAuthAuthorizationToken.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
    }
}
