package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.sagebionetworks.bridge.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@CrossOrigin
@RestController
public class ParticipantDataController extends BaseController {

    private ParticipantDataService participantDataService;

    @Autowired
    final void setReportService(ParticipantDataService participantDataService) {
        this.participantDataService = participantDataService;
    }

    @GetMapping("/v4/users/self/configs")
    public ForwardCursorPagedResourceList<String> listParticipantConfigIds(String userId, String offsetKey, int pageSize) {
        UserSession session = getAuthenticatedSession();
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService.getParticipantDataV4(userId, offsetKey, pageSize);
        return participantData.stream().map(ParticipantData::getConfigId).collect(Collectors.toList());
    }

    //TODO: organize imports once more finalized
}
