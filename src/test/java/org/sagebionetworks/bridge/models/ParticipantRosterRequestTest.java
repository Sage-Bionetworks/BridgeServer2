package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ParticipantRosterRequestTest {
    private static final String PASSWORD = "P@ssword1";
    private static final String STUDY_ID = "test-study-id";

    @Test
    public void builder() {
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword(PASSWORD)
                .withStudyId(STUDY_ID).build();
        assertEquals(request.getPassword(), PASSWORD);
        assertEquals(request.getStudyId(), STUDY_ID);
    }

    @Test
    public void serialize() throws JsonProcessingException {
        // JSON
        String jsonText = "{\n" +
                "   \"password\":\"" + PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}";

        // Convert to POJO
        ParticipantRosterRequest request = BridgeObjectMapper.get().readValue(jsonText, ParticipantRosterRequest.class);
        assertEquals(request.getPassword(), PASSWORD);
        assertEquals(request.getStudyId(), STUDY_ID);

        // Convert back to JSON
        JsonNode node = BridgeObjectMapper.get().convertValue(request, JsonNode.class);
        assertEquals(node.size(), 3);
        assertEquals(node.get("password").textValue(), PASSWORD);
        assertEquals(node.get("studyId").textValue(), STUDY_ID);
    }
}