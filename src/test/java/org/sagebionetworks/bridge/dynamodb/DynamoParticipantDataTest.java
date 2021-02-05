package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class DynamoParticipantDataTest {

    private static final Long VERSION = 12345L;

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();

    @Test
    public void canSerialize() throws Exception{
        DynamoParticipantData participantData = new DynamoParticipantData();

        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("a", true);
        objectNode.put("b", "string");
        objectNode.put("c", 10);

        participantData.setUserId(TEST_USER_ID);
        participantData.setIdentifier(IDENTIFIER);
        participantData.setData(objectNode);

        String json = MAPPER.writeValueAsString(participantData);

        JsonNode node = MAPPER.readTree(json);

        assertNull(node.get("userId"));
        assertEquals(node.get("identifier").textValue(), IDENTIFIER);
        assertEquals(new Long(node.get("version").longValue()), VERSION);
        assertTrue(node.get("data").get("a").booleanValue());
        assertEquals(node.get("data").get("b").textValue(), "string");
        assertEquals(node.get("data").get("c").intValue(), 10);
        assertEquals(node.get("type").textValue(), "ParticipantData");
        assertEquals(node.size(), 4);

        ParticipantData deser = MAPPER.readValue(json, ParticipantData.class);
        assertTrue(deser.getData().get("a").asBoolean());
        assertEquals(deser.getData().get("b").textValue(), "string");
        assertEquals(deser.getData().get("c").intValue(), 10);
        assertEquals(deser.getIdentifier(), IDENTIFIER);
    }
}