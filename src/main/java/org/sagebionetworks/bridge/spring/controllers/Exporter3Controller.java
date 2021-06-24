package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.io.IOException;

import org.sagebionetworks.client.exceptions.SynapseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.services.Exporter3Service;

/** Controller for Exporter 3.0. */
@CrossOrigin
@RestController
public class Exporter3Controller extends BaseController {
    private Exporter3Service exporter3Service;

    @Autowired
    public final void setExporter3Service(Exporter3Service exporter3Service) {
        this.exporter3Service = exporter3Service;
    }

    /** Initializes configs and Synapse resources for Exporter 3.0. */
    @PostMapping(path = "/v1/apps/self/exporter3")
    @ResponseStatus(HttpStatus.CREATED)
    public Exporter3Configuration initExporter3() throws IOException, SynapseException {
        UserSession session = getAuthenticatedSession(ADMIN, DEVELOPER);
        return exporter3Service.initExporter3(session.getAppId());
    }
}
