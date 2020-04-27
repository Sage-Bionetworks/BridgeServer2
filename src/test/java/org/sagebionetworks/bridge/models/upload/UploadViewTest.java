package org.sagebionetworks.bridge.models.upload;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord.ExporterStatus;

/**
 * An API view of uploads that combines information from our internal upload health data record tables.
 * We serialize this in the API but do not read it through the API.  
 */
public class UploadViewTest {
    
    private static final DateTime REQUESTED_ON = DateTime.parse("2016-07-25T16:25:32.211Z");
    private static final DateTime COMPLETED_ON = DateTime.parse("2016-07-25T16:25:32.277Z");
    
    @Test
    public void canSerialize() throws Exception {
        HealthDataRecord record = HealthDataRecord.create();
        record.setAppVersion("appVersion");
        record.setCreatedOn(COMPLETED_ON.getMillis());
        record.setCreatedOnTimeZone("+03:00");
        record.setData(TestUtils.getClientData());
        record.setHealthCode("healthCode");
        record.setId("id");
        record.setMetadata(TestUtils.getClientData());
        record.setPhoneInfo("phoneInfo");
        record.setSchemaId("schema-id");
        record.setSchemaRevision(5);
        record.setAppId(TEST_APP_ID);
        record.setUploadDate(LocalDate.parse("2016-10-10"));
        record.setUploadId("upload-id");
        record.setUploadedOn(REQUESTED_ON.getMillis());
        record.setUserMetadata(TestUtils.getClientData());
        record.setUserSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        record.setUserExternalId("external-id");
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setValidationErrors("some errors");
        record.setVersion(1L);
        record.setSynapseExporterStatus(ExporterStatus.SUCCEEDED);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setContentLength(1000L);
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setRequestedOn(REQUESTED_ON.getMillis());
        upload.setCompletedOn(COMPLETED_ON.getMillis());
        upload.setCompletedBy(UploadCompletionClient.APP);
        // These should be hidden by the @JsonIgnore property
        upload.setContentMd5("some-content");
        upload.setHealthCode("health-code");
        
        UploadView view = new UploadView.Builder().withUpload(upload)
                .withHealthDataRecord(record)
                .withSchemaId("schema-name")
                .withSchemaRevision(10)
                .withHealthRecordExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED).build();

        JsonNode node = BridgeObjectMapper.get().valueToTree(view);
        
        assertEquals(node.get("contentLength").intValue(), 1000);
        assertEquals(node.get("status").textValue(), "succeeded");
        assertEquals(node.get("requestedOn").textValue(), "2016-07-25T16:25:32.211Z");
        assertEquals(node.get("completedOn").textValue(), "2016-07-25T16:25:32.277Z");
        assertEquals(node.get("completedBy").textValue(), "app");
        assertEquals(node.get("schemaId").textValue(), "schema-name");
        assertEquals(node.get("schemaRevision").intValue(), 10);
        assertEquals(node.get("type").textValue(), "Upload");
        assertEquals(node.get("healthRecordExporterStatus").textValue(), "succeeded");
        
        JsonNode recordNode = node.get("healthData");
        assertEquals(recordNode.get("appVersion").textValue(), "appVersion");
        assertEquals(recordNode.get("createdOn").textValue(), COMPLETED_ON.toString());
        assertEquals(recordNode.get("createdOnTimeZone").textValue(), "+03:00");
        assertEquals(recordNode.get("id").textValue(), "id");
        assertEquals(recordNode.get("phoneInfo").textValue(), "phoneInfo");
        assertEquals(recordNode.get("schemaId").textValue(), "schema-id");
        assertEquals(recordNode.get("schemaRevision").intValue(), 5);
        assertEquals(recordNode.get("studyId").textValue(), TEST_APP_ID);
        assertEquals(recordNode.get("uploadDate").textValue(), "2016-10-10");
        assertEquals(recordNode.get("uploadId").textValue(), "upload-id");
        assertEquals(recordNode.get("uploadedOn").textValue(), REQUESTED_ON.toString());
        assertEquals(recordNode.get("userSharingScope").textValue(), "all_qualified_researchers");
        assertEquals(recordNode.get("userExternalId").textValue(), "external-id");
        assertTrue(TestConstants.USER_DATA_GROUPS.contains(recordNode.get("userDataGroups").get(0).textValue()));
        assertTrue(TestConstants.USER_DATA_GROUPS.contains(recordNode.get("userDataGroups").get(1).textValue()));
        assertEquals(recordNode.get("validationErrors").textValue(), "some errors");
        assertEquals(recordNode.get("version").longValue(), 1L);
        assertEquals(recordNode.get("synapseExporterStatus").textValue(), "succeeded");
        assertEquals(recordNode.get("healthCode").textValue(), "healthCode");
        
        assertTrue(recordNode.get("data").isObject());
        assertTrue(recordNode.get("metadata").isObject());
        assertTrue(recordNode.get("userMetadata").isObject());
        
        // With recent changes to expose to admin, these should be present in JSON
        assertEquals(node.get("contentMd5").textValue(), "some-content");
        assertEquals(node.get("healthCode").textValue(), "health-code");
    }

}
