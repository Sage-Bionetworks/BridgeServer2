package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.HealthDataDocumentationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class HealthDataDocumentationController extends BaseController {
    private HealthDataDocumentationService healthDataDocumentationService;

    @Autowired
    public final void setHealthDataDocumentationService(HealthDataDocumentationService healthDataDocumentationService) {
        this.healthDataDocumentationService = healthDataDocumentationService;
    }

    /** Get a health data documentation with the given identifier. */
    @GetMapping(path="/v3/healthdataDocumentation/{identifier}")
    public HealthDataDocumentation getHealthDataDocumentation(@PathVariable String identifer) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        return healthDataDocumentationService.getHealthDataDocumentationForId(identifer, session.getAppId());
    }

    /** Get all health data documentation with the given parentId. */

    /** Create or update a health data documentation. */

    /** Delete a health data documentation with the given identifier. */

    /** Delete all health data documentation with the given parentId. */
}
