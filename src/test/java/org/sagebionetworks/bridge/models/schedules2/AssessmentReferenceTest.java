package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AssessmentReferenceTest {
    
    @Test
    public void canSerialize() throws Exception {
        AssessmentReference ref = new AssessmentReference();
        ref.setGuid(GUID);
        ref.setAssessmentAppId("shared");
        ref.setAssessmentGuid("asmtGuid");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.get("guid").textValue(), GUID);
        
        JsonNode asmtNode = node.get("assessment");
        assertEquals(asmtNode.get("appId").textValue(), "shared");
        assertEquals(asmtNode.get("guid").textValue(), "asmtGuid");
        
        AssessmentReference deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AssessmentReference.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getAssessmentAppId(), "shared");
        assertEquals(deser.getAssessmentGuid(), "asmtGuid");
    }
}
