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
        index.setStudyIds(TestConstants.USER_STUDY_IDS);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(index);
        assertEquals(node.get("identifier").textValue(), "asdf");
        assertEquals(node.get("type").textValue(), "ReportIndex");
        assertTrue(node.get("public").booleanValue());
        assertEquals(node.get("studyIds").get(0).textValue(), "studyA");
        assertEquals(node.get("studyIds").get(1).textValue(), "studyB");
        assertEquals(node.size(), 4);
        
        ReportIndex deser = BridgeObjectMapper.get().readValue(node.toString(), ReportIndex.class);
        assertEquals(deser.getIdentifier(), "asdf");
        assertTrue(deser.isPublic());
        assertEquals(deser.getStudyIds(), TestConstants.USER_STUDY_IDS);
    }
}
