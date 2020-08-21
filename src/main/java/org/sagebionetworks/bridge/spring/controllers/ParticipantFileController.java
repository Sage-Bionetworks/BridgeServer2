package org.sagebionetworks.bridge.spring.controllers;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFile;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.sagebionetworks.bridge.services.ParticipantFileService;
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

import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

@CrossOrigin
@RestController
public class ParticipantFileController extends BaseController {

    static final StatusMessage DELETE_MSG = new StatusMessage("Participant file deleted.");
    static final StatusMessage DELETE_FAIL_MSG = new StatusMessage("File does not exist.");

    private ParticipantFileService fileService;

    @Autowired
    final void setParticipantFileService(ParticipantFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("v3/participants/self/files")
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String userId = session.getParticipant().getId();
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        return fileService.getParticipantFiles(userId, offsetKey, pageSizeInt);
    }

    @GetMapping("v3/participants/self/files/{fileId}")
    @ResponseStatus(HttpStatus.FOUND)
    public ParticipantFile getParticipantFile(@PathVariable String fileId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String userId = session.getParticipant().getId();

        return fileService.getParticipantFile(userId, fileId);
    }

    @PostMapping("v3/participants/self/files/{fileId}")
    public ParticipantFile createParticipantFile(@PathVariable String fileId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String userId = session.getParticipant().getId();

        ParticipantFile file = parseJson(DynamoParticipantFile.class);
        file.setUserId(userId);
        file.setCreatedOn(new DateTime().withZone(UTC));
        file.setFileId(fileId);

        return fileService.createParticipantFile(file);
    }

    @DeleteMapping("/v3/participants/self/files/{fileId}")
    public StatusMessage deleteParticipantFile(@PathVariable String fileId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        String userId = session.getParticipant().getId();

        try {
            fileService.deleteParticipantFile(userId, fileId);
        } catch (EntityNotFoundException e) {
            return DELETE_FAIL_MSG;
        }
        return DELETE_MSG;
    }
}
