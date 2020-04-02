package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.io.File;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadValidationContextTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String UPLOAD_ID = "upload-id";

    @Test
    public void uploadId() {
        // null upload returns null upload ID
        UploadValidationContext context = new UploadValidationContext();
        assertNull(context.getUploadId());

        // non-null upload returns the ID of that upload
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);
        assertEquals(context.getUploadId(), UPLOAD_ID);
    }

    @Test
    public void shallowCopy() {
        // dummy objects to test against
        Study study = TestUtils.getValidStudy(UploadValidationContextTest.class);
        Upload upload = new DynamoUpload2();
        File tempDir = mock(File.class);
        File dataFile = mock(File.class);
        File decryptedDataFile = mock(File.class);
        Map<String, File> unzippedDataFileMap = ImmutableMap.<String, File>builder().put("foo", mock(File.class))
                .put("bar", mock(File.class)).put("baz", mock(File.class)).build();
        JsonNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        HealthDataRecord record = HealthDataRecord.create();

        // create original
        UploadValidationContext original = new UploadValidationContext();
        original.setHealthCode(HEALTH_CODE);
        original.setStudy(study.getIdentifier());
        original.setUpload(upload);
        original.setSuccess(false);
        original.addMessage("common message");
        original.setTempDir(tempDir);
        original.setDataFile(dataFile);
        original.setDecryptedDataFile(decryptedDataFile);
        original.setUnzippedDataFileMap(unzippedDataFileMap);
        original.setInfoJsonNode(infoJsonNode);
        original.setHealthDataRecord(record);
        original.setRecordId("test-record");

        // copy and validate
        UploadValidationContext copy = original.shallowCopy();
        assertEquals(copy.getHealthCode(), HEALTH_CODE);
        assertSame(copy.getStudy(), study.getIdentifier());
        assertSame(copy.getUpload(), upload);
        assertFalse(copy.getSuccess());
        assertSame(copy.getTempDir(), tempDir);
        assertSame(copy.getDataFile(), dataFile);
        assertSame(copy.getDecryptedDataFile(), decryptedDataFile);
        assertEquals(copy.getUnzippedDataFileMap(), unzippedDataFileMap);
        assertSame(copy.getInfoJsonNode(), infoJsonNode);
        assertSame(copy.getHealthDataRecord(), record);
        assertEquals(copy.getRecordId(), "test-record");

        assertEquals(copy.getMessageList().size(), 1);
        assertEquals(copy.getMessageList().get(0), "common message");

        // modify original and validate copy unchanged
        original.setHealthCode("new-health-code");
        original.addMessage("original message");

        assertEquals(copy.getHealthCode(), HEALTH_CODE);
        assertEquals(copy.getMessageList().size(), 1);
        assertEquals(copy.getMessageList().get(0), "common message");

        // modify copy and validate original unchanged
        copy.setRecordId("new-record-id");
        copy.addMessage("copy message");

        assertEquals(original.getRecordId(), "test-record");
        assertEquals(original.getMessageList().size(), 2);
        assertEquals(original.getMessageList().get(0), "common message");
        assertEquals(original.getMessageList().get(1), "original message");
    }
}
