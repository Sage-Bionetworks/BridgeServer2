package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class ExternalIdentifierTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoExternalIdentifier.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'identifier':'AAA','substudyId':'substudy-id'}");
        
        ExternalIdentifier identifier = BridgeObjectMapper.get().readValue(json, ExternalIdentifier.class);
        assertEquals(identifier.getIdentifier(), "AAA");
        assertEquals(identifier.getSubstudyId(), "substudy-id");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(identifier);
        
        assertEquals(node.size(), 3);
        assertEquals(node.get("identifier").textValue(), "AAA");
        assertEquals(node.get("substudyId").textValue(), "substudy-id");
        assertEquals(node.get("type").textValue(), "ExternalIdentifier");
    }
    
}
