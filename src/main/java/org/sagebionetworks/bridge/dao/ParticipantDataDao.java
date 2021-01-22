package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;

public interface ParticipantDataDao {

    /**
     *
     * @param healthCode
     * @param offsetKey
     * @param pageSize
     * @return
     */
    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String healthCode, String offsetKey, int pageSize);

    /**
     * Get participant data record for the given healthCode and identifier
     * @param healthCode
     * @param identifier
     * @return
     */
    ParticipantData getParticipantDataRecord(String healthCode, String identifier);

    /**
     * Writes a participant data to the backing store.
     * @param data - String data
     */
    void saveParticipantData(ParticipantData data);

    /**
     * Delete all records
     * @param healthCode
     *          healthCode to delete
     */
    void deleteAllParticipantData(String healthCode);

    /**
     * Delete a single participant data record.
     * @param healthCode
     * @param identifier
     */
    void deleteParticipantData(String healthCode, String identifier);
}
//TODO: finish javadoc
