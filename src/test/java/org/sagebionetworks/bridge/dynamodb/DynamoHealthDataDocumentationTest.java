package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

public class DynamoHealthDataDocumentationTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final DateTime NOW = new DateTime("2021-05-23T14:20:34.662-07:00");
    private static final String S3_KEY = TEST_APP_ID + "-" + IDENTIFIER;
    private static final Long VERSION = 100L;

    @Test
    public void canSerialize() throws JsonProcessingException {
        DynamoHealthDataDocumentation documentation = new DynamoHealthDataDocumentation();

        documentation.setParentId(TEST_APP_ID); // testing String attributes of HealthDataDocumentation
        documentation.setIdentifier(IDENTIFIER);
        documentation.setS3Key(S3_KEY);
        documentation.setCreatedOn(NOW); // testing the DateTime attributes of HealthDataDocumentation
        documentation.setVersion(VERSION);

        String json = MAPPER.writeValueAsString(documentation);

        JsonNode node = MAPPER.readTree(json);
        assertEquals(node.get("parentId").textValue(), TEST_APP_ID);
        assertEquals(node.get("identifier").textValue(), IDENTIFIER);
        assertEquals(node.get("s3Key").textValue(), S3_KEY);
        assertEquals(new DateTime(node.get("createdOn").textValue()), NOW);
        assertEquals((Long)node.get("version").longValue(), VERSION);

        HealthDataDocumentation deserialize = MAPPER.readValue(json, HealthDataDocumentation.class);
        assertEquals(deserialize.getParentId(), TEST_APP_ID);
        assertEquals(deserialize.getIdentifier(), IDENTIFIER);
        assertEquals(deserialize.getS3Key(), S3_KEY);
        assertEquals(deserialize.getCreatedOn().getMillis(), NOW.getMillis());
        assertEquals(deserialize.getVersion(), VERSION);
    }
}