package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExportToStudyNotificationTest {
    private static final String FILE_ENTITY_ID = "syn1111";
    private static final String PARENT_PROJECT_ID = "syn2222";
    private static final String RAW_FOLDER_ID = "syn3333";
    private static final String RECORD_ID = "test-record-id";
    private static final String S3_BUCKET = "test-s3-bucket";
    private static final String S3_KEY = "test-s3-key";

    @Test
    public void serialize() throws Exception {
        // Start with POJO.
        ExportToStudyNotification notification = new ExportToStudyNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setStudyId(TestConstants.TEST_STUDY_ID);
        notification.setRecordId(RECORD_ID);
        notification.setParentProjectId(PARENT_PROJECT_ID);
        notification.setRawFolderId(RAW_FOLDER_ID);
        notification.setFileEntityId(FILE_ENTITY_ID);
        notification.setS3Bucket(S3_BUCKET);
        notification.setS3Key(S3_KEY);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(notification, JsonNode.class);
        assertEquals(jsonNode.size(), 9);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertEquals(jsonNode.get("recordId").textValue(), RECORD_ID);
        assertEquals(jsonNode.get("parentProjectId").textValue(), PARENT_PROJECT_ID);
        assertEquals(jsonNode.get("rawFolderId").textValue(), RAW_FOLDER_ID);
        assertEquals(jsonNode.get("fileEntityId").textValue(), FILE_ENTITY_ID);
        assertEquals(jsonNode.get("s3Bucket").textValue(), S3_BUCKET);
        assertEquals(jsonNode.get("s3Key").textValue(), S3_KEY);
        assertEquals(jsonNode.get("type").textValue(), "ExportToStudyNotification");

        // Convert back to POJO.
        notification = BridgeObjectMapper.get().treeToValue(jsonNode, ExportToStudyNotification.class);
        assertEquals(notification.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(notification.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(notification.getRecordId(), RECORD_ID);
        assertEquals(notification.getParentProjectId(), PARENT_PROJECT_ID);
        assertEquals(notification.getRawFolderId(), RAW_FOLDER_ID);
        assertEquals(notification.getFileEntityId(), FILE_ENTITY_ID);
        assertEquals(notification.getS3Bucket(), S3_BUCKET);
        assertEquals(notification.getS3Key(), S3_KEY);
    }
}
