package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ExportService;

@CrossOrigin
@RestController
public class ExportController extends BaseController {
    private ExportService exportService;

    /** Service for exports. */
    @Autowired
    public final void setExportService(ExportService exportService) {
        this.exportService = exportService;
    }

    /** Kicks off an on-demand export for the given study. */
    @PostMapping("/v3/export/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage startOnDemandExport() throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        exportService.startOnDemandExport(session.getAppId());
        return new StatusMessage("Request submitted.");
    }
}
