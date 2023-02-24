package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExportToAppNotificationTest {
    private static final String RECORD_ID = "test-record-id";

    private static final String APP_FILE_ENTITY_ID = "syn1111";
    private static final String APP_PARENT_PROJECT_ID = "syn1222";
    private static final String APP_RAW_FOLDER_ID = "syn1333";
    private static final String APP_S3_BUCKET = "app-bucket";
    private static final String APP_S3_KEY = "app-record-key";

    private static final String STUDY_1_ID = "study1";
    private static final String STUDY_1_FILE_ENTITY_ID = "syn2111";
    private static final String STUDY_1_PARENT_PROJECT_ID = "syn2222";
    private static final String STUDY_1_RAW_FOLDER_ID = "syn2333";
    private static final String STUDY_1_S3_BUCKET = "study1-bucket";
    private static final String STUDY_1_S3_KEY = "study1-record-key";

    private static final String STUDY_2_ID = "study2";
    private static final String STUDY_2_FILE_ENTITY_ID = "syn3111";
    private static final String STUDY_2_PARENT_PROJECT_ID = "syn3222";
    private static final String STUDY_2_RAW_FOLDER_ID = "syn3333";
    private static final String STUDY_2_S3_BUCKET = "study2-bucket";
    private static final String STUDY_2_S3_KEY = "study2-record-key";

    @Test
    public void studyRecordsAlwaysNonNull() {
        // Starts empty.
        ExportToAppNotification notification = new ExportToAppNotification();
        assertTrue(notification.getStudyRecords().isEmpty());

        // Set to non-empty.
        notification.setStudyRecords(ImmutableMap.of(STUDY_1_ID, new ExportedRecordInfo()));
        assertEquals(notification.getStudyRecords().size(), 1);

        // Set to null. It's empty again.
        notification.setStudyRecords(null);
        assertTrue(notification.getStudyRecords().isEmpty());
    }

    @Test
    public void serialize() throws Exception {
        // Start with POJO.
        ExportToAppNotification notification = new ExportToAppNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setRecordId(RECORD_ID);

        ExportedRecordInfo appRecordInfo = new ExportedRecordInfo();
        appRecordInfo.setParentProjectId(APP_PARENT_PROJECT_ID);
        appRecordInfo.setRawFolderId(APP_RAW_FOLDER_ID);
        appRecordInfo.setFileEntityId(APP_FILE_ENTITY_ID);
        appRecordInfo.setS3Bucket(APP_S3_BUCKET);
        appRecordInfo.setS3Key(APP_S3_KEY);
        notification.setRecord(appRecordInfo);

        ExportedRecordInfo study1RecordInfo = new ExportedRecordInfo();
        study1RecordInfo.setParentProjectId(STUDY_1_PARENT_PROJECT_ID);
        study1RecordInfo.setRawFolderId(STUDY_1_RAW_FOLDER_ID);
        study1RecordInfo.setFileEntityId(STUDY_1_FILE_ENTITY_ID);
        study1RecordInfo.setS3Bucket(STUDY_1_S3_BUCKET);
        study1RecordInfo.setS3Key(STUDY_1_S3_KEY);

        ExportedRecordInfo study2RecordInfo = new ExportedRecordInfo();
        study2RecordInfo.setParentProjectId(STUDY_2_PARENT_PROJECT_ID);
        study2RecordInfo.setRawFolderId(STUDY_2_RAW_FOLDER_ID);
        study2RecordInfo.setFileEntityId(STUDY_2_FILE_ENTITY_ID);
        study2RecordInfo.setS3Bucket(STUDY_2_S3_BUCKET);
        study2RecordInfo.setS3Key(STUDY_2_S3_KEY);

        Map<String, ExportedRecordInfo> studyRecordMap =
                new ImmutableMap.Builder<String, ExportedRecordInfo>()
                        .put(STUDY_1_ID, study1RecordInfo)
                        .put(STUDY_2_ID, study2RecordInfo)
                        .build();
        notification.setStudyRecords(studyRecordMap);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(notification, JsonNode.class);
        assertEquals(jsonNode.size(), 5);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("recordId").textValue(), RECORD_ID);
        assertEquals(jsonNode.get("type").textValue(), "ExportToAppNotification");

        JsonNode appRecordInfoNode = jsonNode.get("record");
        assertEquals(appRecordInfoNode.size(), 6);
        assertEquals(appRecordInfoNode.get("parentProjectId").textValue(), APP_PARENT_PROJECT_ID);
        assertEquals(appRecordInfoNode.get("rawFolderId").textValue(), APP_RAW_FOLDER_ID);
        assertEquals(appRecordInfoNode.get("fileEntityId").textValue(), APP_FILE_ENTITY_ID);
        assertEquals(appRecordInfoNode.get("s3Bucket").textValue(), APP_S3_BUCKET);
        assertEquals(appRecordInfoNode.get("s3Key").textValue(), APP_S3_KEY);
        assertEquals(appRecordInfoNode.get("type").textValue(), "RecordInfo");

        JsonNode studyRecordsObjectNode = jsonNode.get("studyRecords");
        assertEquals(studyRecordsObjectNode.size(), 2);

        JsonNode study1RecordInfoNode = studyRecordsObjectNode.get(STUDY_1_ID);
        assertEquals(study1RecordInfoNode.size(), 6);
        assertEquals(study1RecordInfoNode.get("parentProjectId").textValue(), STUDY_1_PARENT_PROJECT_ID);
        assertEquals(study1RecordInfoNode.get("rawFolderId").textValue(), STUDY_1_RAW_FOLDER_ID);
        assertEquals(study1RecordInfoNode.get("fileEntityId").textValue(), STUDY_1_FILE_ENTITY_ID);
        assertEquals(study1RecordInfoNode.get("s3Bucket").textValue(), STUDY_1_S3_BUCKET);
        assertEquals(study1RecordInfoNode.get("s3Key").textValue(), STUDY_1_S3_KEY);
        assertEquals(study1RecordInfoNode.get("type").textValue(), "RecordInfo");

        JsonNode study2RecordInfoNode = studyRecordsObjectNode.get(STUDY_2_ID);
        assertEquals(study2RecordInfoNode.size(), 6);
        assertEquals(study2RecordInfoNode.get("parentProjectId").textValue(), STUDY_2_PARENT_PROJECT_ID);
        assertEquals(study2RecordInfoNode.get("rawFolderId").textValue(), STUDY_2_RAW_FOLDER_ID);
        assertEquals(study2RecordInfoNode.get("fileEntityId").textValue(), STUDY_2_FILE_ENTITY_ID);
        assertEquals(study2RecordInfoNode.get("s3Bucket").textValue(), STUDY_2_S3_BUCKET);
        assertEquals(study2RecordInfoNode.get("s3Key").textValue(), STUDY_2_S3_KEY);
        assertEquals(study2RecordInfoNode.get("type").textValue(), "RecordInfo");

        // Convert back to POJO.
        notification = BridgeObjectMapper.get().treeToValue(jsonNode, ExportToAppNotification.class);
        assertEquals(notification.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(notification.getRecordId(), RECORD_ID);

        appRecordInfo = notification.getRecord();
        assertEquals(appRecordInfo.getParentProjectId(), APP_PARENT_PROJECT_ID);
        assertEquals(appRecordInfo.getRawFolderId(), APP_RAW_FOLDER_ID);
        assertEquals(appRecordInfo.getFileEntityId(), APP_FILE_ENTITY_ID);
        assertEquals(appRecordInfo.getS3Bucket(), APP_S3_BUCKET);
        assertEquals(appRecordInfo.getS3Key(), APP_S3_KEY);

        studyRecordMap = notification.getStudyRecords();
        assertEquals(studyRecordMap.size(), 2);

        study1RecordInfo = studyRecordMap.get(STUDY_1_ID);
        assertEquals(study1RecordInfo.getParentProjectId(), STUDY_1_PARENT_PROJECT_ID);
        assertEquals(study1RecordInfo.getRawFolderId(), STUDY_1_RAW_FOLDER_ID);
        assertEquals(study1RecordInfo.getFileEntityId(), STUDY_1_FILE_ENTITY_ID);
        assertEquals(study1RecordInfo.getS3Bucket(), STUDY_1_S3_BUCKET);
        assertEquals(study1RecordInfo.getS3Key(), STUDY_1_S3_KEY);

        study2RecordInfo = studyRecordMap.get(STUDY_2_ID);
        assertEquals(study2RecordInfo.getParentProjectId(), STUDY_2_PARENT_PROJECT_ID);
        assertEquals(study2RecordInfo.getRawFolderId(), STUDY_2_RAW_FOLDER_ID);
        assertEquals(study2RecordInfo.getFileEntityId(), STUDY_2_FILE_ENTITY_ID);
        assertEquals(study2RecordInfo.getS3Bucket(), STUDY_2_S3_BUCKET);
        assertEquals(study2RecordInfo.getS3Key(), STUDY_2_S3_KEY);
    }
}
