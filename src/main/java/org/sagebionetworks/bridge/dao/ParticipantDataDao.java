package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    /**
     *
     * @param userId
     * @param offsetKey
     * @param pageSize
     * @return
     */
    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String userId, String offsetKey, int pageSize);

    /**
     * Get participant data record for the given userId and configId
     * @param userId
     * @param configId
     * @return
     */
    ParticipantData getParticipantDataRecord(String userId, String configId);

    /**
     * Writes a participant data to the backing store.
     * @param data - String data
     */
    void saveParticipantData(ParticipantData data);

    /**
     * Delete all records
     * @param userId
     *          userId to delete
     */
    void deleteAllParticipantData(String userId);

    /**
     * Delete a single participant data record.
     * @param userId
     * @param configId
     */
    void deleteParticipantData(String userId, String configId);
}
//TODO: finish javadoc
