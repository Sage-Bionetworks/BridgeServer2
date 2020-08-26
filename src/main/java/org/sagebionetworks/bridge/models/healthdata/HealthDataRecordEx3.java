package org.sagebionetworks.bridge.models.healthdata;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecordEx3;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

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

    /** Timestamp (epoch milliseconds) of when this record was submitted to Bridge. */
    Long getCreatedOn();
    void setCreatedOn(Long createdOn);

    /** Client info string of the participant at the time they submitted the record to Bridge. */
    String getClientInfo();
    void setClientInfo(String clientInfo);

    /** This is set to true when the record is exported to Synapse/ */
    boolean isExported();
    void setExported(boolean exported);

    /** Client-submitted metadata, as a map of key-value pairs. */
    Map<String, String> getMetadata();
    void setMetadata(Map<String, String> metadata);

    /**
     * Record version. This is used to detect concurrency conflicts. For creating new health data records, this field
     * should be left unspecified. For updating records, this field should match the version of the most recent GET
     * request.
     */
    Long getVersion();
    void setVersion(Long version);
}
