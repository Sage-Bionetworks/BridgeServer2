package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
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
    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String userId, String offsetKey, int pageSize) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        return participantDataDao.getParticipantData(userId, offsetKey, pageSize);
    }

    /**
     * Return a participant data record based on the given identifier.
     */
    public ParticipantData getParticipantDataRecord(String userId, String identifier) {
        return participantDataDao.getParticipantDataRecord(userId, identifier);
    }

    public void saveParticipantData(String userId, String identifier, ParticipantData participantData) {
        checkNotNull(participantData);

        participantData.setUserId(userId);
        participantData.setIdentifier(identifier);

        participantDataDao.saveParticipantData(participantData);
    }

    public void deleteParticipantData(String userId) { //TODO: why isn't there a checkNotNull() in the ReportService code?
        participantDataDao.deleteAllParticipantData(userId);
    }

    public void deleteParticipantDataRecord(String userId, String identifier) {
        participantDataDao.deleteParticipantData(userId, identifier);
    }
}
