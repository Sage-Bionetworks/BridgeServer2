package org.sagebionetworks.bridge.models.reports;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class ReportIndexTest {

    @Test
    public void canSerialize() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setKey("asdf:STUDY");
        index.setIdentifier("asdf");
        index.setPublic(true);
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(index);
        assertEquals(node.get("identifier").textValue(), "asdf");
        assertEquals(node.get("type").textValue(), "ReportIndex");
        assertTrue(node.get("public").booleanValue());
        assertEquals(node.get("substudyIds").get(0).textValue(), "substudyA");
        assertEquals(node.get("substudyIds").get(1).textValue(), "substudyB");
        assertEquals(node.size(), 4);
        
        ReportIndex deser = BridgeObjectMapper.get().readValue(node.toString(), ReportIndex.class);
        assertEquals(deser.getIdentifier(), "asdf");
        assertTrue(deser.isPublic());
        assertEquals(deser.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
    }
}
