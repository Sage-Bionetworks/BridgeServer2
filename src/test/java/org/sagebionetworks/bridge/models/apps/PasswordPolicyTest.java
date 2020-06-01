package org.sagebionetworks.bridge.models.apps;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class PasswordPolicyTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(PasswordPolicy.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        PasswordPolicy policy = new PasswordPolicy(8, true, true, true, true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(policy);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("minLength").asInt(), 8);
        assertTrue(node.get("numericRequired").asBoolean());
        assertTrue(node.get("symbolRequired").asBoolean());
        assertTrue(node.get("lowerCaseRequired").asBoolean());
        assertTrue(node.get("upperCaseRequired").asBoolean());
        assertEquals(node.get("type").asText(), "PasswordPolicy");
        
        PasswordPolicy policy2 = BridgeObjectMapper.get().readValue(json, PasswordPolicy.class);
        assertEquals(policy2, policy);
    }
}
