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

@Component
public class ParticipantDataService {
    private ParticipantDataDao participantDataDao;

    @Autowired
    final void setParticipantDataDao(ParticipantDataDao participantDataDao) {
        this.participantDataDao = participantDataDao;
    }

    /**
     * Return a set of participant data records.
     */
    public ForwardCursorPagedResourceList<ParticipantData> getAllParticipantData(String userId, String offsetKey, int pageSize) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }

        ForwardCursorPagedResourceList<ParticipantData> allParticipantData = participantDataDao.getAllParticipantData(userId, offsetKey, pageSize);
        if (allParticipantData == null) {
            throw new EntityNotFoundException(ParticipantData.class);
        }

        return allParticipantData;
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

    public void saveParticipantData(String userId, String identifier, ParticipantData participantData) {
        checkNotNull(participantData);

        participantData.setUserId(userId);
        participantData.setIdentifier(identifier);

        participantDataDao.saveParticipantData(participantData);
    }

    public void deleteAllParticipantData(String userId) {
        participantDataDao.deleteAllParticipantData(userId);
    }

    public void deleteParticipantData(String userId, String identifier) {
        ParticipantData participantData = this.getParticipantData(userId, identifier);

        if (participantData == null) {
            throw new EntityNotFoundException(ParticipantData.class);
        }

        participantDataDao.deleteParticipantData(userId, identifier);
    }
}
