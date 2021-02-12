package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    /**
     * Get all participant data associated with the given userId.
     * @param userId
     *          the userId associated with the participant data
     * @param offsetKey
     *          the identifier used to retrieve the next page of participant data
     * @param pageSize
     *          the requested page size
     * @return a list of participant data associated with the given userId, as a paginated resource list.
     */
    public ForwardCursorPagedResourceList<ParticipantData> getAllParticipantData(String userId, String offsetKey, int pageSize);

    /**
     * Get participant data record for the given userId and identifier
     * @param userId
     *          the given userId for the participant data
     * @param identifier
     *          the given identifier for the participant data
     * @return the participant data associated with the given userId and identifier.
     */
    ParticipantData getParticipantData(String userId, String identifier);

    /**
     * Writes a participant data to the backing store.
     * @param data
     *          the participant data object
     */
    void saveParticipantData(ParticipantData data);

    /**
     * Delete all records for the given userId.
     * @param userId
     *          userId to delete
     */
    void deleteAllParticipantData(String userId);

    /**
     * Delete a single participant data record.
     * @param userId
     *          the userId associated with the participant data
     * @param identifier
     *          the identifier associated with the participant data
     */
    void deleteParticipantData(String userId, String identifier);
}
