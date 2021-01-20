package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
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

    public ForwardCursorPagedResourceList<? extends ParticipantData> getParticipantData(String userId, String configId,
                                                                                        String offsetKey, int pageSize) {
        return participantDataDao.getParticipantData(userId, offsetKey, pageSize);
    }

    public ForwardCursorPagedResourceList<ParticipantData> getParticipantDataV4(final String userid, final String offsetKey, final int pageSize) {

        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        return participantDataDao.getParticipantDataV4(userid, offsetKey, pageSize);
    }

    public void saveParticipantData(ParticipantData data) {
        checkNotNull(data);
        participantDataDao.saveParticipantData(data);
    }

    public void deleteParticipantData(String userId) { //TODO: why isn't there a checkNotNull() in the ReportService code?
        participantDataDao.deleteParticipantData(userId);
    }

    public void deleteParticipantDataRecord(String userId, String configId) {
        participantDataDao.deleteParticipantDataRecord(userId, configId);
    }

    //TODO: do we need a dedicated update function?

    //TODO: organize imports once more finalized
}
