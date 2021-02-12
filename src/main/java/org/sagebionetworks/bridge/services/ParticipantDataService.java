package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

/**
 * A service for creating and retrieving non time-series participant data.
 */
@Component
public class ParticipantDataService {
    private ParticipantDataDao participantDataDao;

    @Autowired
    final void setParticipantDataDao(ParticipantDataDao participantDataDao) {
        this.participantDataDao = participantDataDao;
    }

    /**
     * Return a list of participant data records.
     */
    public ForwardCursorPagedResourceList<ParticipantData> getAllParticipantData(String userId, String offsetKey, int pageSize) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }

        return participantDataDao.getAllParticipantData(userId, offsetKey, pageSize);
    }

    /**
     * Return a participant data record based on the given identifier.
     */
    public ParticipantData getParticipantData(String userId, String identifier) {
        ParticipantData participantData = participantDataDao.getParticipantData(userId, identifier);

        if (participantData == null) {
            throw new EntityNotFoundException(ParticipantData.class);
        }

        return participantData;
    }

    /**
     * Save a participant data based on the given userId and identifier.
     */
    public void saveParticipantData(String userId, String identifier, ParticipantData participantData) {
        checkNotNull(participantData);

        participantData.setUserId(userId);
        participantData.setIdentifier(identifier);

        participantDataDao.saveParticipantData(participantData);
    }

    /**
     * Delete all participant data associated with the given userId.
     */
    public void deleteAllParticipantData(String userId) {
        participantDataDao.deleteAllParticipantData(userId);
    }

    /**
     * Delete a single participant data based on the given userId and identifier.
     */
    public void deleteParticipantData(String userId, String identifier) {
        ParticipantData participantData = this.getParticipantData(userId, identifier);

        if (participantData == null) {
            throw new EntityNotFoundException(ParticipantData.class);
        }

        participantDataDao.deleteParticipantData(userId, identifier);
    }
}
