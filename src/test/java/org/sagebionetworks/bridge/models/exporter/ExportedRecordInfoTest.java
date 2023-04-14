package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExportedRecordInfoTest {
    private static final String FILE_ENTITY_ID = "syn1111";
    private static final String PARENT_PROJECT_ID = "syn1222";
    private static final String RAW_FOLDER_ID = "syn1333";
    private static final String S3_BUCKET = "test-bucket";
    private static final String S3_KEY = "test-record-key";

    @Test
    public void serialize() throws Exception {
        // Start with POJO.
        ExportedRecordInfo recordInfo = new ExportedRecordInfo();
        recordInfo.setFileEntityId(FILE_ENTITY_ID);
        recordInfo.setParentProjectId(PARENT_PROJECT_ID);
        recordInfo.setRawFolderId(RAW_FOLDER_ID);
        recordInfo.setS3Bucket(S3_BUCKET);
        recordInfo.setS3Key(S3_KEY);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(recordInfo, JsonNode.class);
        assertEquals(jsonNode.size(), 6);
        assertEquals(jsonNode.get("parentProjectId").textValue(), PARENT_PROJECT_ID);
        assertEquals(jsonNode.get("rawFolderId").textValue(), RAW_FOLDER_ID);
        assertEquals(jsonNode.get("fileEntityId").textValue(), FILE_ENTITY_ID);
        assertEquals(jsonNode.get("s3Bucket").textValue(), S3_BUCKET);
        assertEquals(jsonNode.get("s3Key").textValue(), S3_KEY);
        assertEquals(jsonNode.get("type").textValue(), "ExportedRecordInfo");

        // Convert back to POJO.
        recordInfo = BridgeObjectMapper.get().treeToValue(jsonNode, ExportedRecordInfo.class);
        assertEquals(recordInfo.getParentProjectId(), PARENT_PROJECT_ID);
        assertEquals(recordInfo.getRawFolderId(), RAW_FOLDER_ID);
        assertEquals(recordInfo.getFileEntityId(), FILE_ENTITY_ID);
        assertEquals(recordInfo.getS3Bucket(), S3_BUCKET);
        assertEquals(recordInfo.getS3Key(), S3_KEY);
    }
}
