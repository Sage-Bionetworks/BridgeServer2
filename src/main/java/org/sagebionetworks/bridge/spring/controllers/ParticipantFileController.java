package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.sagebionetworks.bridge.services.ParticipantFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class ParticipantFileController extends BaseController {

    private ParticipantFileService fileService;

    @Autowired
    final void setParticipantFileService(ParticipantFileService fileService) {
        this.fileService = fileService;
    }

    // TODO: REST
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize) {
        return null;
    }

    // TODO: Read more multipart upload

}
