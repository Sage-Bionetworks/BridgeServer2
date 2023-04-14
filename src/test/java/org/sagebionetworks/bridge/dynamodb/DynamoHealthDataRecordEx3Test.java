package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.upload.Upload;

public class DynamoHealthDataRecordEx3Test {
    private static final Map<String, String> METADATA_MAP = ImmutableMap.of("foo", "bar");
    private static final int PARTICIPANT_VERSION = 42;
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";
    private static final long VERSION = 3L;

    private static final String APP_STUDY_KEY = TestConstants.TEST_APP_ID + ':' + STUDY_ID;
    private static final Map<String, ExportedRecordInfo> STUDY_RECORD_INFO_MAP =
            ImmutableMap.of(STUDY_ID, new ExportedRecordInfo());

    @Test
    public void createFromUpload() throws Exception {
        // Create upload.
        Upload upload = Upload.create();
        upload.setUploadId(RECORD_ID);
        upload.setAppId(TestConstants.TEST_APP_ID);
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setCompletedOn(TestConstants.CREATED_ON.getMillis());

        // Create metadata.
        String metadataJsonText = "{\n" +
                "   \"null-key\":null,\n" +
                "   \"string-key\":\"string value\",\n" +
                "   \"int-key\":42,\n" +
                "   \"array-key\":[\"foo\", \"bar\"],\n" +
                "   \"object-key\":{\"baz\":\"qux\"}\n" +
                "}";
        ObjectNode metadataNode = (ObjectNode) BridgeObjectMapper.get().readTree(metadataJsonText);
        upload.setMetadata(metadataNode);

        // Convert to record.
        HealthDataRecordEx3 record = HealthDataRecordEx3.createFromUpload(upload);
        assertEquals(record.getId(), RECORD_ID);
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(record.getCreatedOn().longValue(), TestConstants.CREATED_ON.getMillis());

        Map<String, String> metadataMap = record.getMetadata();
        assertEquals(metadataMap.size(), 4);
        assertEquals(metadataMap.get("string-key"), "string value");
        assertEquals(metadataMap.get("int-key"), "42");

        String arrayValueText = metadataMap.get("array-key");
        JsonNode arrayNode = BridgeObjectMapper.get().readTree(arrayValueText);
        assertTrue(arrayNode.isArray());
        assertEquals(arrayNode.size(), 2);
        assertEquals(arrayNode.get(0).textValue(), "foo");
        assertEquals(arrayNode.get(1).textValue(), "bar");

        String objectValueText = metadataMap.get("object-key");
        JsonNode objectNode = BridgeObjectMapper.get().readTree(objectValueText);
        assertTrue(objectNode.isObject());
        assertEquals(objectNode.size(), 1);
        assertEquals(objectNode.get("baz").textValue(), "qux");
    }

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
    public void getSetExportedStudyRecords() {
        // Initially null.
        DynamoHealthDataRecordEx3 record = new DynamoHealthDataRecordEx3();
        assertNull(record.getExportedStudyRecords());

        // Set non-empty.
        record.setExportedStudyRecords(STUDY_RECORD_INFO_MAP);
        assertEquals(record.getExportedStudyRecords(), STUDY_RECORD_INFO_MAP);

        // Set empty, gets changed to null.
        record.setExportedStudyRecords(ImmutableMap.of());
        assertNull(record.getExportedStudyRecords());

        // Set non-empty, then set null. Null is null.
        record.setExportedStudyRecords(STUDY_RECORD_INFO_MAP);
        assertEquals(record.getExportedStudyRecords(), STUDY_RECORD_INFO_MAP);

        record.setExportedStudyRecords(null);
        assertNull(record.getExportedStudyRecords());
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
        record.setParticipantVersion(PARTICIPANT_VERSION);
        record.setCreatedOn(TestConstants.CREATED_ON.getMillis());
        record.setClientInfo("dummy client info");
        record.setExported(true);
        record.setExportedOn(TestConstants.EXPORTED_ON.getMillis());
        record.setExportedRecord(new ExportedRecordInfo());
        record.setExportedStudyRecords(STUDY_RECORD_INFO_MAP);
        record.setMetadata(METADATA_MAP);
        record.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
        record.setUserAgent(TestConstants.UA);
        record.setVersion(VERSION);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(record, JsonNode.class);
        assertEquals(jsonNode.size(), 17);
        assertEquals(jsonNode.get("id").textValue(), RECORD_ID);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), STUDY_ID);
        assertEquals(jsonNode.get("healthCode").textValue(), TestConstants.HEALTH_CODE);
        assertEquals(jsonNode.get("participantVersion").intValue(), PARTICIPANT_VERSION);
        assertEquals(jsonNode.get("createdOn").textValue(), TestConstants.CREATED_ON.toString());
        assertEquals(jsonNode.get("clientInfo").textValue(), "dummy client info");
        assertTrue(jsonNode.get("exported").booleanValue());
        assertEquals(jsonNode.get("exportedOn").textValue(), TestConstants.EXPORTED_ON.toString());
        assertEquals(jsonNode.get("sharingScope").textValue(), "sponsors_and_partners");
        assertEquals(jsonNode.get("userAgent").textValue(), TestConstants.UA);
        assertEquals(jsonNode.get("version").longValue(), VERSION);
        assertEquals(jsonNode.get("type").textValue(), "HealthDataRecordEx3");

        JsonNode metadataMapNode = jsonNode.get("metadata");
        assertEquals(metadataMapNode.size(), 1);
        assertEquals(metadataMapNode.get("foo").textValue(), "bar");

        // Just check that the record infos exist. The actual JSON serialization is tested elsewhere.
        assertTrue(jsonNode.hasNonNull("exportedRecord"));
        JsonNode exportedStudyRecordsNode = jsonNode.get("exportedStudyRecords");
        assertEquals(exportedStudyRecordsNode.size(), 1);
        assertTrue(exportedStudyRecordsNode.hasNonNull(STUDY_ID));

        // Convert back to POJO.
        record = BridgeObjectMapper.get().treeToValue(jsonNode, HealthDataRecordEx3.class);
        assertEquals(record.getId(), RECORD_ID);
        assertEquals(record.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(record.getStudyId(), STUDY_ID);
        assertEquals(record.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(record.getParticipantVersion().intValue(), PARTICIPANT_VERSION);
        assertEquals(record.getCreatedOn().longValue(), TestConstants.CREATED_ON.getMillis());
        assertEquals(record.getClientInfo(), "dummy client info");
        assertTrue(record.isExported());
        assertEquals(record.getExportedOn().longValue(), TestConstants.EXPORTED_ON.getMillis());
        assertNotNull(record.getExportedRecord());
        assertEquals(record.getSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertEquals(record.getMetadata(), METADATA_MAP);
        assertEquals(record.getUserAgent(), TestConstants.UA);
        assertEquals(record.getVersion().longValue(), VERSION);

        Map<String, ExportedRecordInfo> exportedStudyRecordMap = record.getExportedStudyRecords();
        assertEquals(exportedStudyRecordMap.size(), 1);
        assertNotNull(exportedStudyRecordMap.get(STUDY_ID));
    }
}
