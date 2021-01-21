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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;

@CrossOrigin
@RestController
public class ParticipantDataController extends BaseController {

    private ParticipantDataService participantDataService;

    @Autowired
    final void setReportService(ParticipantDataService participantDataService) {
        this.participantDataService = participantDataService;
    }

    @GetMapping("/v4/users/self/configs")
    public ForwardCursorPagedResourceList<String> getAllDataForUser(@RequestParam(required = false) String offsetKey,
                                                                    @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession();

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService
                .getParticipantDataV4(session.getId(), offsetKey, pageSizeInt);
        List<String> configIds = participantData.getItems().stream().map(ParticipantData::getConfigId).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(configIds, participantData.getNextPageOffsetKey());
    }

    @GetMapping("/v4/users/self/configs/{identifier}")
    public ParticipantData getDataByIdentifier(@PathVariable String identifier,
                                                                               @RequestParam(required = false) String offsetKey,
                                                                               @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession();

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        return participantDataService.getParticipantDataRecordV4(session.getId(), identifier, offsetKey, pageSizeInt);
    }

    //TODO: organize imports once more finalized
}
