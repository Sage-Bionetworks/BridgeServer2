package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    /**
     * Get participant data records for the given userId and configId.
     * @param userId
     *          the userId for the participant data
     * @param configId
     *          the configId for the participant data
     * @return list of participant data records in a resource list that includes original query values
     */
    ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId);

    /**
     * Get participant data for the given userId and configId
     * @param userId
     * @param configId
     * @param offsetKey
     * @param pageSize
     * @return
     */
    ForwardCursorPagedResourceList<ParticipantData> getParticipantDataV4(String userId, String configId, String offsetKey, int pageSize);

    //TODO check the three functions below..

    /**
     * Writes a participant data to the backing store.
     * @param data
     *          String data
     */
    void saveParticipantData(String data); //TODO: how do we know the userId and configId of this data?

    /**
     * Delete all records
     * @param userId
     *          userId to delete
     * @param configId
     *          configId to delete
     */
    void deleteParticipantData(String userId, String configId);

    // TODO: can we delete a single record in the participant data if we also provide the String data value?
    // void deleteParticipantDataRecord(String userId, String configId);
}

//TODO: organize imports once more finalized
