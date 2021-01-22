package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/v3/users/self/configs")
    public ForwardCursorPagedResourceList<String> getAllDataForUser(@RequestParam(required = false) String offsetKey,
                                                                    @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedAndConsentedSession();

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService
                .getParticipantData(session.getId(), offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    @GetMapping("/v4/users/self/configs/{identifier}")
    public ParticipantData getDataByIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return participantDataService.getParticipantDataRecord(session.getId(), identifier);
    }

    @PostMapping("/v4/users/self/configs/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveParticipantDataRecordForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setHealthCode(null); // set in service, but just so non future use depends on it

        participantDataService.saveParticipantData(session.getId(), identifier, participantData);

        return new StatusMessage("Participant data saved.");
    }

    //@DeleteMapping("/v4/users/self/configs/{identifier}")
}
