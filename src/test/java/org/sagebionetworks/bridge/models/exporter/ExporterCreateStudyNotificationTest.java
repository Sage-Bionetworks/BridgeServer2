package org.sagebionetworks.bridge.models.exporter;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExporterCreateStudyNotificationTest {
    private static final String PROJECT_ID = "syn5555";
    private static final String RAW_FOLDER_ID = "syn6666";

    @Test
    public void jsonSerialization() {
        // We only ever serialize this, never de-serialize.
        // Start with POJO.
        ExporterCreateStudyNotification notification = new ExporterCreateStudyNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setParentProjectId(PROJECT_ID);
        notification.setRawFolderId(RAW_FOLDER_ID);
        notification.setStudyId(TestConstants.TEST_STUDY_ID);

        // Convert to JSON.
        JsonNode node = BridgeObjectMapper.get().convertValue(notification, JsonNode.class);
        assertEquals(node.size(), 5);
        assertEquals(node.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(node.get("parentProjectId").textValue(), PROJECT_ID);
        assertEquals(node.get("rawFolderId").textValue(), RAW_FOLDER_ID);
        assertEquals(node.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertEquals(node.get("type").textValue(), "ExporterCreateStudyNotification");
    }
}
