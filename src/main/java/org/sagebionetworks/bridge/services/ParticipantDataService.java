package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
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

    // TODO: what kind of resource list?
    public ResourceList<? extends ParticipantData> getParticipantData(String userId, String configId) {
        return participantDataDao.getParticipantData(userId, configId);
    }

    // TODO: what kind of resource list?
    public ResourceList<? extends ParticipantData> getParticipantDataV4(final String userid, final String configId,
                                                                        final String offsetKey, final int pageSize) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        return participantDataDao.getParticipantDataV4(userid, configId, offsetKey, pageSize);
    }

    public void saveParticipantData(String userId, String configId, String data) {
        checkNotNull(data); // TODO: why doesn't Report service check if the appID and Identifier are null?

        // TODO: how does userID and configId come into play here
        participantDataDao.saveParticipantData(data);
    }

    public void deleteParticipantData(String userId, String configId) {
        participantDataDao.deleteParticipantData(userId, configId);
    }

    //TODO: organize imports once more finalized
}
