package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("ParticipantData")
@JsonDeserialize(as= DynamoParticipantData.class)
public interface ParticipantData extends BridgeEntity{

    static ParticipantData create(){
        return new DynamoParticipantData();
    }

    String getUserId();
    void setUserId(String userId);

    String getIdentifier();
    void setIdentifier(String identifier);

    JsonNode getData();
    void setData(JsonNode data);

    Long getVersion();
    void setVersion(Long version);
}
