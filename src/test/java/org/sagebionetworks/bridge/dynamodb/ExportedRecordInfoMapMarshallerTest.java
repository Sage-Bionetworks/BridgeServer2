package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

public class ExportedRecordInfoMapMarshallerTest {
    private static final String FILE_ENTITY_ID = "dummy-file-id";
    private static final ExportedRecordInfoMapMarshaller MARSHALLER = new ExportedRecordInfoMapMarshaller();


    @Test
    public void testConvert() throws Exception {
        // Make a simple record info with just one field. The full JSON serialization is tested elsewhere.
        ExportedRecordInfo recordInfo = new ExportedRecordInfo();
        recordInfo.setFileEntityId(FILE_ENTITY_ID);
        Map<String, ExportedRecordInfo> recordInfoMap = ImmutableMap.of(TEST_STUDY_ID, recordInfo);

        // Serialize. Use Jackson to verify.
        String serialized = MARSHALLER.convert(recordInfoMap);
        JsonNode jsonNode = BridgeObjectMapper.get().readTree(serialized);
        assertEquals(jsonNode.size(), 1);
        assertEquals(jsonNode.get(TEST_STUDY_ID).get("fileEntityId").textValue(), FILE_ENTITY_ID);

        // Deserialize and verify fields.
        Map<String, ExportedRecordInfo> deserialized = MARSHALLER.unconvert(serialized);
        assertEquals(deserialized.size(), 1);
        assertEquals(deserialized.get(TEST_STUDY_ID).getFileEntityId(), FILE_ENTITY_ID);
    }
}
