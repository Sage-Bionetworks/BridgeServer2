package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ExternalIdentifierInfoTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ExternalIdentifierInfo.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerializeWithNoStudy() throws Exception {
        ExternalIdentifierInfo info = new ExternalIdentifierInfo("AAA", null, true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("identifier").textValue(), "AAA");
        assertEquals(node.get("assigned").booleanValue(), true);
        assertEquals(node.get("type").textValue(), "ExternalIdentifier");
        assertEquals(node.size(), 3);
        
        ExternalIdentifierInfo resInfo = BridgeObjectMapper.get().treeToValue(node, ExternalIdentifierInfo.class);
        assertEquals(resInfo, info);
    }
    
    @Test
    public void canSerializeWithStudy() throws Exception {
        ExternalIdentifierInfo info = new ExternalIdentifierInfo("AAA", "oneStudy", false);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals("AAA", node.get("identifier").textValue());
        assertEquals(false, node.get("assigned").booleanValue());
        assertEquals("oneStudy", node.get("studyId").textValue());
        assertEquals("ExternalIdentifier", node.get("type").textValue());
        assertEquals(4, node.size());
        
        ExternalIdentifierInfo resInfo = BridgeObjectMapper.get().treeToValue(node, ExternalIdentifierInfo.class);
        assertEquals(info, resInfo);
    }
}
