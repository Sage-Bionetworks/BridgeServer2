package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

public class ExportedRecordInfoMapMarshaller extends StringKeyMapMarshaller<ExportedRecordInfo> {
    private static final TypeReference<Map<String, ExportedRecordInfo>> REF = new TypeReference<Map<String, ExportedRecordInfo>>() {
    };

    @Override public TypeReference<Map<String, ExportedRecordInfo>> getTypeReference() {
        return REF;
    }
}
