package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.upload.UploadTableRow;

public class HibernateUploadTableRowTest {
    private static final int PARTICIPANT_VERSION = 13;
    private static final String RECORD_ID = "test-record";

    private static final String METADATA_KEY = "metadata-key";
    private static final String METADATA_VALUE = "metadata-value";
    private static final Map<String, String> METADATA_MAP = ImmutableMap.of(METADATA_KEY, METADATA_VALUE);

    private static final String DATA_KEY = "data-key";
    private static final String DATA_VALUE = "data-value";
    private static final Map<String, String> DATA_MAP = ImmutableMap.of(DATA_KEY, DATA_VALUE);

    @Test
    public void metadataAndDataAreNeverNull() {
        // Metadata and data start as empty maps.
        UploadTableRow row = new HibernateUploadTableRow();
        assertTrue(row.getMetadata().isEmpty());
        assertTrue(row.getData().isEmpty());

        // Set metadata and data to non-null non-empty maps.
        Map<String, String> map = ImmutableMap.of("foo", "bar");
        row.setMetadata(map);
        row.setData(map);
        assertEquals(row.getMetadata(), map);
        assertEquals(row.getData(), map);

        // Set metadata and data to null. It should be an empty map again.
        row.setMetadata(null);
        row.setData(null);
        assertTrue(row.getMetadata().isEmpty());
        assertTrue(row.getData().isEmpty());
    }

    @Test
    public void serialization() {
        // Start with POJO.
        UploadTableRow row = new HibernateUploadTableRow();
        row.setAppId(TestConstants.TEST_APP_ID);
        row.setStudyId(TestConstants.TEST_STUDY_ID);
        row.setRecordId(RECORD_ID);
        row.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        row.setCreatedOn(TestConstants.CREATED_ON);
        row.setTestData(true);
        row.setHealthCode(TestConstants.HEALTH_CODE);
        row.setParticipantVersion(PARTICIPANT_VERSION);
        row.setMetadata(METADATA_MAP);
        row.setData(DATA_MAP);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(row, JsonNode.class);
        assertEquals(jsonNode.size(), 11);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertEquals(jsonNode.get("recordId").textValue(), RECORD_ID);
        assertEquals(jsonNode.get("assessmentGuid").textValue(), TestConstants.ASSESSMENT_1_GUID);
        assertEquals(jsonNode.get("createdOn").textValue(), TestConstants.CREATED_ON.toString());
        assertTrue(jsonNode.get("testData").booleanValue());
        assertEquals(jsonNode.get("healthCode").textValue(), TestConstants.HEALTH_CODE);
        assertEquals(jsonNode.get("participantVersion").intValue(), PARTICIPANT_VERSION);
        assertEquals(jsonNode.get("metadata").size(), 1);
        assertEquals(jsonNode.get("metadata").get(METADATA_KEY).textValue(), METADATA_VALUE);
        assertEquals(jsonNode.get("data").size(), 1);
        assertEquals(jsonNode.get("data").get(DATA_KEY).textValue(), DATA_VALUE);
        assertEquals(jsonNode.get("type").textValue(), "UploadTableRow");

        // Convert back to POJO.
        row = BridgeObjectMapper.get().convertValue(jsonNode, HibernateUploadTableRow.class);
        assertEquals(row.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(row.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(row.getRecordId(), RECORD_ID);
        assertEquals(row.getAssessmentGuid(), TestConstants.ASSESSMENT_1_GUID);
        assertEquals(row.getCreatedOn(), TestConstants.CREATED_ON);
        assertTrue(row.isTestData());
        assertEquals(row.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(row.getParticipantVersion().intValue(), PARTICIPANT_VERSION);
        assertEquals(row.getMetadata(), METADATA_MAP);
        assertEquals(row.getData(), DATA_MAP);
    }
}
