package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class IdentifierUpdateTest {
    
    @Test
    public void canSerialize() throws Exception {
        SignIn signIn = new SignIn.Builder().withEmail(EMAIL).withPassword(PASSWORD).build();
        
        // You wouldn't normally send two updates, but for the sake of verifying serialization...
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", PHONE, "updatedSynapseUserId");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(update);
        assertEquals(node.get("emailUpdate").textValue(), "updated@email.com");
        assertEquals(node.get("synapseUserIdUpdate").textValue(), "updatedSynapseUserId");
        assertEquals(node.get("type").textValue(), "IdentifierUpdate");
        
        JsonNode phoneNode = node.get("phoneUpdate");
        assertEquals(phoneNode.get("nationalFormat").textValue(), PHONE.getNationalFormat());
        
        JsonNode signInNode = node.get("signIn");
        assertEquals(signInNode.get("email").textValue(), EMAIL);
        assertEquals(signInNode.get("password").textValue(), PASSWORD);
        
        IdentifierUpdate deser = BridgeObjectMapper.get().readValue(node.toString(), IdentifierUpdate.class);
        assertEquals(deser.getEmailUpdate(), "updated@email.com");
        assertEquals(deser.getPhoneUpdate(), PHONE);
        assertEquals(deser.getSynapseUserIdUpdate(), "updatedSynapseUserId");
        assertEquals(deser.getSignIn().getEmail(), EMAIL);
        assertEquals(deser.getSignIn().getPassword(), PASSWORD);
    }

}
