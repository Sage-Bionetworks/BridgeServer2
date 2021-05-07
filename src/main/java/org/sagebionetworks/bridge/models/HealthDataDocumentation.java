package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDocumentation;
import org.sagebionetworks.bridge.json.BridgeTypeName;

/** This class represents health data documentation */
@BridgeTypeName("HealthDataDocumentation")
@JsonDeserialize(as = DynamoHealthDataDocumentation.class)
public interface HealthDataDocumentation extends BridgeEntity {

    /** Convenience method to instantiate a HealthDataDocumentation. */
    static HealthDataDocumentation create() {
        return new DynamoHealthDataDocumentation();
    }

    String getTitle();
    void setTitle(String title);

    String getParentId();
    void setParentId(String parentId);

    String getIdentifier();
    void setIdentifier(String identifier);

    Long getVersion();
    void setVersion(Long version);

    String getS3Key();
    void setS3Key(String s3Key);

    String getCreatedBy();
    void setCreatedBy(String createdBy);

    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);

    String getModifiedBy();
    void setModifiedBy(String modifiedBy);

    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
}
