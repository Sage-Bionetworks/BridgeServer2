package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_UPDATE_STUDIES;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;

import java.io.IOException;

import org.sagebionetworks.client.exceptions.SynapseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
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
    public Exporter3Configuration initExporter3() throws BridgeSynapseException, IOException, SynapseException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return exporter3Service.initExporter3(session.getAppId());
    }

    @PostMapping("/v5/studies/{studyId}/exporter3")
    @ResponseStatus(HttpStatus.CREATED)
    public Exporter3Configuration initExporter3ForStudy(@PathVariable String studyId) throws BridgeSynapseException, IOException,
            SynapseException {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, studyId);
        return exporter3Service.initExporter3ForStudy(session.getAppId(), studyId);
    }
}
