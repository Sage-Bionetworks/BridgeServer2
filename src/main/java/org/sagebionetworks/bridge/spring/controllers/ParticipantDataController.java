package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public ForwardCursorPagedResourceList<String> getAllDataForSelf(@RequestParam(required = false) String offsetKey,
                                                                    @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedAndConsentedSession();

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService
                .getAllParticipantData(session.getId(), offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    @GetMapping("/v3/users/self/data/{identifier}")
    public ParticipantData getDataByIdentifierForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return participantDataService.getParticipantData(session.getId(), identifier);
    }

    @PostMapping("/v3/users/self/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setUserId(null); // set in service, but just so non future use depends on it

        participantDataService.saveParticipantData(session.getId(), identifier, participantData);
        return new StatusMessage("Participant data saved.");
    }

    @DeleteMapping("/v3/users/self/data/{identifier}")
    public StatusMessage deleteDataByIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        participantDataService.deleteParticipantData(session.getId(), identifier);

        return new StatusMessage("Participant data record deleted.");
    }

    @GetMapping("/v1/apps/{appId}/participants/{userId}/data")
    public ForwardCursorPagedResourceList<String> getAllDataForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                                           String offsetKey, String pageSize) {
        UserSession session = getAuthenticatedSession(ADMIN, WORKER);

        Account account = accountService.getAccount(AccountId.forId(appId, userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }

        checkAdminSessionAppId(session, appId);

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService.getAllParticipantData(
                account.getId(), offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    @DeleteMapping("/v1/apps/{appId}/participants/{userId}/data")
    public StatusMessage deleteAllParticipantDataForAdmin(@PathVariable String appId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        participantDataService.deleteAllParticipantData(session.getId());

        return new StatusMessage("Participant data deleted.");
    }

    @GetMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    public ParticipantData getDataByIdentifierForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                             @PathVariable String identifier) {
        getAuthenticatedSession(ADMIN, WORKER);

        checkAccountExists(appId, userId);
        checkAccountExists(appId, userId);

        return participantDataService.getParticipantData(userId, identifier);
    }

    @PostMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataForAdminWorker(@PathVariable String appId, @PathVariable String userId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN, WORKER);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setUserId(null);

        participantDataService.saveParticipantData(userId, identifier, participantData);

        return new StatusMessage("Participant data saved.");
    }

    @DeleteMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    public StatusMessage deleteDataForAdmin(@PathVariable String appId, @PathVariable String userId,
                                                  @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        participantDataService.deleteParticipantData(userId, identifier);

        return new StatusMessage("Participant data deleted.");
    }

    private void checkAdminSessionAppId(UserSession session, String appId) {
        if (session.isInRole(ADMIN) && (!appId.equals(session.getAppId()))) { //TODO: waiting to hear from dwayne if this is the correct exception
            throw new UnauthorizedException("Caller does not have permission to access participant data.");
        }
    }

    private void checkAccountExists(String appId, String userId) {
        Account account = accountService.getAccount(AccountId.forId(appId, userId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
    }
}
