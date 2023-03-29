package org.sagebionetworks.bridge.models.healthdata;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecordEx3;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;
import org.sagebionetworks.bridge.models.upload.Upload;

/**
 * Represents the Health Data Record model for Exporter 3. This is not cross-compatible with the original model for
 * Health Data Records.
 */
@BridgeTypeName("HealthDataRecordEx3")
@JsonDeserialize(as = DynamoHealthDataRecordEx3.class)
public interface HealthDataRecordEx3 extends BridgeEntity {
    /** Convenience method to instantiate a HealthDataRecord. */
    static HealthDataRecordEx3 create() {
        return new DynamoHealthDataRecordEx3();
    }

    /** Helper method to create a record from an upload. */
    static HealthDataRecordEx3 createFromUpload(Upload upload) {
        HealthDataRecordEx3 record = create();
        record.setId(upload.getUploadId());
        record.setAppId(upload.getAppId());
        record.setHealthCode(upload.getHealthCode());
        record.setCreatedOn(upload.getCompletedOn());

        ObjectNode metadata = upload.getMetadata();
        if (metadata != null) {
            Map<String, String> metadataMap = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> metadataIter = metadata.fields();
            while (metadataIter.hasNext()) {
                Map.Entry<String, JsonNode> entry = metadataIter.next();
                String name = entry.getKey();
                JsonNode value = entry.getValue();

                if (value.isNull()) {
                    // Skip.
                    continue;
                } else if (value.isArray() || value.isObject()) {
                    metadataMap.put(name, value.toString());
                } else {
                    // Primitive. Format in asText() to remove the extra formatting.
                    metadataMap.put(name, value.asText());
                }
            }

            record.setMetadata(metadataMap);
        }

        return record;
    }

    /** Record ID, uniquely identifies this record. */
    String getId();
    void setId(String id);

    /** App that the record belongs to. */
    String getAppId();
    void setAppId(String appId);

    /** Study that the record belongs to. May be null if the record is not part of a study. */
    String getStudyId();
    void setStudyId(String studyId);

    /** Health code of the participant that submitted the record. */
    String getHealthCode();
    void setHealthCode(String healthCode);

    /**
     * Participant version of the participant at the time the upload was submitted. May be null if the participant
     * version doesn't exist.
     */
    Integer getParticipantVersion();
    void setParticipantVersion(Integer participantVersion);

    /** Timestamp (epoch milliseconds) of when this record was submitted to Bridge. */
    Long getCreatedOn();
    void setCreatedOn(Long createdOn);

    /** Client info string of the participant at the time they submitted the record to Bridge. */
    String getClientInfo();
    void setClientInfo(String clientInfo);

    /** This is set to true when the record is exported to Synapse/ */
    boolean isExported();
    void setExported(boolean exported);

    /**
     * Timestamp (epoch milliseconds) of when this record was exported to Synapse. Due to redrives and re-exports, this
     * may be different than {@link #getCreatedOn}.
     */
    Long getExportedOn();
    void setExportedOn(Long exportedOn);

    /**
     * Record that is exported to the app-wide Synapse project. May be null if there is no app-wide Synapse project
     * configured.
     */
    ExportedRecordInfo getExportedRecord();
    void setExportedRecord(ExportedRecordInfo exportedRecord);

    /**
     * Records that are exported to the study-specific Synapse project, keyed by study ID. May be empty if there are no
     * study-specific Synapse projects configured.
     */
    Map<String, ExportedRecordInfo> getExportedStudyRecords();
    void setExportedStudyRecords(Map<String, ExportedRecordInfo> exportedStudyRecords);

    /** Client-submitted metadata, as a map of key-value pairs. */
    Map<String, String> getMetadata();
    void setMetadata(Map<String, String> metadata);

    /**
     * Sharing scope at the time the participant uploaded their data. This allows us to preserve the participant's
     * sharing preferences if export is delayed (due to Synapse maintenance or a redrive).
     */
    SharingScope getSharingScope();
    void setSharingScope(SharingScope sharingScope);

    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();
    void setVersion(Long version);

    /**
     * Download url for record.
     */
    String getDownloadUrl();
    void setDownloadUrl(String downloadUrl);

    /**
     * Expiration for record download url.
     */
    long getDownloadExpiration();
    void setDownloadExpiration(long downloadExpiration);
}
