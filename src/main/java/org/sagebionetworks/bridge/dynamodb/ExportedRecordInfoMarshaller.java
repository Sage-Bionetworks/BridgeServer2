package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

public class ExportedRecordInfoMarshaller extends JsonMarshaller<ExportedRecordInfo> {
    @Override
    public Class<ExportedRecordInfo> getConvertedClass() {
        return ExportedRecordInfo.class;
    }
}
