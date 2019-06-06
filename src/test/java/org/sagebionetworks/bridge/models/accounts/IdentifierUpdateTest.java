package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class IdentifierUpdateTest {
    
    @Test
    public void canSerialize() throws Exception {
        SignIn signIn = new SignIn.Builder().withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD)
                .build();
        
        // You wouldn't normally send two updates, but for the sake of verifying serialization...
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", TestConstants.PHONE, "updatedExternalId");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(update);
        assertEquals(node.get("emailUpdate").textValue(), "updated@email.com");
        assertEquals(node.get("externalIdUpdate").textValue(), "updatedExternalId");
        assertEquals(node.get("type").textValue(), "IdentifierUpdate");
        
        JsonNode phoneNode = node.get("phoneUpdate");
        assertEquals(phoneNode.get("nationalFormat").textValue(), TestConstants.PHONE.getNationalFormat());
        
        JsonNode signInNode = node.get("signIn");
        assertEquals(signInNode.get("email").textValue(), TestConstants.EMAIL);
        assertEquals(signInNode.get("password").textValue(), TestConstants.PASSWORD);
        
        IdentifierUpdate deser = BridgeObjectMapper.get().readValue(node.toString(), IdentifierUpdate.class);
        assertEquals(deser.getEmailUpdate(), "updated@email.com");
        assertEquals(deser.getExternalIdUpdate(), "updatedExternalId");
        assertEquals(deser.getPhoneUpdate(), TestConstants.PHONE);
        assertEquals(deser.getSignIn().getEmail(), TestConstants.EMAIL);
        assertEquals(deser.getSignIn().getPassword(), TestConstants.PASSWORD);
    }

}
