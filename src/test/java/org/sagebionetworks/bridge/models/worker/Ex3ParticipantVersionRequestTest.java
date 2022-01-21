package org.sagebionetworks.bridge.models.worker;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class Ex3ParticipantVersionRequestTest {
    private static final int PARTICIPANT_VERSION = 42;

    @Test
    public void serialize() {
        // We only serialize, never need to actually deserialize.
        Ex3ParticipantVersionRequest request = new Ex3ParticipantVersionRequest();
        request.setAppId(TestConstants.TEST_APP_ID);
        request.setHealthCode(TestConstants.HEALTH_CODE);
        request.setParticipantVersion(PARTICIPANT_VERSION);

        JsonNode node = BridgeObjectMapper.get().convertValue(request, JsonNode.class);
        assertEquals(node.size(), 4);
        assertEquals(node.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(node.get("healthCode").textValue(), TestConstants.HEALTH_CODE);
        assertEquals(node.get("participantVersion").intValue(), PARTICIPANT_VERSION);
        assertEquals(node.get("type").textValue(), "Ex3ParticipantVersionRequest");
    }
}
