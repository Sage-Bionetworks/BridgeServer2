package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
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
        pFile.setDownloadUrl("dummy.download");
        pFile.setUploadUrl("dummy.upload");
        pFile.setExpiresOn(TestConstants.TIMESTAMP);

        String json = MAPPER.writeValueAsString(pFile);
        JsonNode node = MAPPER.readTree(json);

        assertEquals(node.get("type").textValue(), "ParticipantFile");
        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("fileId").textValue(), "fileId");
        assertEquals(node.get("createdOn").textValue(), TestConstants.TIMESTAMP.toString());
        assertEquals(node.get("mimeType").textValue(), "image/jpeg");
        assertEquals(node.get("appId").textValue(), "api_test");
        assertEquals(node.get("downloadUrl").textValue(), "dummy.download");
        assertEquals(node.get("uploadUrl").textValue(), "dummy.upload");
        assertEquals(node.get("expires").textValue(), TestConstants.TIMESTAMP.toString());
        assertEquals(node.size(), 9);

        ParticipantFile deser = MAPPER.readValue(json, ParticipantFile.class);
        assertEquals(deser.getUserId(), "userId");
        assertEquals(deser.getFileId(), "fileId");
        assertEquals(deser.getCreatedOn(), TestConstants.TIMESTAMP);
        assertEquals(deser.getMimeType(), "image/jpeg");
        assertEquals(deser.getAppId(), "api_test");
        assertEquals(deser.getDownloadUrl(), "dummy.download");
        assertEquals(deser.getUploadUrl(), "dummy.upload");
        assertEquals(deser.getExpiresOn(), TestConstants.TIMESTAMP);
    }

    @Test
    public void canDeserialize() throws Exception {

        String json ="{\"fileId\":\"fileId\",\"userId\":\"userId\",\"createdOn\":\"2015-01-27T00:38:32.486Z\"," +
                "\"mimeType\":\"image/jpeg\",\"appId\":\"api_test\",\"uploadUrl\":\"dummy.upload\"," +
                "\"downloadUrl\":\"dummy.download\",\"type\":\"ParticipantFile\"," +
                "\"expires\":\"2015-01-27T00:38:32.486Z\"}";
        ParticipantFile file = MAPPER.readValue(json, ParticipantFile.class);

        assertEquals(file.getFileId(), "fileId");
        assertEquals(file.getUserId(), "userId");
        assertEquals(file.getAppId(), "api_test");
        assertEquals(file.getCreatedOn().toString(), TestConstants.TIMESTAMP.toString());
        assertEquals(file.getMimeType(), "image/jpeg");
        assertEquals(file.getDownloadUrl(), "dummy.download");
        assertEquals(file.getUploadUrl(), "dummy.upload");
        assertEquals(file.getExpiresOn(), TestConstants.TIMESTAMP);
    }
}
