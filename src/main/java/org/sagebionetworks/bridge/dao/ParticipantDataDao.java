package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    /**
     * Get participant data records for the given userId
     * @param userId - the userId for the participant data
     * @return list of participant data records in a resource list that includes original query values
     */
    ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String offsetKey, int pageSize);

    /**
     * Get participant data for the given userId
     * @param userId
     * @param offsetKey
     * @param pageSize
     * @return
     */
    ForwardCursorPagedResourceList<ParticipantData> getParticipantDataV4(String userId, String offsetKey, int pageSize);

    //TODO check the functions below..

    /**
     * Get participant data record for the given userId and configId
     * @param userId
     * @param configId
     * @param offsetKey
     * @param pageSize
     * @return
     */
    ForwardCursorPagedResourceList<ParticipantData> getParticipantDataRecordV4(String userId, String configId, String offsetKey, int pageSize);

    /**
     * Writes a participant data to the backing store.
     * @param data - String data
     */
    void saveParticipantData(ParticipantData data); //TODO: how do we know the userId and configId of this data?

    /**
     * Delete all records
     * @param userId
     *          userId to delete
     */
    void deleteParticipantData(String userId);

    /**
     * Delete a single participant data record.
     * @param userId
     * @param configId
     */
    void deleteParticipantDataRecord(String userId, String configId);
}

//TODO: organize imports once more finalized
//TODO: finish javadoc
