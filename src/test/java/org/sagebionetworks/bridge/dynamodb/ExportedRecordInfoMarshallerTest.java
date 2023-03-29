package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

public class ExportedRecordInfoMarshallerTest {
    private static final String FILE_ENTITY_ID = "dummy-file-id";
    private static final ExportedRecordInfoMarshaller MARSHALLER = new ExportedRecordInfoMarshaller();


    @Test
    public void testConvert() throws Exception {
        // Make a simple record info with just one field. The full JSON serialization is tested elsewhere.
        ExportedRecordInfo recordInfo = new ExportedRecordInfo();
        recordInfo.setFileEntityId(FILE_ENTITY_ID);

        // Serialize. Use Jackson to verify.
        String serialized = MARSHALLER.convert(recordInfo);
        JsonNode jsonNode = BridgeObjectMapper.get().readTree(serialized);
        assertEquals(jsonNode.get("fileEntityId").textValue(), FILE_ENTITY_ID);

        // Deserialize and verify fields.
        ExportedRecordInfo deserialized = MARSHALLER.unconvert(serialized);
        assertEquals(deserialized.getFileEntityId(), FILE_ENTITY_ID);
    }
}
