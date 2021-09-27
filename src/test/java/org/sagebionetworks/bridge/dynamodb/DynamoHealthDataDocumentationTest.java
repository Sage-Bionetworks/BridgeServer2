package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.Test;

import java.util.Date;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

public class DynamoHealthDataDocumentationTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final Long TIMESTAMP = 1632263674L;
    private static final Long VERSION = 100L;

    @Test
    public void canSerialize() throws JsonProcessingException {
        DynamoHealthDataDocumentation documentation = new DynamoHealthDataDocumentation();

        documentation.setParentId(TEST_APP_ID);
        documentation.setIdentifier(IDENTIFIER);
        documentation.setModifiedOn(TIMESTAMP);
        documentation.setVersion(VERSION);

        String json = MAPPER.writeValueAsString(documentation);

        JsonNode node = MAPPER.readTree(json);
        assertEquals(node.get("identifier").textValue(), IDENTIFIER);
        Long modifiedOn = new DateTime(node.get("modifiedOn").textValue()).getMillis();
        assertEquals(modifiedOn, TIMESTAMP);
        assertEquals((Long)node.get("version").longValue(), VERSION);

        HealthDataDocumentation deserialize = MAPPER.readValue(json, HealthDataDocumentation.class);
        assertEquals(deserialize.getIdentifier(), IDENTIFIER);
        assertEquals(deserialize.getModifiedOn(), TIMESTAMP);
        assertEquals(deserialize.getVersion(), VERSION);
    }
}