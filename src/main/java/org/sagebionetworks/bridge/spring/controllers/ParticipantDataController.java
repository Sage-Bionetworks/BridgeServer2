package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantDataService;
import org.sagebionetworks.bridge.spring.util.EtagCacheKey;
import org.sagebionetworks.bridge.spring.util.EtagSupport;
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

    /**
     * User API to get a list identifiers for all of the user's participant data.
     */
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

    /**
     * User API to get a single participant data with the given identifier.
     */
    @EtagSupport({
        @EtagCacheKey(model=ParticipantData.class, keys={"userId", "identifier"})
    })
    @GetMapping("/v3/users/self/data/{identifier}")
    public ParticipantData getDataByIdentifierForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return participantDataService.getParticipantData(session.getId(), identifier);
    }

    /**
     * User API to save a single participant data with the given identifier.
     */
    @PostMapping("/v3/users/self/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataForSelf(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setUserId(null); // set in service, but just so non future use depends on it

        participantDataService.saveParticipantData(session.getId(), identifier, participantData);
        return new StatusMessage("Participant data saved.");
    }

    /**
     * User API to delete a participant data with the given identifier.
     */
    @DeleteMapping("/v3/users/self/data/{identifier}")
    public StatusMessage deleteDataByIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedAndConsentedSession();

        participantDataService.deleteParticipantData(session.getId(), identifier);

        return new StatusMessage("Participant data record deleted.");
    }

    /**
     * Admin or Worker API to get a list of identifiers of all the participant data associated with the given userId.
     */
    @GetMapping("/v1/apps/{appId}/participants/{userId}/data")
    public ForwardCursorPagedResourceList<String> getAllDataForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                                           String offsetKey, String pageSize) {
        UserSession session = getAuthenticatedSession(WORKER);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> participantData = participantDataService.getAllParticipantData(
                userId, offsetKey, pageSizeInt);
        List<String> identifiers = participantData.getItems().stream().map(ParticipantData::getIdentifier).collect(Collectors.toList());

        return new ForwardCursorPagedResourceList<String>(identifiers, participantData.getNextPageOffsetKey());
    }

    /**
     * Admin API to delete all participant data associated with the given userId.
     */
    @DeleteMapping("/v1/apps/{appId}/participants/{userId}/data")
    public StatusMessage deleteAllParticipantDataForAdmin(@PathVariable String appId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        participantDataService.deleteAllParticipantData(userId);

        return new StatusMessage("Participant data deleted.");
    }

    /**
     * Admin or Worker API to get a participant data associated with the given identifier.
     */
    @GetMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    public ParticipantData getDataByIdentifierForAdminWorker(@PathVariable String appId, @PathVariable String userId,
                                                             @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(WORKER);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, userId);

        return participantDataService.getParticipantData(userId, identifier);
    }

    /**
     * Admin or Worker API to save a participant data with the given userId and identifier.
     */
    @PostMapping("/v1/apps/{appId}/participants/{userId}/data/{identifier}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage saveDataForAdminWorker(@PathVariable String appId, @PathVariable String userId, @PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(WORKER);

        checkAccountExists(appId, userId);
        checkAdminSessionAppId(session, appId);

        ParticipantData participantData = parseJson(ParticipantData.class);
        participantData.setUserId(null);

        participantDataService.saveParticipantData(userId, identifier, participantData);

        return new StatusMessage("Participant data saved.");
    }

    /**
     * Admin API to delete a participant data for the given userId and identifier.
     */
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
        if (session.isInRole(ADMIN) && (!appId.equals(session.getAppId()))) {
            throw new EntityNotFoundException(Account.class);
        }
    }

    private void checkAccountExists(String appId, String userId) {
        accountService.getAccount(AccountId.forId(appId, userId))
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
    }
}
