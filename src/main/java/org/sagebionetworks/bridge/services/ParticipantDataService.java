package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ParticipantDataService {
    private ParticipantDataDao participantDataDao;

    @Autowired
    final void setParticipantDataDao(ParticipantDataDao participantDataDao) {
        this.participantDataDao = participantDataDao;
    }

    public ResourceList<? extends ParticipantData> getParticipantData(String userId, String configId) {

    }

    //TODO: organize imports once more finalized
}
