package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public class ScheduledAssessmentTest extends Mockito {

    @Test
    public void canSerialize() throws Exception {
        ScheduledAssessment schAssessment = new ScheduledAssessment("ref", "instanceGuid", new AssessmentReference());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schAssessment);
        assertEquals(node.size(), 3);
        assertEquals(node.get("refKey").textValue(), "ref");
        assertEquals(node.get("instanceGuid").textValue(), "instanceGuid");
        assertEquals(node.get("type").textValue(), "ScheduledAssessment");
        assertNull(node.get("assessmentReference"));
    }
}
