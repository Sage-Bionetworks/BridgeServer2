package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.HealthDataDocumentationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

@CrossOrigin
@RestController
public class HealthDataDocumentationController extends BaseController {

    private HealthDataDocumentationService healthDataDocumentationService;

    @Autowired
    public final void setHealthDataDocumentationService(HealthDataDocumentationService healthDataDocumentationService) {
        this.healthDataDocumentationService = healthDataDocumentationService;
    }

    /** Create or update a health data documentation. */
    @PostMapping(path="/v3/healthdatadocumentation")
    public HealthDataDocumentation createOrUpdateHealthDataDocumentation() {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        HealthDataDocumentation documentation = parseJson(HealthDataDocumentation.class);
        setDocParentIdHelper(documentation, session.getAppId());

        return healthDataDocumentationService.createOrUpdateHealthDataDocumentation(documentation);
    }

    /** Get a health data documentation with the given identifier. */
    @GetMapping(path="/v3/healthdatadocumentation/{identifier}")
    public HealthDataDocumentation getHealthDataDocumentationForId(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        // AppId is a placeholder for parentId. In the future, for study-scoped documentation, it would be “{appId}:study:{studyId}”
        // and for Assessments it would be “{appId}:assessment:{assessmentId}”.
        String parentId = session.getAppId();
        return healthDataDocumentationService.getHealthDataDocumentationForId(parentId, identifier);
    }

    /** Get all health data documentation with the given parentId. */
    @GetMapping(path="/v3/healthdatadocumentation")
    public ForwardCursorPagedResourceList<HealthDataDocumentation> getAllHealthDataDocumentationForParentId(
            @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return healthDataDocumentationService.getAllHealthDataDocumentation(session.getAppId(), pageSizeInt, offsetKey);
    }

    /** Delete a health data documentation with the given identifier. */
    @DeleteMapping(path="v3/healthdatadocumentation/{identifier}")
    public StatusMessage deleteHealthDataDocumentationForIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        String parentId = session.getAppId(); // placeholder
        healthDataDocumentationService.deleteHealthDataDocumentation(parentId, identifier);

        return new StatusMessage("Health data documentation has been deleted for the given identifier.");
    }

    /** Delete all health data documentation with the given parentId. */
    @DeleteMapping(path="/v3/healthdatadocumentation")
    public StatusMessage deleteAllHealthDataDocumentationForParentId() {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);

        healthDataDocumentationService.deleteAllHealthDataDocumentation(session.getAppId());
        return new StatusMessage("Health data documentation has been deleted.");
    }

    void setDocParentIdHelper(HealthDataDocumentation doc, String parentId) {
        doc.setParentId(parentId);
    }
}
