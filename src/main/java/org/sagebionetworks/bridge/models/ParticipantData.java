package org.sagebionetworks.bridge.models;
// TODO: check if we need to refactor this into /models/reports/

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("ParticipantData")
@JsonDeserialize(as= DynamoParticipantData.class)
public interface ParticipantData extends BridgeEntity{

    static TypeReference<ForwardCursorPagedResourceList<ParticipantData>> PAGED_REPORT_DATA = new TypeReference<ForwardCursorPagedResourceList<ParticipantData>>() {
    };

    static ParticipantData create(){
        return new DynamoParticipantData();
    }

    String getUserId();
    void setUserId(String userId);

}

//TODO: organize imports once more finalized
