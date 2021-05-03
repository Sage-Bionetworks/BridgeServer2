package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDoc;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import java.net.URL;

/** This class represents health data documentation */
@BridgeTypeName("HealthDataDoc")
@JsonDeserialize(as = DynamoHealthDataDoc.class)
public interface HealthDataDoc extends BridgeEntity {

    static HealthDataDoc create() {
        return new DynamoHealthDataDoc();
    }

    String getTitle();
    void setTitle(String title);

    String getAppId();
    void setAppId(String appId);

    String getIdentifier();
    void setIdentifier(String identifier);

    String getDoc();
    void setDoc(String doc);

    Long getVersion();
    void setVersion(Long version);

    URL getDocUrl();
    void setDocUrl(URL docUrl);

    String getCreatedBy();
    void setCreatedBy(String createdBy);

    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);

    String getModifiedBy();
    void setModifiedBy(String modifiedBy);

    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
}
