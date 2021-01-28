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
    public ForwardCursorPagedResourceList<ParticipantData> getAllParticipantData(String userId, String offsetKey, int pageSize);

    /**
     * Get participant data record for the given userId and identifier
     * @param userId
     * @param identifier
     * @return
     */
    ParticipantData getParticipantData(String userId, String identifier);

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
     * @param identifier
     */
    void deleteParticipantData(String userId, String identifier);
}
//TODO: finish javadoc
