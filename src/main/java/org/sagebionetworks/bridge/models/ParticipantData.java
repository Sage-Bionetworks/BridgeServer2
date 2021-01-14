package org.sagebionetworks.bridge.models;
// TODO: check if we need to refactor this into /models/reports/

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("ParticipantData")
@JsonDeserialize(as= DynamoParticipantData.class)
public interface ParticipantData extends BridgeEntity{

    // TODO: I'm not sure what this is for but it's in ReportData
    static TypeReference<ForwardCursorPagedResourceList<ParticipantData>> PAGED_REPORT_DATA = new TypeReference<ForwardCursorPagedResourceList<ParticipantData>>() {
    };

    static ParticipantData create(){
        return new DynamoParticipantData();
    }

    String getUserId();
    void setUserId(String userId);

    String getConfigId();
    void setConfigId(String configId);

    String getData();
    void setData(String data);

    Long getVersion();
    void setVersion(Long version);
}

//TODO: organize imports once more finalized
