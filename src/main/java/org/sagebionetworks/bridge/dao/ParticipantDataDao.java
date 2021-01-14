package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId);

    ForwardCursorPagedResourceList<ParticipantData> getParticipantDataV4(String userId, String configId, String offsetKey, int pageSize);

    //TODO check the three functions below..
    void saveParticipantData(String data);

    void deleteParticipantData(String userId);

    void deleteParticipantDataRecord(String userId);
}

//TODO: organize imports once more finalized
