package org.sagebionetworks.bridge.spring.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

@CrossOrigin
@RestController
public class ParticipantDataController extends BaseController {

    private ParticipantDataService participantDataService;

    @Autowired
    final void setReportService(ParticipantDataService participantDataService) {
        this.participantDataService = participantDataService;
    }

    @GetMapping("/v3/users/self/data")
    public ForwardCursorPagedResourceList<String> getAllDataForUser(@RequestParam(required = false) String offsetKey,
                                                                    @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedAndConsentedSession();

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService
                .getParticipantData(session.getId(), offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    @GetMapping("/v4/users/self/data/{identifier}")
    public ParticipantData getDataByIdentifierForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return participantDataService.getParticipantDataRecord(session.getId(), identifier);
    }

    @PostMapping("/v4/users/self/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataRecordForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setUserId(null); // set in service, but just so non future use depends on it

        participantDataService.saveParticipantData(session.getId(), identifier, participantData);
        return new StatusMessage("Participant data saved.");
    }

    @DeleteMapping("/v4/users/self/data/{identifier}")
    public StatusMessage deleteDataByIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        ParticipantData participantData = participantDataService.getParticipantDataRecord(session.getId(), identifier);
        if (participantData == null) {
            throw new EntityNotFoundException(ParticipantData.class);
        }
        participantDataService.deleteParticipantDataRecord(session.getId(), identifier);

        return new StatusMessage("Participant data record deleted.");
    }

    @GetMapping("/v1/apps/{appId}/participants/{userId}/data")
    public ForwardCursorPagedResourceList<String> getAllDataForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                                           String offsetKey, String pageSize) {
        getAuthenticatedSession(ADMIN, WORKER);

        Account account = accountService.getAccount(AccountId.forId(appId, userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService.getParticipantData(
                account.getId(), offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    @DeleteMapping("/v1/apps/{appId}/participants/{userId}/data")
    public StatusMessage deleteAllParticipantData(@PathVariable String appId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        App app = appService.getApp(session.getAppId());

        Account account = accountService.getAccount(AccountId.forId(app.getIdentifier(), userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        participantDataService.deleteParticipantData(session.getId());

        return new StatusMessage("Participant data deleted.");
    }

    @GetMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    public ParticipantData getDataByIdentifierForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                             @PathVariable String identifier) {
        getAuthenticatedSession(ADMIN, WORKER);

        Account account = accountService.getAccount(AccountId.forId(appId, userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return participantDataService.getParticipantDataRecord(userId, identifier);
    }

    @PostMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataRecordForAdminWorker(@PathVariable String appId, @PathVariable String userId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN, WORKER); //TODO where do I use appId?

        JsonNode node = parseJson(JsonNode.class); //TODO are lines 131 through 135 necessary or only necessary in ParticipantReportController?
        if (!node.has("healthCode")) {
            throw new BadRequestException("A health code is required to save participant data");
        }
        String healthCode = node.get("healthCode").asText(); //TODO I don't need a reportDataKey, so does that mean I don't need appId and healthCode?

        ParticipantData participantData = parseJson(node, ParticipantData.class);
        participantData.setUserId(null);

        participantDataService.saveParticipantData(userId, identifier, participantData);

        return new StatusMessage("Participant data saved.");
    }

    @DeleteMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    public StatusMessage deleteDataRecordForAdmin(@PathVariable String appId, @PathVariable String userId,
                                                  @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);

        participantDataService.deleteParticipantDataRecord(userId, identifier); //TODO I also am not using appID here

        return new StatusMessage("Participant data deleted");
    }
}
