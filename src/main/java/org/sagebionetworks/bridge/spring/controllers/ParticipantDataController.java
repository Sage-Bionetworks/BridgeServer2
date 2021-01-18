package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.sagebionetworks.bridge.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
public class ParticipantDataController extends BaseController {

    private ParticipantDataService participantDataService;

    @Autowired
    final void setReportService(ParticipantDataService participantDataService) {
        this.participantDataService = participantDataService;
    }

//    @GetMapping("/v4/users/self/configs")
//    public ResourceList<DynamoParticipantData> getParticipantDataForSelf(@PathVariable String identifer) {
//        UserSession session = getAuthenticatedSession();
//        return reportService.getParticipantReport(session.getAppId(), identifer, session.getHealthCode(), null, null);
//    }

    //TODO: organize imports once more finalized
}
