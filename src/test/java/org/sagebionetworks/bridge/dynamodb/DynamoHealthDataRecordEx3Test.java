package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

public class DynamoHealthDataRecordEx3Test {
    private static final String CLIENT_INFO = "test-client";
    private static final Map<String, String> METADATA_MAP = ImmutableMap.of("foo", "bar");
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";
    private static final long VERSION = 3L;

    private static final String APP_STUDY_KEY = TestConstants.TEST_APP_ID + ':' + STUDY_ID;

    @Test
    public void getAppStudyKey() {
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        record.setAppId(null);
        record.setStudyId(null);
        assertNull(record.getAppStudyKey());

        record.setAppId(null);
        record.setStudyId(STUDY_ID);
        assertNull(record.getAppStudyKey());

        record.setAppId(TestConstants.TEST_APP_ID);
        record.setStudyId(null);
        assertNull(record.getAppStudyKey());

        record.setAppId(TestConstants.TEST_APP_ID);
        record.setStudyId(STUDY_ID);
        assertEquals(record.getAppStudyKey(), APP_STUDY_KEY);
    }

    @Test
    public void setAppStudyKey() {
        // Initially null.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        assertNull(record.getAppId());
        assertNull(record.getStudyId());

        // Set with the correct format.
        record.setAppStudyKey(APP_STUDY_KEY);
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getStudyId(), STUDY_ID);

        // Ignore null.
        record.setAppStudyKey(null);
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getStudyId(), STUDY_ID);

        // Ignore incorrect format.
        record.setAppStudyKey("wrong-format");
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getStudyId(), STUDY_ID);
    }

    @Test
    public void getSetMetadata() {
        // Initially null.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        assertNull(record.getMetadata());

        // Set non-empty.
        record.setMetadata(METADATA_MAP);
        assertEquals(record.getMetadata(), METADATA_MAP);

        // Set empty, gets changed to null.
        record.setMetadata(ImmutableMap.of());
        assertNull(record.getMetadata());

        // Set non-empty, then set null. Null is null.
        record.setMetadata(METADATA_MAP);
        assertEquals(record.getMetadata(), METADATA_MAP);

        record.setMetadata(null);
        assertNull(record.getMetadata());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // Make record.
        HealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        record.setId(RECORD_ID);
        record.setAppId(TestConstants.TEST_APP_ID);
        record.setStudyId(STUDY_ID);
        record.setHealthCode(TestConstants.HEALTH_CODE);
        record.setCreatedOn(TestConstants.CREATED_ON.getMillis());
        record.setClientInfo(CLIENT_INFO);
        record.setExported(true);
        record.setMetadata(METADATA_MAP);
        record.setVersion(VERSION);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(record, JsonNode.class);
        assertEquals(jsonNode.size(), 10);
        assertEquals(jsonNode.get("id").textValue(), RECORD_ID);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), STUDY_ID);
        assertEquals(jsonNode.get("healthCode").textValue(), TestConstants.HEALTH_CODE);
        assertEquals(jsonNode.get("createdOn").textValue(), TestConstants.CREATED_ON.toString());
        assertEquals(jsonNode.get("clientInfo").textValue(), CLIENT_INFO);
        assertTrue(jsonNode.get("exported").booleanValue());
        assertEquals(jsonNode.get("version").longValue(), VERSION);
        assertEquals(jsonNode.get("type").textValue(), "HealthDataRecordEx3");

        JsonNode metadataMapNode = jsonNode.get("metadata");
        assertEquals(metadataMapNode.size(), 1);
        assertEquals(metadataMapNode.get("foo").textValue(), "bar");

        // Convert back to POJO.
        record = BridgeObjectMapper.get().treeToValue(jsonNode, HealthDataRecordEx3.class);
        assertEquals(record.getId(), RECORD_ID);
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getStudyId(), STUDY_ID);
        assertEquals(record.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(record.getCreatedOn().longValue(), TestConstants.CREATED_ON.getMillis());
        assertEquals(record.getClientInfo(), CLIENT_INFO);
        assertTrue(record.isExported());
        assertEquals(record.getMetadata(), METADATA_MAP);
        assertEquals(record.getVersion().longValue(), VERSION);
    }
}
