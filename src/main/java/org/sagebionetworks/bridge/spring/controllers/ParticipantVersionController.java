package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ParticipantVersionService;

/** Controller for Participant. */
@CrossOrigin
@RestController
public class ParticipantVersionController extends BaseController {
    private ParticipantVersionService participantVersionService;

    @Autowired
    public final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    /** Delete all participant versions for the given user. This is called by integration tests. */
    @DeleteMapping(path="/v3/participants/{userIdToken}/versions")
    public StatusMessage deleteParticipantVersionsForUser(@PathVariable String userIdToken) {
        UserSession session = getAuthenticatedSession(ADMIN);
        String appId = session.getAppId();

        Account account = accountService.getAccount(BridgeUtils.parseAccountId(appId, userIdToken)).orElseThrow(
                () -> new EntityNotFoundException(StudyParticipant.class));
        String healthCode = account.getHealthCode();
        if (!account.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP)) {
            throw new UnauthorizedException("Cannot delete participant version if account is not a test user");
        }

        participantVersionService.deleteParticipantVersionsForHealthCode(appId, healthCode);

        return new StatusMessage("Participant versions have been deleted for participant");
    }

    /** Get all participant versions for health code. Returns an empty list if none exist. */
    @GetMapping(path="/v1/apps/{appId}/participants/{userIdToken}/versions")
    public ResourceList<ParticipantVersion> getAllParticipantVersionsForUser(@PathVariable String appId,
            @PathVariable String userIdToken) {
        getAuthenticatedSession(WORKER);

        String healthCode = accountService.getAccountHealthCode(appId, userIdToken).orElseThrow(
                () -> new EntityNotFoundException(StudyParticipant.class));
        List<ParticipantVersion> participantVersionList = participantVersionService
                .getAllParticipantVersionsForHealthCode(appId, healthCode);
        return new ResourceList<>(participantVersionList);
    }

    /** Retrieves the latest participant version. */
    @GetMapping(path="/v1/apps/{appId}/participants/{userIdToken}/versions/latest")
    public ParticipantVersion getLatestParticipantVersion(@PathVariable String appId,
            @PathVariable String userIdToken) {
        getAuthenticatedSession(WORKER);

        String healthCode = accountService.getAccountHealthCode(appId, userIdToken).orElseThrow(
                () -> new EntityNotFoundException(StudyParticipant.class));
        return participantVersionService.getLatestParticipantVersionForHealthCode(appId, healthCode).orElseThrow(
                () -> new EntityNotFoundException(ParticipantVersion.class));
    }

    /** Retrieves the specified participant version. */
    @GetMapping(path="/v1/apps/{appId}/participants/{userIdToken}/versions/{version}")
    public ParticipantVersion getParticipantVersion(@PathVariable String appId, @PathVariable String userIdToken,
            @PathVariable String version) {
        getAuthenticatedSession(WORKER);

        String healthCode = accountService.getAccountHealthCode(appId, userIdToken).orElseThrow(
                () -> new EntityNotFoundException(StudyParticipant.class));

        int versionInt;
        try {
            versionInt = Integer.parseInt(version);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid version " + version);
        }
        return participantVersionService.getParticipantVersion(appId, healthCode, versionInt);
    }
}
