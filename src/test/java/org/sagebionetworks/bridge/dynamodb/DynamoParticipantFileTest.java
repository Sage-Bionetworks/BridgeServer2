package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DynamoParticipantFileTest {
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();

    @Test
    public void canSerialize() throws Exception {
        DynamoParticipantFile pFile = new DynamoParticipantFile("userId", "fileId");
        pFile.setAppId("api_test");
        pFile.setCreatedOn(TestConstants.TIMESTAMP);
        pFile.setMimeType("image/jpeg");

        String json = MAPPER.writeValueAsString(pFile);
        JsonNode node = MAPPER.readTree(json);

        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("fileId").textValue(), "fileId");
        assertEquals(node.get("createdOn").textValue(), TestConstants.TIMESTAMP.toString());
        assertEquals(node.get("mimeType").textValue(), "image/jpeg");
        assertEquals(node.get("appId").textValue(), "api_test");
        assertEquals(node.size(), 6);
    }

    @Test
    public void canDeserialize() throws Exception {
        String json = "{\"fileId\":\"fileId\",\"userId\":\"userId\",\"createdOn\":\"2015-01-27T00:38:32.486Z\",\"mimeType\":\"image/jpeg\",\"appId\":\"api_test\",\"type\":\"DynamoParticipantFile\"}";
        DynamoParticipantFile file = MAPPER.readValue(json, DynamoParticipantFile.class);

        assertEquals(file.getFileId(), "fileId");
        assertEquals(file.getUserId(), "userId");
        assertEquals(file.getAppId(), "api_test");
        assertEquals(file.getCreatedOn().toString(), TestConstants.TIMESTAMP.toString());
        assertEquals(file.getMimeType(), "image/jpeg");
    }
}
