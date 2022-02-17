package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public class ScheduledAssessmentTest extends Mockito {

    @Test
    public void canSerialize() throws Exception {
        ScheduledAssessment schAssessment = new ScheduledAssessment.Builder()
                .withRefKey("ref")
                .withReference(new AssessmentReference())
                .withClientTimeZone(TEST_CLIENT_TIME_ZONE)
                .withFinishedOn(CREATED_ON)
                .withInstanceGuid("instanceGuid")
                .withState(ABANDONED)
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schAssessment);
        assertEquals(node.size(), 6);
        assertEquals(node.get("refKey").textValue(), "ref");
        assertEquals(node.get("instanceGuid").textValue(), "instanceGuid");
        assertEquals(node.get("state").textValue(), "abandoned");
        assertEquals(node.get("finishedOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("type").textValue(), "ScheduledAssessment");
        
        ScheduledAssessment deser = BridgeObjectMapper.get().readValue(node.toString(), ScheduledAssessment.class);
        assertEquals(deser.getRefKey(), "ref");
        assertEquals(deser.getInstanceGuid(), "instanceGuid");
        assertEquals(deser.getState(), ABANDONED);
        assertEquals(deser.getFinishedOn().toString(), CREATED_ON.toString());
        assertEquals(deser.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
    }
    
    @Test
    public void canSerializeNulls() throws Exception {
        ScheduledAssessment schAssessment = new ScheduledAssessment.Builder().build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schAssessment);
        assertEquals(node.size(), 1);
        assertEquals(node.get("type").textValue(), "ScheduledAssessment");
    }
}
