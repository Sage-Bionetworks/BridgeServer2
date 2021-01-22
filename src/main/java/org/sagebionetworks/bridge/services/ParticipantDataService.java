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

//    /**
//     * Return a set of participant data records.
//     */
//    public ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId,
//                                                                                        String offsetKey, int pageSize) {
//        return participantDataDao.getParticipantData(userId, offsetKey, pageSize);
//    }

    /**
     * Return a set of participant data records.
     */
    public ForwardCursorPagedResourceList<ParticipantData> getParticipantData(String healthCode, String offsetKey, int pageSize) {
                                                                                    // TODO what actually is offsetKey?
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        return participantDataDao.getParticipantData(healthCode, offsetKey, pageSize);
    }

    /**
     * Return a participant data record based on the given identifier.
     */
    public ParticipantData getParticipantDataRecord(String healthCode, String identifier) {
        return participantDataDao.getParticipantDataRecord(healthCode, identifier);
    }

    public void saveParticipantData(String healthCode, String identifier, ParticipantData participantData) {
        checkNotNull(participantData);

        participantData.setHealthCode(healthCode);
        participantData.setIdentifier(identifier);

        participantDataDao.saveParticipantData(participantData);
    }

    public void deleteParticipantData(String healthCode) { //TODO: why isn't there a checkNotNull() in the ReportService code?
        participantDataDao.deleteAllParticipantData(healthCode);
    }

    public void deleteParticipantDataRecord(String healthCode, String identifier) {
        participantDataDao.deleteParticipantData(healthCode, identifier);
    }
}
